package paintchat

import akka.actor.{Actor, ActorRef, ActorLogging, Terminated}
import akka.io.Tcp.ConnectionClosed
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.{Publish, Subscribe, SubscribeAck}
import spray.can.Http
import spray.can.websocket.UpgradedToWebSocket
import collection.mutable.{HashMap, ListBuffer}

sealed trait Status
case object ServerStatus extends Status
case class ServerInfo(connections: Int) extends Status

sealed trait ServerUpdate
case class Accepted(username: String) extends ServerUpdate
case class UserJoin(username: String, client:ActorRef) extends ServerUpdate
case class UserLeft(username: String) extends ServerUpdate
case class UserCount(usercount: Int) extends ServerUpdate
case class PaintBuffer(pbuffer: ListBuffer[String]) extends ServerUpdate
case class UserList(userlist: Iterable[String]) extends ServerUpdate
case class UpdatedBuffer(pbuffer: Iterable[String], userlist: Iterable[String], usercount: Int) extends ServerUpdate

class ServerWorker extends Actor with ActorLogging {
  val clients = new HashMap[ActorRef, String]
  val pbuffer = new ListBuffer[String]
  val userlist = new ListBuffer[String]
  val mediator = DistributedPubSub(context.system).mediator
  mediator ! Subscribe("update", self)

  def receive = {
    case SubscribeAck(Subscribe(topic, None, `self`)) => println(s"subscribed to topic: $topic")
    case UpgradedToWebSocket => clients(sender) = ""
    case ServerStatus => sender ! ServerInfo(clients.size)
    case c:Chat => clients.keys.foreach(_ ! c)
    case GetBuffer => sender ! PaintBuffer(pbuffer)
    case GetUserList => sender ! UserList(userlist)
    case GetUpdated => sender ! UpdatedBuffer(pbuffer, userlist, userlist.size)
    case UpdatedBuffer(p,ul,uc) =>
      pbuffer ++= p
      userlist ++= ul

    case Http.Connected(remoteAddress, localAddress) =>
      val connection = context.watch(context.actorOf(ClientWorker.props(sender, self, mediator)))
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

    case UserJoin(username,client) =>
      clients.keys.foreach(_ ! UserJoin(username,client))
      userlist += username
      clients.keys.foreach(_ ! UserCount(userlist.size))

    case UserLeft(username) =>
      clients.keys.foreach(_ ! UserLeft(username))
      userlist -= username
      clients.keys.foreach(_ ! UserCount(userlist.size))

    case x => println(s"[SERVER] recieved unknown message from $sender: $x")
  }
}