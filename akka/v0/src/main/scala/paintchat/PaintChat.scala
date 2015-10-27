package paintchat

import akka.actor.{Actor, ActorRef, ActorSystem, Props, ActorLogging, Terminated}
import akka.io.IO
import akka.io.Tcp.{ConnectionClosed, ConfirmedClosed}
import akka.util.Timeout
import akka.pattern.ask

import scala.concurrent.Await
import scala.concurrent.duration._

import spray.can.server.UHttp
import spray.can.{Http, websocket}
import spray.can.websocket.frame.{Frame, BinaryFrame, TextFrame}
import spray.can.websocket.{UpgradedToWebSocket, FrameCommandFailed}
import spray.http.HttpRequest
import spray.routing.HttpServiceActor
import spray.json._
import DefaultJsonProtocol._

final case class Push(msg: String)
case class ForwardFrame(frame: Frame)

object Server extends App with MySslConfiguration {
  implicit val system = ActorSystem("paintchat-system")
  val server = system.actorOf(Props(classOf[Server]), "paintchat-server")
  implicit val timeout = Timeout(1 seconds)
  val bind_future = ask(IO(UHttp), Http.Bind(server, "0.0.0.0", 8080))
  val bind_result = Await.result(bind_future, timeout.duration)
  bind_result match {
    case Http.Bound(x) =>
      scala.io.StdIn.readLine(s"server listening on $x (press ENTER to exit)\n")
    case x: Http.CommandFailed =>
      println("CommandFailed! (probably couldn't initialize server): $x")
  }
  println("shutting down server")
  system.shutdown()
  system.awaitTermination()
}
class Server extends Actor with ActorLogging {

  val clients = collection.mutable.Map[ActorRef, String]()
  val paintbuffer = new collection.mutable.ListBuffer[String]

  def receive = {
    // when a new connection comes in we register a WebSocketConnection actor as the per connection handler
    case Http.Connected(remoteAddress, localAddress) =>
      // println("new connection "+remoteAddress+" - creating new actor")
      val conn = context.actorOf(WebSocketWorker.props(sender, self))
      context.watch(conn)
      sender ! Http.Register(conn)

    case UpgradedToWebSocket =>
      clients(sender) = ""
      print("connected clients: "+clients.size+"                    \r")

    case msg: TextFrame =>
      val _ = msg.payload.utf8String.split(":",2).toList match {

        case "PAINT"::data::_ =>
          paintbuffer += data
          clients.keys.foreach(_.forward(ForwardFrame(msg)))

        case "GETBUFFER"::_ =>
          sender ! Push("PAINTBUFFER:"+paintbuffer.toList.toJson)

        case "USERNAME"::username::_ =>
          // println("[SERVER] new user: $username")
          clients(sender) = username
          sender ! Push("ACCEPTED:"+username)
          clients.keys.filter(_ != sender).foreach(_ ! Push("INFO:"+username+" has joined"))

        case "RESET"::_ =>
          sender ! Push("SRESET:")
          clients.keys.filter(_ != sender).foreach(_ ! Push("RESET:"+clients(sender)))
          paintbuffer.clear()

        case "CHAT"::message::_ =>
          val m = "CHAT:"+clients(sender)+":"+message
          // println("broadcasting chat: $m")
          clients.keys.filter(_ != sender).foreach(_ ! Push(m))

        case _ =>
          println("[SERVER] recieved unrecognized textframe: "+msg.payload.utf8String)
      }

    case x: ConnectionClosed =>
      clients -= sender
      print("connected clients: "+clients.size+"                    \r")

    case x: Terminated =>
      clients -=(sender)
      print("connected clients: "+clients.size+"                    \r")

    case x =>
      println("[SERVER] recieved unknown message: "+x)
  }

}

// these are actors - one for each connection
object WebSocketWorker {
  def props(serverConnection: ActorRef, parent: ActorRef) = Props(classOf[WebSocketWorker], serverConnection, parent)
}
class WebSocketWorker(val serverConnection: ActorRef, val parent: ActorRef) extends HttpServiceActor with websocket.WebSocketServerWorker {

  override def receive = handshaking orElse businessLogicNoUpgrade orElse closeLogic

  def businessLogic: Receive = {

    // uncomment if running echo benchmarks
    // case x @ (_: BinaryFrame | _: TextFrame) =>
    //   sender ! x

    case msg: TextFrame =>
      parent ! msg

    case ForwardFrame(f) =>
      // println("[WORKER] recieved a ForwardFrame: "+f.payload.utf8String)
      send(f)

    case Push(msg) =>
      // println("[WORKER] recieved a Push: "+msg)
      send(TextFrame(msg))

    case x: FrameCommandFailed =>
      // log.error("frame command failed", x)

    // should never happen... right?
    case x: HttpRequest =>
      // println("[WORKER] got an http request: "+x)

    // onClose
    case x: ConnectionClosed =>
      // println("[WORKER] connection closing...")
      parent ! x
      context.stop(self)

    case ConfirmedClosed =>
      // println("[WORKER] connection CONFIRMED closed")

    case UpgradedToWebSocket =>
      // println("[WORKER] upgraded to websocket - sender: "+sender)

    case x =>
      println("[WORKER] recieved unknown message: "+x)
  }

  def businessLogicNoUpgrade: Receive = {
    runRoute {
      pathEndOrSingleSlash {
        getFromResource("www/index.html")
      } ~
      getFromResourceDirectory("www")
    }
  }
}
