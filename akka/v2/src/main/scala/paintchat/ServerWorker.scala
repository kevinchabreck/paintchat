package paintchat

import akka.actor.{Actor, ActorRef, ActorLogging, Terminated}
import akka.io.Tcp.ConnectionClosed
import akka.pattern.ask
import akka.util.Timeout
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.{Publish, Subscribe, SubscribeAck}
import akka.cluster.singleton.{ClusterSingletonProxy, ClusterSingletonProxySettings}
import spray.can.Http
import spray.can.websocket.UpgradedToWebSocket
import collection.mutable.{HashMap, ListBuffer}
import scala.concurrent.Await
import scala.concurrent.duration._

case object ServerStatus
case class  ServerState(connections: Int)

sealed trait ServerUpdate
case class Accepted(username:String) extends ServerUpdate
case class UserJoin(username:String, client:ActorRef) extends ServerUpdate
case class UserLeft(username:String) extends ServerUpdate
case class UserCount(usercount:Int) extends ServerUpdate
case class PaintBuffer(pbuffer:ListBuffer[String]) extends ServerUpdate
case class UserList(userlist:Iterable[String]) extends ServerUpdate

class ServerWorker extends Actor with ActorLogging {
  val clients = new HashMap[ActorRef, String]
  var pbuffer = new ListBuffer[String]
  var buffervalid = false
  val userlist = new ListBuffer[String]
  val mediator = DistributedPubSub(context.system).mediator
  mediator ! Subscribe("client_update", self)
  mediator ! Subscribe("canvas_update", self)
  mediator ! Subscribe("buffer_update", self)
  var bufferproxy = context.actorOf(ClusterSingletonProxy.props(
    singletonManagerPath = "/user/buffer-manager",
    settings = ClusterSingletonProxySettings(context.system)),
    name = "buffer-proxy")

  def receive = {
    case SubscribeAck(Subscribe(topic, None, `self`)) =>
    case UpgradedToWebSocket => clients(sender) = ""
    case ServerStatus => sender ! ServerState(clients.size)
    case c:Chat => clients.keys.foreach(_ ! c)
    case GetUserList => sender ! UserList(userlist)
    case BufferUpdated => buffervalid = false

    case Http.Connected(remoteAddress, localAddress) =>
      val connection = context.watch(context.actorOf(ClientWorker.props(sender, bufferproxy, mediator)))
      sender ! Http.Register(connection)

    case _:ConnectionClosed | _:Terminated =>
      mediator ! Publish("client_update", UserLeft(clients(sender)))
      clients -= sender

    case GetBuffer =>
      if (!buffervalid) {
        try {
          implicit val timeout = Timeout(5 seconds)
          val f = ask(bufferproxy, GetBuffer).mapTo[PaintBuffer]
          val PaintBuffer(buffer) = Await.result(f, timeout.duration)
          pbuffer = buffer
          buffervalid = true
        } catch {
          case scala.util.control.NonFatal(e) => log.error("read repair failed...")
        }
      }
      sender ! PaintBuffer(pbuffer)

    case Paint(data) =>
      clients.keys.foreach(_ ! Paint(data))
      pbuffer += data

    case r:Reset =>
      clients.keys.foreach(_ ! r)
      pbuffer.clear()

    case UserName(username) =>
      clients(sender) = username
      sender ! Accepted(username)
      mediator ! Publish("client_update", UserJoin(username,sender))

    case UserJoin(username,client) =>
      userlist += username
      clients.keys.foreach(_ ! UserJoin(username,client))
      clients.keys.foreach(_ ! UserCount(userlist.size))

    case UserLeft(username) =>
      userlist -= username
      clients.keys.foreach(_ ! UserLeft(username))
      clients.keys.foreach(_ ! UserCount(userlist.size))

    case x => log.warning(s"[SERVER] recieved unknown message from $sender: $x")
  }
}