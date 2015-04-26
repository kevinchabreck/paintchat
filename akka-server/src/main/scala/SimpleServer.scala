package spray.can.websocket.examples

import akka.actor.{ ActorSystem, Actor, Props, ActorLogging, ActorRef, ScalaActorRef, ActorRefFactory, Terminated}
import akka.io.IO
import spray.can.Http
import spray.can.server.UHttp
import spray.can.websocket
import spray.can.websocket.frame.{Frame, BinaryFrame, TextFrame }
import spray.http.HttpRequest
import spray.can.websocket.{ UpgradedToWebSocket, FrameCommandFailed }
import spray.routing.HttpServiceActor
import spray.json._
import DefaultJsonProtocol._
import akka.io.Tcp.{ConnectionClosed, PeerClosed, ConfirmedClosed}
import akka.routing.ActorRefRoutee
import akka.routing.Router

case class ForwardFrame(frame: Frame)

object SimpleServer extends App with MySslConfiguration {

  final case class Push(msg: String)

  // class ClientRouter extends Actor {
  //   var router = {
  //     val routees = Vector.fill(5) {
  //       val r = context.actorOf(Props[Worker])
  //       context watch r
  //       ActorRefRoutee(r)
  //     }
  //     Router(BroadcastLogic(), routees)
  //   }

  //   def receive = {

  //     case x @ (_: TextFrame) =>
  //       router.route(x, sender())
  //     case Terminated(a) =>
  //       router = router.removeRoutee(a)
  //       val r = context.actorOf(Props[Worker])
  //       context watch r
  //       router = router.addRoutee(r)
  //   }
  // }

  object WebSocketServer {
    def props() = Props(classOf[WebSocketServer])
  }
  class WebSocketServer extends Actor with ActorLogging {

    // import context.dispatcher
    val clients = collection.mutable.Map[ActorRef, String]()
    val paintbuffer = new collection.mutable.ListBuffer[String]

    def receive = {
      // when a new connection comes in we register a WebSocketConnection actor as the per connection handler
      case Http.Connected(remoteAddress, localAddress) =>
        // println("new connection "+remoteAddress+" - creating new actor")
        val conn = context.actorOf(WebSocketWorker.props(sender(), self))
        context.watch(conn)
        sender() ! Http.Register(conn)

      case UpgradedToWebSocket =>
        clients(sender()) = ""
        // print("connected clients: "+clients.size+"                    \r")

      case msg: TextFrame =>
        val _ = msg.payload.utf8String.split(":",2).toList match {

          case "PAINT"::data::_ =>
            paintbuffer += data
            clients.keys.foreach(_.forward(ForwardFrame(msg)))

          case "GETBUFFER"::_ =>
            sender() ! Push("PAINTBUFFER:"+paintbuffer.toList.toJson)

          case "USERNAME"::username::_ =>
            // println("[SERVER] new user: "+username)
            clients(sender()) = username
            sender() ! Push("ACCEPTED:"+username)
            clients.keys.filter(_ != sender()).foreach(_ ! Push("INFO:"+username+" has joined"))

          case "RESET"::_ =>
            sender() ! Push("SRESET:")
            clients.keys.filter(_ != sender()).foreach(_ ! Push("RESET:"+clients(sender())))
            paintbuffer.clear()

          case "CHAT"::_ =>

          case _ =>
            println("[SERVER] recieved unrecognized textframe: "+msg.payload.utf8String)
        }

      case x: ConnectionClosed =>
        clients -= sender()
        // print("connected clients: "+clients.size+"                    \r")

      case x: Terminated =>
        clients -=(sender())
        // print("connected clients: "+clients.size+"                    \r")

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

    // val server = self.getContext().parent
    // var handshakes = 0

    def businessLogic: Receive = {

      case x @ (_: BinaryFrame | _: TextFrame) =>
        sender() ! x

      // case x @ (_: BinaryFrame) =>
      //   sender() ! x

      case msg: TextFrame =>
        // val m = msg.payload.utf8String
        // println("sending frame to parent")
        parent ! msg

      case ForwardFrame(f) =>
        // println("[WORKER] recieved a ForwardFrame: "+f.payload.utf8String)
        // println("sender: "+sender())
        send(f)

      case Push(msg) =>
        // println("[WORKER] recieved a Push: "+msg)
        // println("sender: "+sender())
        send(TextFrame(msg))

      case x: FrameCommandFailed =>
        // log.error("frame command failed", x)

      // should never happen... right?
      case x: HttpRequest =>
        // println("[WORKER] got an http request: "+x)

      // onClose
      case x: ConnectionClosed =>
        // println("[WORKER] connection closing...")
        // parent ! x
        context.stop(self)

      case ConfirmedClosed =>
        // println("[WORKER] connection CONFIRMED closed")

      case UpgradedToWebSocket =>
        // println("[WORKER] upgraded to websocket - sender(): "+sender())
        // parent forward UpgradedToWebSocket

      case x =>
        println("[WORKER] recieved unknown message: "+x)
    }

    def businessLogicNoUpgrade: Receive = {
      implicit val refFactory: ActorRefFactory = context
      runRoute {
        getFromResourceDirectory(".")
      }
    }
  }

  class ServerManager extends Actor {

    implicit val system = context.system
    import system.dispatcher

    override def receive: Receive = {
      case ("start", port:Int) =>
        // println("about to start server on port "+port)
        val server = system.actorOf(WebSocketServer.props(), "server")
        // IO(UHttp) ! Http.Bind(server, "localhost", port)
        IO(UHttp) ! Http.Bind(server, "0.0.0.0", port)

      case Http.Bound(x) => println("server listening on "+x)

      case x: Http.CommandFailed =>
        println("CommandFailed! (probably couldn't initialize server): "+x)

      case "stop" =>
        println("server manager stopping")

      case x =>
        println("unknown message delivered to SERVER MANAGER... (maybe CONFIRMED closed??): "+x)
    }
  }

  def doMain() {
    implicit val system = ActorSystem()
    // import system.dispatcher

    // val messageRouter: ActorRef = system.actorOf(BroadcastGroup([]).props(), "messageRouter")

    val serverManager: ActorRef = system.actorOf(Props[ServerManager], "servermanager")
    serverManager ! ("start", 8080)
    // serverManager ! ("start")

    readLine("Hit ENTER to exit ...\n")
    println("shutting down server")
    serverManager ! "stop"
    system.shutdown()
    system.awaitTermination()
  }

  // def doMain() {
  //   implicit val system = ActorSystem()
  //   import system.dispatcher

  //   val server = system.actorOf(WebSocketServer.props(), "websocket")

  //   IO(UHttp) ! Http.Bind(server, "localhost", 8080)

  //   readLine("Hit ENTER to exit ...\n")
  //   system.shutdown()
  //   system.awaitTermination()
  // }

  // because otherwise we get an ambiguous implicit if doMain is inlined
  doMain()
}