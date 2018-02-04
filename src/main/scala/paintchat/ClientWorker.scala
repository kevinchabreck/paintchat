package paintchat

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.cluster.pubsub.DistributedPubSub
import akka.io.Tcp.ConnectionClosed
import akka.cluster.{Cluster, ClusterEvent}
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import org.apache.commons.text.StringEscapeUtils
import play.api.libs.json.Json

sealed trait ClientUpdate
case class  Paint(data:String) extends ClientUpdate
case class  UserName(username:String) extends ClientUpdate
case class  Chat(username:String, message:String, client:ActorRef) extends ClientUpdate
case class  Reset(username:String, client:ActorRef) extends ClientUpdate
case object GetBuffer extends ClientUpdate
case object GetUserList extends ClientUpdate

object ClientWorker {
  case class Connected(outgoing:ActorRef)
  case class IncomingMessage(text:String)
  case class OutgoingMessage(text:String)
  def props(serverWorker:ActorRef) = Props(classOf[ClientWorker], serverWorker)
}
class ClientWorker(val serverWorker:ActorRef) extends Actor with ActorLogging {

  var name = "anonymous"
  var client:ActorRef = null
  val mediator = DistributedPubSub(context.system).mediator

  Cluster(context.system).subscribe(self, classOf[ClusterEvent.ClusterDomainEvent])

  def clustersize = Cluster(context.system).state.members.size

  def send(text:String) = client ! ClientWorker.OutgoingMessage(text)

  def receive = {
    case ClientWorker.Connected(outgoing) =>
      context.become(connected)
      serverWorker ! Connected
      client = outgoing
  }

  def connected:Receive = {
    case _: ConnectionClosed => context.stop(self)
    case ClientWorker.IncomingMessage(text) => handleIncomingMessage(text)
    case _: ClusterEvent.ClusterDomainEvent => send(s"CLUSTERSIZE:$clustersize")
    case Paint(data) => send(s"PAINT:$data")
    case Chat(username, message, client) => send("CHAT:" + (if (client != self) username else "Me") + s":$message")
    case Reset(username, client) => send(if (client != self) s"RESET:$username" else "SRESET:")
    case UserJoin(username, client) => if (client != self) send(s"INFO:$username has joined")
    case UserLeft(username) => send(s"INFO:$username has left")
    case PaintBuffer(pbuffer) => send(s"PAINTBUFFER:${Json.toJson(pbuffer)}")
    case UserList(userlist) => send(s"USERLIST:" + userlist.mkString(" "))
    case UserCount(usercount) => send(s"USERCOUNT:$usercount")
    case Accepted(username) =>
      send(s"ACCEPTED:$username")
      name = username
    case x => log.warning(s"received unknown message: $x")
  }

  def handleIncomingMessage(text:String) = {
    text.split(":",2).toList match {
      case "PAINT"::data::_ => mediator ! Publish("canvas_update", Paint(data))
      case "CHAT"::message::_ => mediator ! Publish("client_update", Chat(name,StringEscapeUtils.escapeHtml4(message),self))
      case "RESET"::_ => mediator ! Publish("canvas_update", Reset(name, self))
      case "USERNAME"::username::_ => serverWorker ! UserName(username)
      case "GETBUFFER"::_ => serverWorker ! GetBuffer
      case "GETUSERLIST"::_ => serverWorker ! GetUserList
      case "PING"::_ => send("PINGACK")
      case "GETCLUSTERSIZE"::_ => send(s"CLUSTERSIZE:$clustersize")
      case _ => log.warning(s"unrecognized incoming message: $text")
    }
  }
}