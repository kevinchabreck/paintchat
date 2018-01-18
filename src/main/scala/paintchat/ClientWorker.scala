package paintchat

import akka.actor.{ActorLogging, ActorRef, Address, Props}
import akka.io.Tcp.ConnectionClosed
import akka.util.Timeout
import akka.pattern.ask
import akka.cluster.{Cluster, Member, ClusterEvent}
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import org.apache.commons.lang3.StringEscapeUtils
import spray.can.websocket.{WebSocketServerWorker, UpgradedToWebSocket, FrameCommandFailed}
import spray.can.websocket.frame.TextFrame
import spray.routing.HttpServiceActor
import scala.concurrent.Await
import scala.concurrent.duration._
import play.api.libs.json.{Json, Writes, JsValue, JsString}

sealed trait ClientUpdate
case class  Paint(data:String) extends ClientUpdate
case class  UserName(username:String) extends ClientUpdate
case class  Chat(username:String, message:String, client:ActorRef) extends ClientUpdate
case class  Reset(username:String, client:ActorRef) extends ClientUpdate
case object GetBuffer extends ClientUpdate
case object GetUserList extends ClientUpdate

object ClientWorker {
  def props(serverConnection:ActorRef, bufferproxy:ActorRef, mediator:ActorRef) = Props(classOf[ClientWorker], serverConnection, bufferproxy, mediator)
}
class ClientWorker(val serverConnection:ActorRef, val bufferproxy:ActorRef, val mediator:ActorRef)
                  extends HttpServiceActor with WebSocketServerWorker with ActorLogging {

  var name = "anonymous"
  override def receive = handshaking orElse businessLogicNoUpgrade orElse closeLogic

  def clustersize = Cluster(context.system).state.members.size

  def businessLogicNoUpgrade: Receive = {
    runRoute {
      pathEndOrSingleSlash {
        getFromResource("www/index.html")
      } ~ path("health") {
        complete("{status: up}")
      } ~ path("status") {
        complete(statusJSON.toString)
      } ~ path("paintbuffer") {
        complete(pbufferJSON.toString)
      } ~ getFromResourceDirectory("www")
    }
  }

  def businessLogic: Receive = {
    case UpgradedToWebSocket => handleNewConnection
    case x:ConnectionClosed => context.stop(self)
    case frame:TextFrame => handleTextFrame(frame)
    case _:ClusterEvent.ClusterDomainEvent => send(TextFrame(s"CLUSTERSIZE:$clustersize"))
    case Paint(data) => send(TextFrame(s"PAINT:$data"))
    case Chat(username, message, client) => send(TextFrame("CHAT:"+(if (client!=self) username else "Me")+s":$message"))
    case Reset(username, client) => send(TextFrame(if (client!=self) s"RESET:$username" else "SRESET:"))
    case UserJoin(username, client) => if (client!=self) {send(TextFrame(s"INFO:$username has joined"))}
    case UserLeft(username) => send(TextFrame(s"INFO:$username has left"))
    case PaintBuffer(pbuffer) => send(TextFrame(s"PAINTBUFFER:${Json.toJson(pbuffer)}"))
    case UserList(userlist) => send(TextFrame(s"USERLIST:"+userlist.mkString(" ")))
    case UserCount(usercount) => send(TextFrame(s"USERCOUNT:${usercount}"))
    case Accepted(username) =>
      send(TextFrame(s"ACCEPTED:$username"))
      name = username
    case x:FrameCommandFailed => log.error(s"frame command failed: $x")
    case x => log.warning(s"recieved unknown message: $x")
  }

  def handleNewConnection() {
    context.parent ! UpgradedToWebSocket
    Cluster(context.system).subscribe(self, classOf[ClusterEvent.ClusterDomainEvent])
  }

  def handleTextFrame(frame: TextFrame) = {
    frame.payload.utf8String.split(":",2).toList match {
      case "PAINT"::data::_ => mediator ! Publish("canvas_update", Paint(data))
      case "CHAT"::message::_ => mediator ! Publish("client_update", Chat(name,StringEscapeUtils.escapeHtml4(message),self))
      case "RESET"::_ => mediator ! Publish("canvas_update", Reset(name, self))
      case "USERNAME"::username::_ => context.parent ! UserName(username)
      case "GETBUFFER"::_ => context.parent ! GetBuffer
      case "GETUSERLIST"::_ => context.parent ! GetUserList
      case "PING"::_ => send(TextFrame("PINGACK"))
      case "GETCLUSTERSIZE"::_ => send(TextFrame(s"CLUSTERSIZE:$clustersize"))
      case _ => log.warning(s"unrecognized textframe: ${frame.payload.utf8String}")
    }
  }

  implicit val addressWrites = new Writes[Address] {
    def writes(address: Address) = JsString(address.host.get+":"+address.port.get)
  }

  implicit val memberWrites = new Writes[Member] {
    def writes(member: Member) = Json.obj (
      member.address.host.get+":"+member.address.port.get -> member.status.toString
    )
  }

  def statusJSON: JsValue = {
    implicit val timeout = Timeout(5 seconds)
    val fs = ask(context.parent, ServerStatus).mapTo[ServerState]
    val fb = ask(bufferproxy, BufferStatus).mapTo[BufferState]
    val ServerState(clients) = Await.result(fs, timeout.duration)
    val BufferState(buffaddress, buffsize) = Await.result(fb, timeout.duration)
    val clusterstatus = Cluster(context.system).state
    return Json.obj(
      "status"  -> "Up",
      "uptime"  -> context.system.uptime,
      "clients" -> clients,
      "buffer-manager" -> Json.obj(
        "address" -> buffaddress,
        "size"    -> buffsize),
      "cluster" -> Json.obj(
        "address" -> Cluster(context.system).selfAddress,
        "leader"  -> clusterstatus.leader,
        "members" -> clusterstatus.members)
    )
  }

  def pbufferJSON: JsValue = {
    implicit val timeout = Timeout(1 seconds)
    val fs = ask(context.parent, GetBuffer)
    val PaintBuffer(pbuffer) = Await.result(fs, timeout.duration)
    return Json.toJson(pbuffer)
  }
}