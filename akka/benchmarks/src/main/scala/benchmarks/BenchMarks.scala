package benchmarks

import scala.sys.exit
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

  // map of all clients that this app controls
  val clientMap = collection.mutable.Map[Int, TestClient]()

  // this generates random values for forced delay - currently not in use
  val randomRange = 100.0
  val base = 50.0
  val ranGenerator = new Random(8)

  // used to determine when the last packet was recieved to delay tests while traffic is going on
  var lastReceived : Long = 0
  
  // store the results of all the tests so that the information can be examined
  var delayArray = Array.ofDim[Double](numberClients, numberTestPackets)

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
    override def onClose(code: Int, reason: String, remote: Boolean): Unit = {
      println("This " + id + " is being closed!")
      clientMap -= id
    }
    override def onOpen(handshakedata: ServerHandshake): Unit = println(s"There is a websocket opened with id $id and the delay is none for now")
    override def onError(ex: Exception): Unit = println("Ahh, I am client " + id + " and I am in error! " + ex)
  }

  def waitOneSec(){
    lastReceived = System.currentTimeMillis
    while (System.currentTimeMillis - lastReceived < 1000) {} // wait for one second to make sure system is ready to continue
  }

  def createClients() {
    (1 to numberClients).foreach({ cnt =>
      val client = new TestClient(cnt, (ranGenerator.nextDouble() * randomRange + base).asInstanceOf[Long])
      client.connectBlocking()
      client.send("GETBUFFER:")
      client.send("USERNAME:" + cnt)
      clientMap(cnt) = client
    })
  }

  def startTests() {
    // Test one: Basic Test - Each client sends certain number of packets and measures the average delay
    (1 to numberTestPackets).foreach({ packetNum =>
      clientMap.foreach({ case (clientNum, client) =>
        client.send(s"PAINT:$clientNum $packetNum ${System.currentTimeMillis} ${packetNum + 1} 5 #ffff00")
      })
    })

  }

  def recordResults() {
    var grandTotal = 0.0
    var grandMax = 0.0
    var grandMin = Double.MaxValue
    var grandPacketsDropped = 0
    (1 to numberClients).foreach({ clientNum =>
      var total = 0.0
      var packetsReceived = 0
      var max = 0.0
      var min = Double.MaxValue
      delayArray(clientNum - 1).foreach({ cnt =>
        if ( cnt > 1e-3 ){
          if (cnt > max) 
            max = cnt
          if (cnt < min)
            min = cnt
          total += cnt
          packetsReceived = packetsReceived + 1
        }
      })
      // add up totals
      if (packetsReceived > 0){
        grandTotal += (total / packetsReceived)
      }
      else{
        max = Double.MaxValue
      }

      // calc grand max and min
      if (max > grandMax)
        grandMax = max
      if (min < grandMin)
        grandMin = min

      // calc total packets lost
      grandPacketsDropped += (numberTestPackets - packetsReceived)

      // print initial results
      print(s"The average delay for client $clientNum is ${total/packetsReceived} and the total is $total. Max delay is $max and min is $min. ")
      println(s"For client $clientNum, $packetsReceived packets were received out of $numberTestPackets")
    })
    println(s"\nThe average delay for all clients is ${grandTotal / numberClients}. Max delay is $grandMax and min is $grandMin. $grandPacketsDropped total packets were dropped") 

  }

  createClients()

  waitOneSec()
  readLine("Press ENTER to begin test...\n")

  startTests()

  waitOneSec()

  recordResults()

  readLine("Hit ENTER to exit ...\n")
  println("Shutting down benchmark framework")

  // shut down all clients
  clientMap.foreach({ case (clientNum, client) =>
    client.close()
  })

  waitOneSec()

  exit()
}
