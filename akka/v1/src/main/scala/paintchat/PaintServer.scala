package paintchat

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.stream.scaladsl.{Flow, Source, Sink, Merge}
// import akka.stream.scaladsl._
import akka.stream.scaladsl.FlowGraph.Implicits._
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import play.api.libs.json.Json

import scala.language.implicitConversions
import scala.io.StdIn

case class ChatMessage(sender: String, text: String)
object SystemMessage {
  def apply(text: String) = ChatMessage("System", "INFO:"+text)
}
sealed trait ChatEvent
case class UserJoined(name: String, userActor: ActorRef) extends ChatEvent
case class UserLeft(name: String) extends ChatEvent
case class IncomingMessage(sender: String, message: String) extends ChatEvent
// case class IncomingMessage(sender: ActorRef, message: String) extends ChatEvent

object Server extends App {

  var count : Int = 0

  import Directives._

  implicit val actorSystem = ActorSystem("server-system")
  implicit val flowMaterializer = ActorMaterializer()

  val route = {
    pathEndOrSingleSlash {
      count += 1
      handleWebsocketMessages(ChatRooms.findOrCreate(0).websocketFlow(count.toString)) ~
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

object ChatRooms {
  var chatRooms: Map[Int, ChatRoom] = Map.empty[Int, ChatRoom]

  def findOrCreate(number: Int)(implicit actorSystem: ActorSystem): ChatRoom = chatRooms.getOrElse(number, createNewChatRoom(number))

  private def createNewChatRoom(number: Int)(implicit actorSystem: ActorSystem): ChatRoom = {
    val chatroom = ChatRoom(number)
    chatRooms += number -> chatroom
    chatroom
  }
}

object ChatRoom {
  def apply(roomId: Int)(implicit actorSystem: ActorSystem) = new ChatRoom(roomId, actorSystem)
}
class ChatRoom(roomId: Int, actorSystem: ActorSystem) {

  private[this] val chatRoomActor = actorSystem.actorOf(Props(classOf[ChatRoomActor], roomId))

  def websocketFlow(user : String): Flow[Message, Message, _] =
    Flow(Source.actorRef[ChatMessage](bufferSize = 5, OverflowStrategy.fail)) {
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
            Flow[ChatMessage].map {
              case ChatMessage(author, text) =>
                //println(s"[$user] <- [$author]: $text")
                TextMessage(s"$text")
            }
          )

          //send messages to the actor, if send also UserLeft(user) before stream completes.
          val chatActorSink = Sink.actorRef[ChatEvent](chatRoomActor, UserLeft(user))

          //merges both pipes
          val merge = builder.add(Merge[ChatEvent](2))

          //Materialized value of Actor who sit in chatroom
          val actorAsSource = builder.materializedValue.map(actor => UserJoined(user, actor))

          //Message from websocket is converted into IncommingMessage and should be send to each in room
          fromWebsocket ~> merge.in(0)

          //If Source actor is just created should be send as UserJoined and registered as particiant in room
          actorAsSource ~> merge.in(1)

          //Merges both pipes above and forward messages to chatroom Represented by ChatRoomActor
          merge ~> chatActorSink

          //Actor already sit in chatRoom so each message from room is used as source and pushed back into websocket
          chatSource ~> backToWebsocket

          // expose ports
          (fromWebsocket.inlet, backToWebsocket.outlet)
    }

  def sendMessage(message: ChatMessage): Unit = chatRoomActor ! message
}

class ChatRoomActor(roomId: Int) extends Actor {
  var participants: Map[String, (ActorRef, String)] = Map.empty[String, (ActorRef, String)] // map of numeric identifier to actor reference and username
  val paintbuffer = new collection.mutable.ListBuffer[String]

  override def receive: Receive = {
    case UserJoined(name, actorRef) =>
      participants += name -> (actorRef, "")
      //broadcast(SystemMessage(s"User $name joined channel..."))

    case UserLeft(name) =>
      //broadcast(SystemMessage(s"User $name left channel[$roomId]"))
      participants -= name

    case IncomingMessage(user, message) =>

 /*def receive = {
    case Http.Connected(remoteAddress, localAddress) =>
      val conn = context.actorOf(WebSocketWorker.props(sender, self))
      context.watch(conn)
      sender ! Http.Register(conn)

    case UpgradedToWebSocket => clients(sender) = ""

    case x: ConnectionClosed => clients -= sender

    case x: Terminated => clients -= sender

    case ServerStatus => sender ! ServerInfo(clients.size)*/

    //case msg: TextFrame =>
      // val _ = message./*payload.utf8String.*/split(":",2).toList match {
      val _ = message.split(":",2).toList match {

        case "PAINT"::data::_ =>
          paintbuffer += data
          broadcast(IncomingMessage(user,message))
   
        case "GETBUFFER"::_ =>
          participants(user)._1 ! ChatMessage(user, "PAINTBUFFER:"+Json.toJson(paintbuffer))

        case "USERNAME"::username::_ =>
          val tempRef : ActorRef = participants(user)._1
          participants += user -> (tempRef, username)
          participants(user)._1 ! ChatMessage(user, "ACCEPTED:"+username)
          broadcast(ChatMessage(user, "INFO:"+username+" has joined"))
         
        case "RESET"::_ =>
          participants(user)._1 ! ChatMessage(user, "SRESET:")
          broadcast(ChatMessage(user, "RESET:"+participants(user)._2)) 
          paintbuffer.clear()

        case "CHAT"::message::_ =>
          val m = "CHAT:"+participants(user)._2+":"+message
          broadcast(ChatMessage(user, m))

        case x =>
          println(s"[SERVER] recieved unrecognized update: $x")
      }

    case x =>
      println("[SERVER] recieved unknown message: "+x)
  }


  implicit def chatEventToChatMessage(event: IncomingMessage): ChatMessage = ChatMessage(event.sender, event.message)

  def broadcast(message: ChatMessage): Unit = participants.values.foreach(_._1 ! message)

}
