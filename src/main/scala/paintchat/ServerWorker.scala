package paintchat

import akka.actor.{Actor, ActorLogging, ActorRef, Terminated}
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.{Publish, Subscribe}
import akka.cluster.singleton.{ClusterSingletonProxy, ClusterSingletonProxySettings}
import akka.io.Tcp.ConnectionClosed
import collection.mutable

case object ServerStatus
case class  ServerState(connections: Int)
case object Connected

sealed trait ServerUpdate
case class PaintBuffer(buffer:mutable.ListBuffer[String]) extends ServerUpdate
case class Accepted(username:String) extends ServerUpdate
case class UserJoin(username:String, client:ActorRef) extends ServerUpdate
case class UserLeft(username:String) extends ServerUpdate
case class UserList(userlist:Iterable[String]) extends ServerUpdate
case class UserCount(usercount:Int) extends ServerUpdate

class ServerWorker extends Actor with ActorLogging {
  val clients = new mutable.HashMap[ActorRef, String]
  var pbuffer = new mutable.ListBuffer[String]
  val userlist = new mutable.ListBuffer[String]
  var buffervalid = false

  var bufferproxy = context.actorOf(ClusterSingletonProxy.props(
    singletonManagerPath = "/user/buffer-manager",
    settings = ClusterSingletonProxySettings(context.system)),
    name = "buffer-proxy")

  val mediator = DistributedPubSub(context.system).mediator
  mediator ! Subscribe("client_update", self)
  mediator ! Subscribe("canvas_update", self)
  mediator ! Subscribe("buffer_update", self)

  def broadcast(m:Any) = clients.keys.foreach(_ ! m)

  def receive = {
    case ServerStatus => sender ! ServerState(clients.size)
    case c:Chat => broadcast(c)
    case GetUserList => sender ! UserList(userlist)
    case BufferUpdated => buffervalid = false

    case PaintBuffer(buffer) =>
      pbuffer = buffer
      buffervalid = true

    case BufferReset =>
      pbuffer.clear()
      buffervalid = true

    case Connected =>
      clients(sender) = ""
      context.watch(sender())

    case _:ConnectionClosed | _:Terminated =>
      mediator ! Publish("client_update", UserLeft(clients(sender)))
      clients -= sender

    case GetBuffer =>
      if (buffervalid) {
        sender ! PaintBuffer(pbuffer)
      } else {
        bufferproxy.forward(GetBuffer)
        bufferproxy ! GetBuffer
      }

    case Paint(data) =>
      broadcast(Paint(data))
      pbuffer += data

    case r:Reset =>
      broadcast(r)
      pbuffer.clear()

    case UserName(username) =>
      clients(sender) = username
      sender ! Accepted(username)
      mediator ! Publish("client_update", UserJoin(username,sender))

    case UserJoin(username,client) =>
      userlist += username
      broadcast(UserJoin(username,client))
      broadcast(UserCount(userlist.size))

    case UserLeft(username) =>
      userlist -= username
      broadcast(UserLeft(username))
      broadcast(UserCount(userlist.size))
  }
}