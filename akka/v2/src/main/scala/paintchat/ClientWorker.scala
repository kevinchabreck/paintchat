package paintchat

import akka.actor.{ActorRef, Address, Props}
import akka.io.Tcp.ConnectionClosed
import akka.util.Timeout
import akka.pattern.ask
import akka.cluster.{Cluster, Member}
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import spray.can.server.UHttp
import spray.can.websocket.{WebSocketServerWorker, UpgradedToWebSocket, FrameCommandFailed}
import spray.can.websocket.frame.TextFrame
import spray.routing.HttpServiceActor
import scala.concurrent.Await
import scala.concurrent.duration._

import play.api.libs.json.{Json, Writes, JsValue, JsString}

sealed trait ClientUpdate
case class Paint(data:String) extends ClientUpdate
case class UserName(username:String) extends ClientUpdate
case class Chat(username:String, message:String, client:ActorRef) extends ClientUpdate
case class Reset(username:String, client:ActorRef) extends ClientUpdate
case object GetBuffer extends ClientUpdate
case object GetUserList extends ClientUpdate

object ClientWorker {
  def props(serverConnection:ActorRef, parent:ActorRef, mediator:ActorRef) = Props(classOf[ClientWorker], serverConnection, parent, mediator)
}
class ClientWorker(val serverConnection:ActorRef, val parent:ActorRef, val mediator:ActorRef) extends HttpServiceActor with WebSocketServerWorker {

  var username = "anonymous"

  override def receive = handshaking orElse businessLogicNoUpgrade orElse closeLogic

  def businessLogicNoUpgrade: Receive = {
    runRoute {
      pathEndOrSingleSlash {
        getFromResource("www/index.html")
      } ~
      path("status") {
        complete(statusJSON.toString)
      } ~
      path("paintbuffer") {
        complete(pbufferJSON.toString)
      } ~
      getFromResourceDirectory("www")
    }
  }

  def businessLogic: Receive = {
    case UpgradedToWebSocket => parent ! UpgradedToWebSocket
    case x:FrameCommandFailed => println(s"frame command failed: $x")
    case x:ConnectionClosed => context.stop(self)
    case frame:TextFrame => handleTextFrame(frame)
    case Paint(data) => send(TextFrame(s"PAINT:$data"))
    case UserJoin(username, client) => if (client!=self) {send(TextFrame(s"INFO:$username has joined"))}
    case UserLeft(username) => send(TextFrame(s"INFO:$username has left"))
    // case UserJoin(username, client) => if (client!=self) {send(TextFrame(s"USERJOIN:$username"))}
    // case UserLeft(username) => send(TextFrame(s"USERLEFT:$username"))
    case PaintBuffer(pbuffer) => send(TextFrame(s"PAINTBUFFER:${Json.toJson(pbuffer)}"))
    case UserList(userlist) => send(TextFrame(s"USERLIST:"+userlist.mkString(" ")))
    case UserCount(usercount) => send(TextFrame(s"USERCOUNT:${usercount}"))


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
      case "GETUSERLIST"::_ => parent ! GetUserList
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
      "cluster_address" -> Cluster(context.system).selfAddress,
      "cluster_state" -> Json.obj(
        "leader" -> clusterstatus.leader,
        "members" -> clusterstatus.members
      )
    )
  }

  def pbufferJSON: JsValue = {
    implicit val timeout = Timeout(1 seconds)
    val fs = ask(parent, GetBuffer)
    val PaintBuffer(pbuffer) = Await.result(fs, timeout.duration)
    return Json.toJson(pbuffer)
  }
}