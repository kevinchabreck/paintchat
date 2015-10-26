package benchmarks

import org.java_websocket.client.WebSocketClient
import org.java_websocket.drafts.{Draft_17}
import org.java_websocket.handshake.ServerHandshake
import java.net.URI

object Benchmarks extends App {

  class TestClient(id: Int, delay: Long) extends WebSocketClient(new URI(s"ws://localhost:8080/"), new Draft_17){
    override def onMessage(message: String): Unit = {
      Thread.sleep(delay);
      println("Delay is " + delay)
    }
    override def onClose(code: Int, reason: String, remote: Boolean): Unit = println("This is being closed!")
    override def onOpen(handshakedata: ServerHandshake): Unit = println(s"There is a websocket opened and the delay is $delay")
    override def onError(ex: Exception): Unit = println("Ahh, I am in error! " + ex)
  }

  def doMain() {
    val clientMap = collection.mutable.Map[TestClient, Int]()

    val numberClients = 10
    val randomRange = 100
    val base = 50
    (1 to numberClients).foreach({ cnt =>
      val client = new TestClient(cnt, Math.round(Math.random() * randomRange + base))
      Thread.sleep(10)
      println("I am here " + cnt)
      client.connectBlocking()
      client.send("GETBUFFER:")
      client.send("USERNAME:" + cnt)
      clientMap(client) = cnt
    })

    readLine("Hit ENTER to exit ...\n")
    println("Shutting down benchmark framework")
    
    clientMap.foreach({ case (client, clientNum) =>
      client.close()
    })
  }

  doMain()
}
