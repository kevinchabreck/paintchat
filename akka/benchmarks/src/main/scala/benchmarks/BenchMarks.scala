package benchmarks

import com.typesafe.config.ConfigFactory
import org.java_websocket.client.WebSocketClient
import org.java_websocket.drafts.{Draft_17}
import org.java_websocket.handshake.ServerHandshake
import java.net.URI
import java.util.Random

object Benchmarks extends App {

  val configuration = ConfigFactory.load("application.conf")
  val numberClients = configuration.getInt("app.numberClients")
  val numberTestPackets = configuration.getInt("app.numberTestPackets")
  val connectionSitePort = configuration.getString("app.connectionSitePort")

  // used to determine when the last packet was recieved to delay tests while traffic is going on
  var lastReceived : Long = 0
  
  // store the results of all the tests so that the information can be examined
  var delayArray = Array.ofDim[Long](numberClients, numberTestPackets)

  class TestClient(id: Int, delay: Long) extends WebSocketClient(new URI(connectionSitePort), new Draft_17){
    override def onMessage(message: String): Unit = {
      //Thread.sleep(delay); - no forced delay for now
      val timeReceived = System.currentTimeMillis
      var splitCol : Seq[String] = message.split(":", 2)
      if (splitCol(0).compareTo("PAINT") == 0){
        var senderNum : Seq[String] = splitCol(1).split(" ", 4)
        if (senderNum(0).compareTo(id.toString()) == 0){
          // senderNum(1) is the packetNum and senderNum(2) is the timestamp
          delayArray(id - 1)(senderNum(1).toInt - 1) = timeReceived - senderNum(2).toLong
        }
      }
      lastReceived = timeReceived
    }
    override def onClose(code: Int, reason: String, remote: Boolean): Unit = println("This is being closed!")
    override def onOpen(handshakedata: ServerHandshake): Unit = println(s"There is a websocket opened with id $id and the delay is none for now")
    override def onError(ex: Exception): Unit = println("Ahh, I am in error! " + ex)
  }

  def waitOneSec(){
    lastReceived = System.currentTimeMillis
    while (System.currentTimeMillis - lastReceived < 1000) {} // wait for one second to make sure system is ready to continue
  }

  def doMain() {
    val clientMap = collection.mutable.Map[TestClient, Int]()

    val randomRange = 100.0
    val base = 50.0

    // set pseudo random number seed so that the random number is deterministic between tests for the case of forced delay
    val ranGenerator = new Random(8)

    (1 to numberClients).foreach({ cnt =>
      val client = new TestClient(cnt, (ranGenerator.nextDouble() * randomRange + base).asInstanceOf[Long])
      client.connectBlocking()
      client.send("GETBUFFER:")
      client.send("USERNAME:" + cnt)
      clientMap(client) = cnt
    })

    waitOneSec()
    readLine("Press ENTER to begin test...\n")

    // Test one: Basic Test - Each client sends certain number of packets and measures the average delay
    (1 to numberTestPackets).foreach({ packetNum =>
      clientMap.foreach({ case (client, clientNum) =>
        client.send(s"PAINT:$clientNum $packetNum ${System.currentTimeMillis} ${packetNum + 1} 5 #ffff00")
      })
    })

    waitOneSec()
    var grandTotal = 0.0
    (1 to numberClients).foreach({ clientNum =>
      var total = 0.0
      delayArray(clientNum - 1).foreach({ cnt =>
        total += cnt.asInstanceOf[Double]
      })
      grandTotal += (total / numberTestPackets)
      println(s"The average delay for client $clientNum is ${total / numberTestPackets} and the total is $total")
    })
    println(s"The average delay for all clients is ${grandTotal / numberClients}") 

    readLine("Hit ENTER to exit ...\n")
    println("Shutting down benchmark framework")
    
    clientMap.foreach({ case (client, clientNum) =>
      client.close()
    })
  }

  doMain()
}
