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
import spray.can.websocket.{UpgradedToWebSocket, FrameCommandFailed}
import spray.can.websocket.frame.{Frame, BinaryFrame, TextFrame}
import spray.http.HttpRequest
import spray.routing.HttpServiceActor
import spray.json._
import DefaultJsonProtocol._

case class Push(msg: String)
case class ForwardFrame(frame: Frame)

case object ServerStatus
case class ServerInfo(connections: Int)

object Server extends App with MySslConfiguration {
  implicit val system = ActorSystem("paintchat-system")
  val server = system.actorOf(Props(classOf[Server]), "paintchat-server")
  val config = system.settings.config
  val interface = config.getString("app.interface")
  val port = config.getInt("app.port")

  implicit val timeout = Timeout(1 seconds)
  val bind_future = ask(IO(UHttp), Http.Bind(server, interface, port))
  val bind_result = Await.result(bind_future, timeout.duration)
  bind_result match {
    case Http.Bound(x) => scala.io.StdIn.readLine(s"server listening on $x (press ENTER to exit)\n")
    case x: Http.CommandFailed => println(s"Error: Failed to bind to $interface:$port: $x")
  }
  println("shutting down server")
  system.shutdown()
  system.awaitTermination()
}
class Server extends Actor with ActorLogging {

  val clients = collection.mutable.Map[ActorRef, String]()
  val paintbuffer = new collection.mutable.ListBuffer[String]

  def receive = {
    case Http.Connected(remoteAddress, localAddress) =>
      val conn = context.actorOf(WebSocketWorker.props(sender, self))
      context.watch(conn)
      sender ! Http.Register(conn)

    case UpgradedToWebSocket => clients(sender) = ""

    case x: ConnectionClosed => clients -= sender

    case x: Terminated => clients -= sender

    case ServerStatus => sender ! ServerInfo(clients.size)

    case msg: TextFrame =>
      val _ = msg.payload.utf8String.split(":",2).toList match {

        case "PAINT"::data::_ =>
          paintbuffer += data
          clients.keys.foreach(_.forward(ForwardFrame(msg)))

        case "GETBUFFER"::_ =>
          sender ! Push("PAINTBUFFER:"+paintbuffer.toList.toJson)

        case "USERNAME"::username::_ =>
          clients(sender) = username
          sender ! Push("ACCEPTED:"+username)
          clients.keys.filter(_ != sender).foreach(_ ! Push("INFO:"+username+" has joined"))

        case "RESET"::_ =>
          sender ! Push("SRESET:")
          clients.keys.filter(_ != sender).foreach(_ ! Push("RESET:"+clients(sender)))
          paintbuffer.clear()

        case "CHAT"::message::_ =>
          val m = "CHAT:"+clients(sender)+":"+message
          clients.keys.filter(_ != sender).foreach(_ ! Push(m))

        case _ =>
          println("[SERVER] recieved unrecognized textframe: "+msg.payload.utf8String)
      }

    case x =>
      println("[SERVER] recieved unknown message: "+x)
  }

}

object WebSocketWorker {
  def props(serverConnection: ActorRef, parent: ActorRef) = Props(classOf[WebSocketWorker], serverConnection, parent)
}
class WebSocketWorker(val serverConnection: ActorRef, val parent: ActorRef) extends HttpServiceActor with websocket.WebSocketServerWorker {

  override def receive = handshaking orElse businessLogicNoUpgrade orElse closeLogic

  def businessLogic: Receive = {

    case msg: TextFrame => parent ! msg

    case UpgradedToWebSocket => parent ! UpgradedToWebSocket

    case ForwardFrame(f) => send(f)

    case Push(msg) => send(TextFrame(msg))

    case x: FrameCommandFailed => println(s"frame command failed: $x")

    case x: ConnectionClosed =>
      parent ! x
      context.stop(self)

    case ConfirmedClosed => println(s"[WORKER $self] ConfirmedClosed")

    case x => println("[WORKER] recieved unknown message: $x")
  }

  def businessLogicNoUpgrade: Receive = {
    runRoute {
      pathEndOrSingleSlash {
        getFromResource("www/index.html")
      } ~
      path("status") {
        complete(status)
      } ~
      getFromResourceDirectory("www")
    }
  }

  def status: String = {
    implicit val timeout = Timeout(1 seconds)
    val f = ask(parent, ServerStatus).mapTo[ServerInfo]
    val ServerInfo(connections) = Await.result(f, timeout.duration)
    var status = "{"
    status += "status: up, "
    status += s"uptime: ${context.system.uptime}, "
    status += s"connections: $connections"
    status += "}"
    return status
  }
}
