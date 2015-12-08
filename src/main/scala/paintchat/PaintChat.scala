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
  val default_interface = config.getString("app.interface")
  val default_http_port = config.getInt("app.port")
  val default_tcp_port  = config.getInt("akka.remote.netty.tcp.port")
  println("starting app with seeds: "+config.getList("akka.cluster.seed-nodes"))

  def bindTCPPort(port:Int): ActorSystem = {
    try {
      ActorSystem("ClusterSystem", ConfigFactory.parseString("akka.remote.netty.tcp.port="+port).withFallback(ConfigFactory.load()))
    } catch {
      case scala.util.control.NonFatal(_) =>
        if (port==default_tcp_port)
          bindTCPPort(0)
        else
          ActorSystem("ClusterSystem")
    }
  }

  def bindHTTPPort(server:ActorRef, port:Int): Unit = {
    implicit val timeout = Timeout(5 seconds)
    val bind_future = ask(IO(UHttp), Http.Bind(server, default_interface, port))
    val bind_result = Await.result(bind_future, timeout.duration)
    bind_result match {
      case Http.Bound(x) =>
        scala.io.StdIn.readLine(Console.GREEN+s"server listening on http:/$x (press ENTER to exit)\n"+Console.RESET)
      case x: Http.CommandFailed =>
        if (port < default_http_port+3)
          bindHTTPPort(server, port+1)
        else
          println(Console.RED+"Error: http bind failed"+Console.RESET)
    }
  }

  implicit val system = bindTCPPort(default_tcp_port)
  system.actorOf(Props(classOf[ClusterListener]), "paintchat-cluster")
  system.actorOf(ClusterSingletonManager.props(
    singletonProps = Props(classOf[BufferManager]),
    terminationMessage = PoisonPill,
    settings = ClusterSingletonManagerSettings(system)),
    name = "buffer-manager")
  bindHTTPPort(system.actorOf(Props(classOf[ServerWorker]), "paintchat-server"), default_http_port)
  system.terminate()
  Await.result(system.whenTerminated, Duration.Inf)
}
