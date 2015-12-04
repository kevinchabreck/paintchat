package paintchat

import akka.actor.{ActorLogging, Address}
import akka.persistence.{PersistentActor, RecoveryCompleted, SnapshotOffer}
import akka.cluster.Cluster
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.{Publish, Subscribe, SubscribeAck}
import collection.mutable

case object BufferStatus
case class  BufferState(address:Address, size:Int)
case object BufferUpdated
case class  Evt(data:ClientUpdate)

class BufferManager extends PersistentActor with ActorLogging {
  override def persistenceId = "buffer-manager"
  var pbuffer = new mutable.ListBuffer[String]
  val mediator = DistributedPubSub(context.system).mediator
  mediator ! Subscribe("canvas_update", self)

  def handleEvent(e:Evt) = { e match {
      case Evt(Paint(data)) => pbuffer += data
      case Evt(_:Reset) => pbuffer.clear()
    }
    mediator ! Publish("buffer_update", BufferUpdated)
  }

  override def receiveRecover: Receive = {
    case e:Evt => handleEvent(e)
    case SnapshotOffer(_, PaintBuffer(snapshot)) =>
      pbuffer = snapshot
      mediator ! Publish("buffer_update", BufferUpdated)
    case RecoveryCompleted => // do nothing
    case x => log.warning(s"BufferManager: unknown message $x in receiveRecover")
  }

  override def receiveCommand: Receive = {
    case GetBuffer => sender ! PaintBuffer(pbuffer)
    case u:ClientUpdate => persistAsync(Evt(u))(handleEvent)
    case BufferStatus => sender ! BufferState(Cluster(context.system).selfAddress, pbuffer.size)
    case "snap"  => saveSnapshot(PaintBuffer(pbuffer))
    case _:SubscribeAck => // do nothing
    case x => log.warning(s"BufferManager: unknown message $x in receiveCommand")
  }
}