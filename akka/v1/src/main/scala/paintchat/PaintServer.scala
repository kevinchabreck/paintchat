package paintchat

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.stream.scaladsl.{Flow, Source, Sink, Merge}
import akka.stream.scaladsl.FlowGraph.Implicits._
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import play.api.libs.json.Json

import scala.language.implicitConversions
import scala.io.StdIn

case class PaintchatMessage(sender: String, text: String)

sealed trait PaintchatEvent
case class UserJoined(name: String, userActor: ActorRef) extends PaintchatEvent
case class UserLeft(name: String) extends PaintchatEvent
case class IncomingMessage(sender: String, message: String) extends PaintchatEvent

object Server extends App {

  var count : Int = 0

  import Directives._

  implicit val actorSystem = ActorSystem("server-system")
  implicit val flowMaterializer = ActorMaterializer()

  val paintchatFlowControl = new PaintchatFlowControl(actorSystem)

  val route = {
    pathEndOrSingleSlash {
      count += 1
      handleWebsocketMessages(paintchatFlowControl.websocketFlow(count.toString)) ~
      getFromResource("www/index.html")
    } ~
    getFromResourceDirectory("www")
  }

  val config = actorSystem.settings.config
  val interface = config.getString("app.interface")
  val port = config.getInt("app.port")

  val binding = Http().bindAndHandle(route, interface, port)
  println(s"server listening on http://$interface:$port (press ENTER to exit)")

  StdIn.readLine()

  import actorSystem.dispatcher

  binding.flatMap(_.unbind()).onComplete(_ => actorSystem.shutdown())
  println("Server is down...")

}

object PaintchatFlowControl {
  def apply(implicit actorSystem: ActorSystem) = new PaintchatFlowControl(actorSystem)
}
class PaintchatFlowControl(actorSystem: ActorSystem) {

  private[this] val paintchatActor = actorSystem.actorOf(Props(classOf[PaintchatActor]))

  def websocketFlow(user : String): Flow[Message, Message, _] =
    Flow(Source.actorRef[PaintchatMessage](bufferSize = 5, OverflowStrategy.fail)) {
      implicit builder =>
        chatSource => //source provideed as argument

          //flow used as input it takes Message's
          val fromWebsocket = builder.add(
            Flow[Message].collect {
              case TextMessage.Strict(txt) =>
                //println(s"[$user] -> $txt")
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
      participants += name -> (actorRef, "")

    case UserLeft(name) =>
      participants -= name

    //case ServerStatus => sender ! ServerInfo(clients.size)

    case IncomingMessage(user, message) =>
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
