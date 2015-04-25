package spray.can.websocket.examples

import akka.actor.{ ActorSystem, Actor, Props, ActorLogging, ActorRef, ScalaActorRef, ActorRefFactory }
import akka.io.IO
import spray.can.Http
import spray.can.server.UHttp
import spray.can.websocket
import spray.can.websocket.frame.{Frame, BinaryFrame, TextFrame }
import spray.http.HttpRequest
import spray.can.websocket.FrameCommandFailed
import spray.routing.HttpServiceActor
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
    // val connectedClients:collection.mutable.Set[ActorRef] = Set()
    // val clients = collection.mutable.Set[ActorRef]()
    val clients = collection.mutable.Map[ActorRef, String]()

    def receive = {
      // when a new connection comes in we register a WebSocketConnection actor as the per connection handler
      case Http.Connected(remoteAddress, localAddress) =>
        println("new connection "+remoteAddress+" - creating new actor")
        val serverConnection = sender()
        val conn = context.actorOf(WebSocketWorker.props(serverConnection, self))
        serverConnection ! Http.Register(conn)
        // clients += conn
        clients(conn) = ""

      case msg: TextFrame =>
        val _ = msg.payload.utf8String.split(":",2).toList match {
          case "PAINT"::data =>
            println("[SERVER] recieved PAINT message")
            println("[SERVER] broadcasting: "+msg.payload.utf8String)
            // clients.foreach(_.forward(ForwardFrame(msg)))
            clients.keys.foreach(_.forward(ForwardFrame(msg)))
            // clients.foreach{ case (client, _) => client.forward(ForwardFrame(msg))}
          // case "GETBUFFER"::_ =>
          case "USERNAME"::username::_ =>
            println("[SERVER] recieved USERNAME message")
            println("[SERVER] new user: "+username)
            sender() ! Push("ACCEPTED:"+username)
            clients.keys.filter(_ != sender()).foreach(_ ! Push("INFO:"+username+" has joined"))
          // case "RESET":: =>
          // case "CHAT":: =>
          case _ =>
            println("[SERVER] recieved unrecognized message: "+msg.payload.utf8String)
        }

      case x: ConnectionClosed =>
        println("[SERVER] registered ConnectionClosed event from "+sender())
        clients -= sender()


      case x =>
        println("Server recieved something other than a new Http connection (maybe a Registered??)")
        println("x: "+x)
    }

  }

  // these are actors - one for each connection
  // object WebSocketWorker {
  //   def props(serverConnection: ActorRef) = Props(classOf[WebSocketWorker], serverConnection)
  // }
  // class WebSocketWorker(val serverConnection: ActorRef) extends HttpServiceActor with websocket.WebSocketServerWorker {
  object WebSocketWorker {
    def props(serverConnection: ActorRef, parent: ActorRef) = Props(classOf[WebSocketWorker], serverConnection, parent)
  }
  class WebSocketWorker(val serverConnection: ActorRef, val parent: ActorRef) extends HttpServiceActor with websocket.WebSocketServerWorker {

    override def receive = handshaking orElse businessLogicNoUpgrade orElse closeLogic

    // val server = self.getContext().parent
    // val server = self.getContext().parent

    def businessLogic: Receive = {

      // recieve binary frame from client
      case x @ (_: BinaryFrame) => sender() ! x

      // recieve text frame from client>
      case msg: TextFrame =>
        val m = msg.payload.utf8String
        println("[WORKER(path = "+self.path+")] recieved textframe: ["+m+"] from "+sender())
        // println("sending ["+m+"] to 'sender()':"+sender())
        // sender() ! msg
        println("sending frame to parent")
        parent ! msg

      case ForwardFrame(f) =>
        println("[WORKER] recieved a ForwardFrame: "+f.payload.utf8String)
        println("sender: "+sender())
        send(f)

      case Push(msg) =>
        println("[WORKER] recieved a Push: "+msg)
        println("sender: "+sender())
        send(TextFrame(msg))

      case x: FrameCommandFailed =>
        log.error("frame command failed", x)

      // should never happen... right?
      case x: HttpRequest =>
        println("[WORKER] got an http request: "+x)

      // onClose
      case x: ConnectionClosed =>
        println("[WORKER] connection closing...")
        parent ! x
        context.stop(self)

      case ConfirmedClosed =>
        println("[WORKER] connection CONFIRMED closed")

      case websocket.UpgradedToWebSocket =>
        println("[WORKER] upgraded to websocket - sender(): "+sender())

      case x =>
        println("[WORKER] unknown message... (maybe CONFIRMED closed??): "+x)
    }

    def businessLogicNoUpgrade: Receive = {
      implicit val refFactory: ActorRefFactory = context
      runRoute {
        println("running route")
        getFromDirectory("resources")
      }
    }
  }

  class ServerManager extends Actor {

    implicit val system = context.system
    import system.dispatcher

    override def receive: Receive = {
      case ("start", port:Int) =>
        println("about to start server on port "+port)
        val server = system.actorOf(WebSocketServer.props(), "server")
        IO(UHttp) ! Http.Bind(server, "localhost", port)

      case Http.Bound(x) => println("server initialized! Listening to "+x)

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