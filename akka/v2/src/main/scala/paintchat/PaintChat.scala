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
import collection.mutable.{HashMap, ListBuffer}

import spray.can.server.UHttp
import spray.can.{Http, websocket}
import spray.can.websocket.{UpgradedToWebSocket, FrameCommandFailed}
import spray.can.websocket.frame.{Frame, BinaryFrame, TextFrame}
import spray.http.HttpRequest
import spray.routing.HttpServiceActor

import play.api.libs.json.{Json, Writes, JsValue, JsString}
import com.typesafe.config.ConfigFactory

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
        if (port == default_tcp_port) {
          return bindTCPPort(0)
        } else {
          println(s"Error: TCP port bind failed")
          return ActorSystem("ClusterSystem")
        }
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
  val cluster = system.actorOf(Props(classOf[PaintClusterListener]), "paintchat-cluster")
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
  }
}

sealed trait Status
case object ServerStatus extends Status
case class ServerInfo(connections: Int) extends Status

sealed trait ServerUpdate
case class Accepted(username: String) extends ServerUpdate
case class UserJoin(username: String, client:ActorRef) extends ServerUpdate
case class UserLeft(username: String) extends ServerUpdate
case class PaintBuffer(pbuffer: ListBuffer[String]) extends ServerUpdate

sealed trait ClientUpdate
case class Paint(data:String) extends ClientUpdate
case class UserName(username:String) extends ClientUpdate
case class Chat(username:String, message:String, client:ActorRef) extends ClientUpdate
case class Reset(username:String, client:ActorRef) extends ClientUpdate
case object GetBuffer extends ClientUpdate

class Server extends Actor with ActorLogging {
  val clients = new HashMap[ActorRef, String]
  val pbuffer = new ListBuffer[String]
  val mediator = DistributedPubSub(context.system).mediator
  mediator ! Subscribe("update", self)

  def receive = {
    case SubscribeAck(Subscribe(topic, None, `self`)) => println(s"subscribed to topic: $topic")
    case UpgradedToWebSocket => clients(sender) = ""
    case ServerStatus => sender ! ServerInfo(clients.size)
    case c:Chat => clients.keys.foreach(_ ! c)
    case u:UserJoin => clients.keys.foreach(_ ! u)
    case u:UserLeft => clients.keys.foreach(_ ! u)
    case GetBuffer => sender ! PaintBuffer(pbuffer)

    case Http.Connected(remoteAddress, localAddress) =>
      val connection = context.watch(context.actorOf(WebSocketWorker.props(sender, self, mediator)))
      sender ! Http.Register(connection)

    case _:ConnectionClosed | _:Terminated =>
      mediator ! Publish("update", UserLeft(clients(sender)))
      clients -= sender

    case Paint(data) =>
      clients.keys.foreach(_ ! Paint(data))
      pbuffer += data

    case r:Reset =>
      clients.keys.foreach(_ ! r)
      pbuffer.clear()

    case UserName(username) =>
      clients(sender) = username
      sender ! Accepted(username)
      mediator ! Publish("update", UserJoin(username,sender))

    case x => println(s"[SERVER] recieved unknown message from $sender: $x")
  }
}

object WebSocketWorker {
  def props(serverConnection:ActorRef, parent:ActorRef, mediator:ActorRef) = Props(classOf[WebSocketWorker], serverConnection, parent, mediator)
}
class WebSocketWorker(val serverConnection:ActorRef, val parent:ActorRef, val mediator:ActorRef) extends HttpServiceActor with websocket.WebSocketServerWorker {

  var username = "anonymous"

  override def receive = handshaking orElse businessLogicNoUpgrade orElse closeLogic

  def businessLogic: Receive = {
    case UpgradedToWebSocket => parent ! UpgradedToWebSocket
    case x:FrameCommandFailed => println(s"frame command failed: $x")
    case x:ConnectionClosed => context.stop(self)
    case frame:TextFrame => handleTextFrame(frame)
    case Paint(data) => send(TextFrame(s"PAINT:$data"))
    case UserJoin(username, client) => if (client!=self) {send(TextFrame(s"INFO:$username has joined"))}
    case UserLeft(username) => send(TextFrame(s"INFO:$username has left"))
    case PaintBuffer(pbuffer) => send(TextFrame(s"PAINTBUFFER:${Json.toJson(pbuffer)}"))

    case Accepted(username) =>
      send(TextFrame(s"ACCEPTED:$username"))
      this.username = username

    case Chat(username, message, client) =>
      val s = if (client!=self) username else "Me"
      send(TextFrame(s"CHAT:$s:$message"))

    case Reset(username, client) =>
      val rs_msg = if (client!=self) s"RESET:$username" else "SRESET:"
      send(TextFrame(rs_msg))

    case x => println(s"[WORKER] recieved unknown message: $x")
  }

  def handleTextFrame(frame: TextFrame) = {
    frame.payload.utf8String.split(":",2).toList match {
      case "PAINT"::data::_ => mediator ! Publish("update", Paint(data))
      case "CHAT"::message::_ => mediator ! Publish("update", Chat(username,message,self))
      case "RESET"::_ => mediator ! Publish("update", Reset(username, self))
      case "USERNAME"::username::_ => parent ! UserName(username)
      case "GETBUFFER"::_ => parent ! GetBuffer
      case _ => println(s"unrecognized textframe: ${frame.payload.utf8String}")
    }
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
    val ServerInfo(connections) = Await.result(fs, timeout.duration)
    val clusterstatus = Cluster(context.system).state
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
