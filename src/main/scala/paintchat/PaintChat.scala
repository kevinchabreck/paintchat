package paintchat

import akka.actor.{ActorRef, ActorSystem, PoisonPill, Props}
import akka.io.IO
import akka.util.Timeout
import akka.pattern.ask
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings}
import com.typesafe.config.ConfigFactory
import spray.can.Http
import spray.can.server.UHttp
import scala.concurrent.Await
import scala.concurrent.duration._

object PaintChat extends App with MySslConfiguration {

  val config = ConfigFactory.load()

  // join cluster
  implicit val system = ActorSystem("ClusterSystem")
  system.actorOf(Props(classOf[ClusterListener]), "paintchat-cluster")
  system.actorOf(ClusterSingletonManager.props(singletonProps = Props(classOf[BufferManager]),
                                               terminationMessage = PoisonPill,
                                               settings = ClusterSingletonManagerSettings(system)),
                 name = "buffer-manager")

  // bind to http port
  implicit val timeout = Timeout(5 seconds)
  val bind_future = IO(UHttp) ? Http.Bind(system.actorOf(Props(classOf[ServerWorker]), "paintchat-server"), config.getString("app.interface"), config.getInt("app.port"))
  Await.result(bind_future, timeout.duration) match {
    case Http.Bound(x) => println(Console.GREEN+s"server listening on http:/$x"+Console.RESET)
    case _: Http.CommandFailed => println(Console.RED+"Error: http bind failed"+Console.RESET)
  }

  Await.result(system.whenTerminated, Duration.Inf)
}
