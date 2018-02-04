package paintchat

import akka.actor.{ActorSystem, PoisonPill, Props}
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings}
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.Directives._
import akka.management.AkkaManagement
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.stream.scaladsl._
import com.typesafe.config.ConfigFactory
import scala.concurrent.Await
import scala.concurrent.duration._

object PaintChat extends App {

  val config = ConfigFactory.load()

  // join cluster
  implicit val system = ActorSystem("ClusterSystem")
  implicit val materializer = ActorMaterializer()
  AkkaManagement(system).start()
  ClusterBootstrap(system).start()
  system.actorOf(Props(classOf[ClusterListener]), "paintchat-cluster")
  system.actorOf(ClusterSingletonManager.props(
    singletonProps = Props(classOf[BufferManager]),
    terminationMessage = PoisonPill,
    settings = ClusterSingletonManagerSettings(system)),
    name = "buffer-manager")
  val serverWorker = system.actorOf(Props[ServerWorker], "paintchat-server")

  def newUser(): Flow[Message, Message, _] = {
    val clientWorker = system.actorOf(ClientWorker.props(serverWorker))

    val incomingMessages: Sink[Message, _] =
      Flow[Message].map {
        case TextMessage.Strict(text) => ClientWorker.IncomingMessage(text)
      }.to(Sink.actorRef[ClientWorker.IncomingMessage](clientWorker, PoisonPill))

    val outgoingMessages: Source[Message, _] =
      Source.actorRef[ClientWorker.OutgoingMessage](100, OverflowStrategy.dropHead)
        .mapMaterializedValue { outActor =>
          clientWorker ! ClientWorker.Connected(outActor)
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

  // bind to http port
  Await.result(Http().bindAndHandle(route, "0.0.0.0", 8080), 3.seconds) match {
    case ServerBinding(address) => println(Console.GREEN+s"server listening on http:/$address"+Console.RESET)
    case x => println(Console.RED+s"Error: http bind failed - recieved response $x"+Console.RESET)
  }

  Await.result(system.whenTerminated, Duration.Inf)
}
