package paintchat

import akka.actor.{ActorSystem, PoisonPill, Props}
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings, ClusterSingletonProxy, ClusterSingletonProxySettings}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.Directives._
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.stream.scaladsl._
import com.typesafe.config.ConfigFactory

import scala.concurrent.Await
import scala.concurrent.duration._

object PaintChat extends App {

  val config = ConfigFactory.load()

  // join cluster
  implicit val system = ActorSystem("ClusterSystem")
  system.actorOf(Props(classOf[ClusterListener]), "paintchat-cluster")
  system.actorOf(ClusterSingletonManager.props(
    singletonProps = Props(classOf[BufferManager]),
    terminationMessage = PoisonPill,
    settings = ClusterSingletonManagerSettings(system)),
    name = "buffer-manager")
  implicit val materializer = ActorMaterializer()
  var bufferproxy = system.actorOf(ClusterSingletonProxy.props(
    singletonManagerPath = "/user/buffer-manager",
    settings = ClusterSingletonProxySettings(system)),
    name = "buffer-proxy")
  val mediator = DistributedPubSub(system).mediator
  val serverWorker = system.actorOf(ServerWorker.props(bufferproxy, mediator), "paintchat-server")

  def newUser(): Flow[Message, Message, _] = {
    val userActor = system.actorOf(Props(new ClientWorker(serverWorker, bufferproxy, mediator)))

    val incomingMessages: Sink[Message, _] =
      Flow[Message].map {
        case TextMessage.Strict(text) => ClientWorker.IncomingMessage(text)
      }.to(Sink.actorRef[ClientWorker.IncomingMessage](userActor, PoisonPill))

    val outgoingMessages: Source[Message, _] =
      Source.actorRef[ClientWorker.OutgoingMessage](100, OverflowStrategy.dropHead)
        .mapMaterializedValue { outActor =>
          userActor ! ClientWorker.Connected(outActor)
        }.map((outMsg: ClientWorker.OutgoingMessage) => TextMessage(outMsg.text))

    Flow.fromSinkAndSource(incomingMessages, outgoingMessages)
  }

  val route =
    pathEndOrSingleSlash {
      getFromResource("www/index.html")
    } ~ path("ws") {
      handleWebSocketMessages(newUser())
    } ~ path("health") {
      complete("{status: up}")
    } ~ getFromResourceDirectory("www")

  val binding = Await.result(Http().bindAndHandle(route, "0.0.0.0", 8080), 3.seconds)

  Await.result(system.whenTerminated, Duration.Inf)
}
