package paintchat

import akka.actor.{Actor, ActorRef, ActorSystem, Address, Props}
import akka.cluster.{Cluster, Member}
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.stream.scaladsl.{Flow, Source, Sink, Merge}
import akka.stream.scaladsl.FlowGraph.Implicits._
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.util.Timeout
import akka.pattern.ask
import play.api.libs.json.{Json, Writes, JsValue, JsString}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.implicitConversions
import scala.io.StdIn

case class PaintchatMessage(sender: String, text: String)

sealed trait PaintchatEvent
case class UserJoined(name: String, userActor: ActorRef) extends PaintchatEvent
case class UserLeft(name: String) extends PaintchatEvent
case class IncomingMessage(sender: String, message: String) extends PaintchatEvent

sealed trait Status
case object ServerStatus extends Status
case class ServerInfo(connections: Int) extends Status

object Server extends App {
  implicit val system = ActorSystem("server-system")
  implicit val flowMaterializer = ActorMaterializer()
  val paintchatActor = system.actorOf(Props(classOf[PaintchatActor]))
  val paintchatFlowControl = new PaintchatFlowControl(paintchatActor)
  var count:Int = 0

  implicit val addressWrites = new Writes[Address] {
    def writes(address: Address) = JsString(address.host.get+":"+address.port.get)
  }

  implicit val memberWrites = new Writes[Member] {
    def writes(member: Member) = Json.obj(
      member.address.host.get+":"+member.address.port.get -> member.status.toString
    )
  }

  def statusJSON: JsValue = {
    implicit val timeout = Timeout(1 seconds)
    val fs = ask(paintchatActor, ServerStatus).mapTo[ServerInfo]
    val ServerInfo(connections) = Await.result(fs, timeout.duration)
    // val clusterstatus = Cluster(system).state
    return Json.obj(
      "status" -> "Up",
      "uptime" -> system.uptime,
      "client_connections" -> connections
      // "cluster" -> Json.obj(
      //   "leader" -> clusterstatus.leader,
      //   "members" -> clusterstatus.members
      // )
    )
  }

  val route = {
    pathEndOrSingleSlash {
      count += 1
      handleWebsocketMessages(paintchatFlowControl.websocketFlow(count.toString)) ~
      getFromResource("www/index.html")
    } ~
    path("status") {
      complete(statusJSON.toString)
    } ~
    getFromResourceDirectory("www")
  }

  val config = system.settings.config
  val interface = config.getString("app.interface")
  val port = config.getInt("app.port")

  val binding = Http().bindAndHandle(route, interface, port)

  StdIn.readLine(s"server listening on http://$interface:$port (press ENTER to exit)\n")
  println("shutting down server")
  binding.flatMap(_.unbind()).onComplete(_ => system.terminate())
  Await.result(system.whenTerminated, Duration.Inf)
}

class PaintchatFlowControl(paintchatActor: ActorRef) {

  def websocketFlow(user: String): Flow[Message, Message, _] =
    Flow(Source.actorRef[PaintchatMessage](bufferSize = 5, OverflowStrategy.fail)) {
      implicit builder =>
        chatSource => //source provideed as argument

          var socket : ActorRef = null
          // println(s"[flow $user] builder.materializedValue: ${builder.materializedValue}")
          //flow used as input it takes Message's
          val fromWebsocket = builder.add(
            Flow[Message].collect {
              case TextMessage.Strict(txt) =>
                //println(s"[$chatSource] -> $txt")
                IncomingMessage(user, txt)
            }
          )

          //flow used as output, it returns Message's
          val backToWebsocket = builder.add(
            Flow[PaintchatMessage].map {
              case PaintchatMessage(author, text) =>
                //println(s"[$user] <- [$author]: $text")
                TextMessage(s"$text")
            }
          )

          //send messages to the actor, if send also UserLeft(user) before stream completes.
          val paintchatActorSink = Sink.actorRef[PaintchatEvent](paintchatActor, UserLeft(user))

          //merges both pipes
          val merge = builder.add(Merge[PaintchatEvent](2))

          //Materialized value of Actor who sits in flow control
          val actorAsSource = builder.materializedValue.map(actor => UserJoined(user, actor))

          //Message from websocket is converted into IncommingMessage and should be send to each in room
          fromWebsocket ~> merge.in(0)

          //If Source actor is just created should be send as UserJoined and registered as particiant in room
          actorAsSource ~> merge.in(1)

          //Merges both pipes above and forward messages to PaintchatActor
          merge ~> paintchatActorSink

          //Actor already sit in flow control so each message from room is used as source and pushed back into websocket
          chatSource ~> backToWebsocket

          // expose ports
          (fromWebsocket.inlet, backToWebsocket.outlet)
    }

  def sendMessage(message: PaintchatMessage): Unit = paintchatActor ! message
}

class PaintchatActor() extends Actor {
  var participants: Map[String, (ActorRef, String)] = Map.empty[String, (ActorRef, String)] // map of numeric identifier to actor reference and username
  val paintbuffer = new collection.mutable.ListBuffer[String]

  override def receive: Receive = {
    case UserJoined(name, actorRef) =>
      // println(s"got userjoin($name, $actorRef)")
      participants += name -> (actorRef, "")

    case UserLeft(name) =>
      participants -= name

    case ServerStatus => sender ! ServerInfo(participants.size)

    case IncomingMessage(user, message) =>
      //println(s"new IncomingMessage [client = ${participants(user)._1}, sender = $sender]")
      val _ = message.split(":",2).toList match {

        case "PAINT"::data::_ =>
          paintbuffer += data
          broadcast(IncomingMessage(user,message))

        case "GETBUFFER"::_ =>
          participants(user)._1 ! PaintchatMessage(user, "PAINTBUFFER:"+Json.toJson(paintbuffer))

        case "USERNAME"::username::_ =>
          val tempRef : ActorRef = participants(user)._1
          participants += user -> (tempRef, username)
          participants(user)._1 ! PaintchatMessage(user, "ACCEPTED:"+username)
          broadcastNotSender(PaintchatMessage(user, "INFO:"+username+" has joined"))

        case "RESET"::_ =>
          participants(user)._1 ! PaintchatMessage(user, "SRESET:")
          broadcastNotSender(PaintchatMessage(user, "RESET:"+participants(user)._2))
          paintbuffer.clear()

        case "CHAT"::message::_ =>
          val m = "CHAT:"+participants(user)._2+":"+message
          broadcastNotSender(PaintchatMessage(user, m))

        case x =>
          println(s"[SERVER] recieved unrecognized update: $x")
      }

    case x =>
      println("[SERVER] recieved unknown message: "+x)
  }


  implicit def chatEventToChatMessage(event: IncomingMessage): PaintchatMessage = PaintchatMessage(event.sender, event.message)

  def broadcast(message: PaintchatMessage): Unit = participants.values.foreach(_._1 ! message)

  def broadcastNotSender(m : PaintchatMessage): Unit = {
    val PaintchatMessage(user,message) = m
    participants.filter({case (u,_)=> user != u }).foreach({case(_,(ref,_)) => ref ! PaintchatMessage(user,message)})
  }
}
