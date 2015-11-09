package paintchat

import akka.actor.{Actor, ActorRef, ActorSystem, Address, Props, ActorLogging, Terminated}
import akka.io.IO
import akka.io.Tcp.{ConnectionClosed, ConfirmedClosed}
import akka.util.Timeout
import akka.pattern.ask
import akka.cluster.{Cluster, ClusterEvent, Member}
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.{Publish, Subscribe, SubscribeAck}

import scala.concurrent.Await
import scala.concurrent.duration._

import spray.can.server.UHttp
import spray.can.{Http, websocket}
import spray.can.websocket.{UpgradedToWebSocket, FrameCommandFailed}
import spray.can.websocket.frame.{Frame, BinaryFrame, TextFrame}
import spray.http.HttpRequest
import spray.routing.HttpServiceActor

import play.api.libs.json.{Json, Writes, JsValue, JsString}
import com.typesafe.config.ConfigFactory

sealed trait Status
case object ServerStatus extends Status
case class ServerInfo(connections: Int) extends Status
case object ClusterStatus extends Status

sealed trait ServerMessage
case class Push(msg: String) extends ServerMessage
case class ForwardFrame(frame: Frame) extends ServerMessage
case class UserJoin(user: String) extends ServerMessage
case class UserLeft(user: String) extends ServerMessage

case class Update(msg:TextFrame, publisher:ActorRef)

object PaintChat extends App with MySslConfiguration {

  val config = ConfigFactory.load()
  val interface = config.getString("app.interface")
  val default_http_port = config.getInt("app.port")
  val default_tcp_port = config.getInt("akka.remote.netty.tcp.port")

  // helper method for local cluster testing
  def bindTCPPort(port: Int): ActorSystem = {
    try {
      val config = ConfigFactory.parseString("akka.remote.netty.tcp.port=" + port).withFallback(ConfigFactory.load())
      return ActorSystem("ClusterSystem", config)
    } catch {
      case _ : Throwable =>
        if (port == default_tcp_port)
          return bindTCPPort(0)
        else
          println(s"Error: TCP port bind failed")
          return ActorSystem("ClusterSystem")
    }
  }

  // helper method for local cluster testing
  def bindHTTPPort(port: Int): Unit = {
    implicit val timeout = Timeout(1 seconds)
    val bind_future = ask(IO(UHttp), Http.Bind(server, interface, port))
    val bind_result = Await.result(bind_future, timeout.duration)
    bind_result match {
      case Http.Bound(x) => scala.io.StdIn.readLine(s"server listening on http:/$x (press ENTER to exit)\n")
      case x: Http.CommandFailed =>
        if (port < default_http_port+3) {
          bindHTTPPort(port+1)
        } else {
          println(s"Error: unable to bind to a port")
        }
    }
  }

  implicit val system = bindTCPPort(default_tcp_port)
  val server = system.actorOf(Props(classOf[Server]), "paintchat-server")
  bindHTTPPort(default_http_port)
  println("shutting down server")
  system.terminate()
  Await.result(system.whenTerminated, Duration.Inf)

  // implicit val system = ActorSystem("paintchat-system")
  // val config = system.settings.config
  // val server = system.actorOf(Props(classOf[Server]), "paintchat-server")
  // val interface = config.getString("app.interface")
  // val defaultport = config.getInt("app.port")
  // implicit val timeout = Timeout(1 seconds)
  // val bind_future = ask(IO(UHttp), Http.Bind(server, interface, port))
  // val bind_result = Await.result(bind_future, timeout.duration)
  // bind_result match {
  //   // case Http.Bound(x) => scala.io.StdIn.readLine(s"server listening on $x (press ENTER to exit)\n")
  //   case Http.Bound(x) => scala.io.StdIn.readLine(s"server listening on http:/$x (press ENTER to exit)\n")
  //   case x: Http.CommandFailed => println(s"Error: Failed to bind to $interface:$port: $x")
  // }
  // println("shutting down server")
  // system.terminate()
  // Await.result(system.whenTerminated, Duration.Inf)
}

class PaintClusterListener extends Actor with ActorLogging {
  val cluster = Cluster(context.system)

  override def preStart(): Unit = {
    println(s"starting ClusterListener: ${self.path.name}")
    cluster.subscribe(self,
      initialStateMode=ClusterEvent.InitialStateAsEvents,
      classOf[ClusterEvent.MemberUp],
      classOf[ClusterEvent.MemberExited],
      classOf[ClusterEvent.MemberRemoved],
      classOf[ClusterEvent.UnreachableMember],
      classOf[ClusterEvent.ReachableMember],
      classOf[ClusterEvent.MemberEvent]
    )
  }

  override def postStop(): Unit = {
    println(s"stopping ClusterListener: ${self.path.name}")
    cluster.unsubscribe(self)
  }

  def receive = {
    case ClusterEvent.MemberUp(member) => println(s"Member up: ${member.address}")
    case ClusterEvent.UnreachableMember(member) => println(s"Member unreachable: ${member}")
    case ClusterEvent.MemberRemoved(member, previousStatus) => println(s"Member removed: ${member.address} after ${previousStatus}")
    case event: ClusterEvent.MemberEvent => println(s"recieved ClusterEvent.MemberEvent: $event")
    case state: ClusterEvent.CurrentClusterState => println(s"recieved CurrentClusterState: $state")
    case ClusterStatus => sender ! Cluster(context.system).state
  }
}

class Server extends Actor with ActorLogging {
  val clients = new collection.mutable.HashMap[ActorRef, String]
  val pbuffer = new collection.mutable.ListBuffer[String]
  val cluster = context.watch(context.actorOf(Props(classOf[PaintClusterListener]), "paintchat-cluster"))
  val mediator = DistributedPubSub(context.system).mediator
  mediator ! Subscribe("updates", self)

  def receive = {
    case SubscribeAck(Subscribe(topic, None, `self`)) => println(s"subscripted to topic: $topic")

    case Http.Connected(remoteAddress, localAddress) =>
      val connection = context.watch(context.actorOf(WebSocketWorker.props(sender, self)))
      sender ! Http.Register(connection)

    case UpgradedToWebSocket => clients(sender) = ""

    case _:ConnectionClosed | _:Terminated => clients -= sender

    case ServerStatus => sender ! ServerInfo(clients.size)

    case Update(msg, publisher) => if (publisher != self) {handleTextFrame(msg, self)}

    case msg: TextFrame => handleTextFrame(msg, sender)

    case x => println(s"[SERVER] recieved unknown message: $x")
  }

  def handleTextFrame(msg: TextFrame, sender: ActorRef) = {
    msg.payload.utf8String.split(":",2).toList match {
      case "PAINT"::data::_ =>
        pbuffer += data
        if (sender != self) {mediator ! Publish("updates", Update(msg, self))}
        clients.keys.foreach(_.forward(ForwardFrame(msg)))

      case "GETBUFFER"::_ =>
        sender ! Push("PAINTBUFFER:"+Json.toJson(pbuffer))

      case "USERNAME"::username::_ =>
        clients(sender) = username
        sender ! Push("ACCEPTED:"+username)
        clients.keys.foreach(_.forward(UserJoin(username)))

      case "RESET"::_ =>
        sender ! Push("SRESET:")
        clients.keys.filter(_ != sender).foreach(_ ! Push("RESET:"+clients(sender)))
        pbuffer.clear()

      case "CHAT"::message::_ =>
        val m = s"CHAT:${clients(sender)}:$message"
        clients.keys.filter(_ != sender).foreach(_ ! Push(m))

      case _ =>
        println(s"[SERVER] recieved unrecognized textframe: ${msg.payload.utf8String}")
    }
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

    case UserJoin(user) => if (sender!=self) {send(TextFrame(s"INFO:$user has joined"))}

    case x: FrameCommandFailed => println(s"frame command failed: $x")

    case x: ConnectionClosed =>
      parent ! x
      context.stop(self)

    case ConfirmedClosed => println(s"[WORKER $self] ConfirmedClosed")

    case x => println("[WORKER] recieved unknown message: $x")
  }

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
    val fs = ask(parent, ServerStatus).mapTo[ServerInfo]
    val fc = ask(context.actorSelection("../paintchat-cluster"), ClusterStatus).mapTo[ClusterEvent.CurrentClusterState]
    val ServerInfo(connections) = Await.result(fs, timeout.duration)
    val clusterstatus = Await.result(fc, timeout.duration)
    return Json.obj(
      "status" -> "Up",
      "uptime" -> context.system.uptime,
      "client_connections" -> connections,
      "cluster" -> Json.obj(
        "leader" -> clusterstatus.leader,
        "members" -> clusterstatus.members
      )
    )
  }

  def businessLogicNoUpgrade: Receive = {
    runRoute {
      pathEndOrSingleSlash {
        getFromResource("www/index.html")
      } ~
      path("status") {
        complete(statusJSON.toString)
      } ~
      getFromResourceDirectory("www")
    }
  }
}
