package paintchat

import akka.actor.{ActorSystem, Props}
import akka.io.IO
import akka.util.Timeout
import akka.pattern.ask
import com.typesafe.config.ConfigFactory
import spray.can.Http
import spray.can.server.UHttp
import scala.concurrent.Await
import scala.concurrent.duration._

object PaintChat extends App with MySslConfiguration {

  val config = ConfigFactory.load()
  val interface = config.getString("app.interface")
  val default_http_port = config.getInt("app.port")
  val default_tcp_port = config.getInt("akka.remote.netty.tcp.port")

  // helper method for local cluster testing
  def bindTCPPort(port: Int): ActorSystem = {
    try {
      val config = ConfigFactory.parseString("akka.remote.netty.tcp.port=" + port).withFallback(ConfigFactory.load())
      return ActorSystem("ClusterSystem", config)
    } catch {
      case _ : Throwable =>
        if (port == default_tcp_port) {
          return bindTCPPort(0)
        } else {
          println(s"Error: TCP port bind failed")
          return ActorSystem("ClusterSystem")
        }
    }
  }

  // helper method for local cluster testing
  def bindHTTPPort(port: Int): Unit = {
    implicit val timeout = Timeout(1 seconds)
    val bind_future = ask(IO(UHttp), Http.Bind(server, interface, port))
    val bind_result = Await.result(bind_future, timeout.duration)
    bind_result match {
      case Http.Bound(x) => scala.io.StdIn.readLine(s"server listening on http:/$x (press ENTER to exit)\n")
      case x: Http.CommandFailed =>
        if (port < default_http_port+3) {
          bindHTTPPort(port+1)
        } else {
          println(s"Error: unable to bind to a port")
        }
    }
  }

  implicit val system = bindTCPPort(default_tcp_port)
  val cluster = system.actorOf(Props(classOf[ClusterListener]), "paintchat-cluster")
  val server = system.actorOf(Props(classOf[ServerWorker]), "paintchat-server")
  bindHTTPPort(default_http_port)
  println("shutting down server")
  system.terminate()
  Await.result(system.whenTerminated, Duration.Inf)

  // implicit val system = ActorSystem("paintchat-system")
  // val config = system.settings.config
  // val server = system.actorOf(Props(classOf[Server]), "paintchat-server")
  // val interface = config.getString("app.interface")
  // val defaultport = config.getInt("app.port")
  // implicit val timeout = Timeout(1 seconds)
  // val bind_future = ask(IO(UHttp), Http.Bind(server, interface, port))
  // val bind_result = Await.result(bind_future, timeout.duration)
  // bind_result match {
  //   // case Http.Bound(x) => scala.io.StdIn.readLine(s"server listening on $x (press ENTER to exit)\n")
  //   case Http.Bound(x) => scala.io.StdIn.readLine(s"server listening on http:/$x (press ENTER to exit)\n")
  //   case x: Http.CommandFailed => println(s"Error: Failed to bind to $interface:$port: $x")
  // }
  // println("shutting down server")
  // system.terminate()
  // Await.result(system.whenTerminated, Duration.Inf)
}
