package benchmarks

import akka.actor.{Actor, ActorSystem, ActorRef, Props}
import scala.sys.exit
import scala.io.StdIn.readLine
import scala.concurrent.Await
import scala.concurrent.duration._
import com.typesafe.config.ConfigFactory
import org.java_websocket.client.WebSocketClient
import org.java_websocket.drafts.{Draft_17}
import org.java_websocket.handshake.ServerHandshake
import java.net.URI
import java.util.Random

case class ReceivedMessage(message : String, timeStamp : Long)

object Benchmarks extends App {
  implicit val system = ActorSystem("tester-system")

  val configuration = ConfigFactory.load("application.conf")
  val numberClients = configuration.getInt("app.numberClients")
  val numberTestPackets = configuration.getInt("app.numberTestPackets")
  val connectionSitePort = configuration.getString("app.connectionSitePort")

  // map of all clients that this app controls
  val clientMap = collection.mutable.Map[Int, ActorRef]()

  // used to determine when the last packet was recieved to delay tests while traffic is going on
  var lastReceived : Long = 0

  // store the results of all the tests so that the information can be examined
  var delayArray = Array.ofDim[Double](numberClients, numberTestPackets)

  println(s"\nClients:$numberClients")
  println(s"TestPacketsPerClient:$numberTestPackets")
  println(s"ServerAddress:$connectionSitePort")

  createClients()

  waitOneSec()

  println(s"\nClientsConnectFail:${numberClients - clientMap.size}\n")

  startTests()

  waitOneSec()

  recordResults()

  println("\nShutting down benchmark framework\n")

  // shut down all clients
  clientMap.foreach({ case (clientNum, client) =>
    client ! "close"
  })

  system.terminate()
  Await.result(system.whenTerminated, Duration.Inf)

  exit()

  def waitOneSec(){
    lastReceived = System.currentTimeMillis
    while (System.currentTimeMillis - lastReceived < 1000) {} // wait for one second to make sure system is ready to continue
  }

  def createClients() {
    // this generates random values for forced delay - currently not in use
    val randomRange = 100.0
    val base = 50.0
    val ranGenerator = new Random(8)

    (1 to numberClients).foreach({ cnt =>
      val client = system.actorOf(Props(classOf[TestClient], cnt, (ranGenerator.nextDouble() * randomRange + base).asInstanceOf[Long], connectionSitePort, numberTestPackets))
      client ! "connectBlocking"
      client ! "start"
      clientMap(cnt) = client
    })
  }

  def startTests() {
    // Test one: Basic Test - Each client sends certain number of packets and measures the average delay
    clientMap.foreach({ case (clientNum, client) =>
      client ! "send"
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
      val clientName : String = "c" + clientNum
      println(s"\n$clientName.avgDelay:${total/packetsReceived}")
      println(s"$clientName.totDelay:${total}")
      println(s"$clientName.minDelay:${min}")
      println(s"$clientName.maxDelay:${max}")
      println(s"$clientName.sent:${numberTestPackets}")
      println(s"$clientName.rec:${packetsReceived}")
    })

    val clientName : String = "sum"
    println(s"\n$clientName.avgDelay:${grandTotal / numberClients}")
    println(s"$clientName.minDelay:${grandMin}")
    println(s"$clientName.maxDelay:${grandMax}")
    println(s"$clientName.sent:${numberTestPackets * numberClients}")
    println(s"$clientName.rec:${numberTestPackets * numberClients - grandPacketsDropped}")

  }
}

class TestClient(id: Int, delay: Long, connectionSitePort: String, numberTestPackets: Int) extends WebSocketClient(new URI(connectionSitePort), new Draft_17) with Actor {
  var packetNum : Int = 1

  override def receive = {
    case "connectBlocking" => super.connectBlocking()

    case "start" =>
      super.send("GETBUFFER:")
      super.send("USERNAME:" + id)
      super.send("RESET:")

    case "send" =>
      super.send(s"PAINT:$id $packetNum ${System.currentTimeMillis} ${packetNum + 1} 5 #ffff00")
      if (packetNum < numberTestPackets){
        self ! "send"
        packetNum += 1
      }

    case "close" => super.close()

    case ReceivedMessage(message, timeReceived) =>
      var splitCol : Seq[String] = message.split(":", 2)
      if (splitCol(0).compareTo("PAINT") == 0){
        var senderNum : Seq[String] = splitCol(1).split(" ", 4)
        if (senderNum(0).compareTo(id.toString()) == 0){
          // senderNum(1) is the packetNum and senderNum(2) is the timestamp
          Benchmarks.delayArray(id - 1)(senderNum(1).toInt - 1) = timeReceived - senderNum(2).toLong
        }
      }
      Benchmarks.lastReceived = timeReceived

    case x => println("I have no idea how to handle what you just what you just sent me. " + x)
  }

  override def onMessage(message: String): Unit = {
    //Thread.sleep(delay); - no forced delay for now
    val timeReceived = System.currentTimeMillis
    self ! ReceivedMessage(message, timeReceived)
  }
  override def onClose(code: Int, reason: String, remote: Boolean): Unit = {
    println("This " + id + " is being closed!")
    Benchmarks.clientMap -= id
  }
  override def onOpen(handshakedata: ServerHandshake): Unit = { }
  override def onError(ex: Exception): Unit = println("Ahh, I am client " + id + " and I am in error! " + ex)

}
