package paintchat

import akka.actor.{Actor, ActorLogging}
import akka.cluster.{Cluster, ClusterEvent, Member}
import akka.cluster.ClusterEvent.{InitialStateAsEvents, MemberUp, MemberExited, MemberRemoved, UnreachableMember, ReachableMember, MemberEvent}
import collection.mutable.{ListBuffer}

sealed trait BufferUpdate
case class BroadcastBuffer(pbuffer: ListBuffer[String]) extends BufferUpdate

class ClusterListener extends Actor with ActorLogging {
  var initialized = false
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
    case ClusterEvent.MemberUp(member) => handleNewMember(member)
    case ClusterEvent.UnreachableMember(member) => println(s"Member unreachable: ${member}")
    case ClusterEvent.MemberRemoved(member, previousStatus) => println(s"Member removed: ${member.address} after ${previousStatus}")
    case event: ClusterEvent.MemberEvent => println(s"recieved ClusterEvent.MemberEvent: $event")
    case state: ClusterEvent.CurrentClusterState => println(s"recieved CurrentClusterState: $state")

    case PaintBuffer(pbuffer) => 
      println(s"recieved paintbuffer from leader")
      context.system.actorSelection(cluster.selfAddress + "/user/paintchat-server") ! BroadcastBuffer(pbuffer)
  }

  def handleNewMember(member:Member) = {
    println(s"Member up: ${member.address}")
    if (!initialized && (cluster.selfAddress != cluster.state.leader.get)) {
      // request buffer from leader's ServerWorker, and set initialized=true
      // send UpdateBuffer to local ServerWorker
      context.system.actorSelection(cluster.state.leader.get + "/user/paintchat-server") ! GetBuffer
      initialized = true
    }
  }
}
