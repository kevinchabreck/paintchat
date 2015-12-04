package paintchat

import akka.actor.{Actor, ActorLogging}
import akka.cluster.{Cluster, ClusterEvent}

class ClusterListener extends Actor with ActorLogging {
  val cluster = Cluster(context.system)

  override def preStart(): Unit = {
    cluster.subscribe(self,
      initialStateMode=ClusterEvent.InitialStateAsEvents,
      classOf[ClusterEvent.MemberEvent]
    )
  }

  override def postStop(): Unit = cluster.unsubscribe(self)

  def receive = {
    case e:ClusterEvent.MemberEvent => log.info(s"MemberEvent: $e")
  }
}
