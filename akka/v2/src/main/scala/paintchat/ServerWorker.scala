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
case class PaintBuffer(pbuffer: ListBuffer[String]) extends ServerUpdate

class ServerWorker extends Actor with ActorLogging {
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

    case x => println(s"[SERVER] recieved unknown message from $sender: $x")
  }
}