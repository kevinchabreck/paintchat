package benchmark

import javax.inject.Inject
import play.api.mvc._
import play.api.libs.ws._
import play.api.libs.ws.ning._


import akka.actor.{Actor, ActorSystem, ActorRef, Props}
import akka.util.Timeout

import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.postfixOps
import scala.sys.exit
import scala.io.StdIn.readLine
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.Future
import scala.collection.immutable.StringOps
import collection.mutable.{ListBuffer}
import com.typesafe.config.ConfigFactory
import org.java_websocket.client.WebSocketClient
import org.java_websocket.drafts.{Draft_17}
import org.java_websocket.handshake.ServerHandshake
import java.net.URI
import java.util.Random

case class ReceivedMessage(message : String, timeStamp : Long)

object Benchmark extends App {

  implicit val system = ActorSystem("tester-system")

  val configuration = ConfigFactory.load("application.conf")
  val numberClients = configuration.getInt("app.numberClients")
  val numberTestPackets = configuration.getInt("app.numberTestPackets")
  val connectionSitePort = configuration.getString("app.connectionSitePort")
  val numDistributedClients = configuration.getInt("app.numDistributedClients")
  val distributedTest = configuration.getBoolean("app.distributedTest")
  val distributedClientsRaw = configuration.getString("app.distributedClients")
  val distributedClients : Seq[String] = distributedClientsRaw.split(",", 3)

  // map of all clients that this app controls
  val clientMap = collection.mutable.Map[Int, ActorRef]()

  // used to determine when the last packet was recieved to delay tests while traffic is going on
  var lastReceived : Long = 0

  // store the results of all the tests so that the information can be examined
  var delayArray = Array.ofDim[Double](numberClients, numberTestPackets)

  println(s"\nClients:$numberClients")
  println(s"TestPacketsPerClient:$numberTestPackets")
  println(s"ServerAddress:$connectionSitePort\n")

  createClients()

  waitOneSec()

  println(s"ClientsConnectFail:${numberClients - clientMap.size}\n")
  readLine()

  val startTime = System.currentTimeMillis

  startTests()

  waitOneSec()

  val endTime = System.currentTimeMillis

  recordResults()

  println("Total Time is " + (endTime - startTime) + "\n")

  val requestPerSec : Double = (numberTestPackets.asInstanceOf[Double] * numberClients) / ((endTime.asInstanceOf[Double] - startTime.asInstanceOf[Double] - 2000) / 1000.0)

  println(s"Request per second is ${requestPerSec}")

  println(s"ClientsConnectEnd:${clientMap.size}/$numberClients\n")

  //checkBuffer()

  readLine()

  println("Shutting down benchmark framework\n")

  // shut down all clients
  clientMap.foreach({ case (clientNum, client) =>
    client ! "close"
  })

  system.terminate()
  Await.result(system.whenTerminated, Duration.Inf)

  exit()

  def waitOneSec(){
    lastReceived = System.currentTimeMillis
    while (System.currentTimeMillis - lastReceived < 2000) {} // wait for one second to make sure system is ready to continue
  }

  def createClients() {
    // this generates random values for forced delay - currently not in use
    val randomRange = 100.0
    val base = 50.0
    val ranGenerator = new Random(8)


    (1 to numberClients).foreach({ cnt =>

      var connection : String = connectionSitePort
      if (distributedTest == true){
        val cntMod3 = (cnt % 3)
        connection = distributedClients(cntMod3)
      }

      val client = system.actorOf(Props(classOf[TestClient], cnt, (ranGenerator.nextDouble() * randomRange + base).asInstanceOf[Long], connection, numberTestPackets))
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
      if (max > grandMax) grandMax = max
      if (min < grandMin) grandMin = min

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

  def checkBuffer() {
    println(s"Consistency Checking Start: ")
    val urlList : Seq[String] = distributedClientsRaw.split(",").map({ url=>
      url.replaceAll("ws","http")
    })

    var responsebody = new ListBuffer[String]

    (1 to 3).foreach({ iter =>
      val client = NingWSClient()
      implicit val timeout = Timeout(1 seconds)
      val future = client.url(urlList(iter-1)+"/paintbuffer").get()
      val response = Await.result(future, timeout.duration)
      responsebody += response.body
      client.close()
    })

    var consistency = true

    if ((responsebody(0) == responsebody(1)) && (responsebody(1) == responsebody(2))){
      consistency = true
    } else{
      consistency = false
    }

    // (1 to 3).foreach({ iter =>
    //   println(responsebody(iter-1))
    // })

    if(consistency) {
      println(s"Data is consistent")
    } else{
      println(s"Data is inconsistent")
    }
  }

}



class TestClient(id: Int, delay: Long, connectionSitePort: String, numberTestPackets: Int) extends WebSocketClient(new URI(connectionSitePort), new Draft_17) with Actor {
  var packetNum : Int = 1

  val pixelSpacingX : Int = 7
  val pixelSpacingY : Int = 4
  val hexStringR : String = "%02X".format(if (id > 40) (255 - (id * 2)) else 0)
  val hexStringG : String = "%02X".format(-(id * id * 1 / 10) + (102 / 10 * id))
  val hexStringB : String = "%02X".format(if (id > 60) 0 else (255 - (id * 2)))
  val hexString : String = hexStringR + hexStringG + hexStringB

  override def receive = {
    case "connectBlocking" => super.connectBlocking()

    case "start" =>
      // super.send("GETBUFFER:")
      super.send("USERNAME:" + id)
      super.send("RESET:")

    case "send" =>
      super.send(s"PAINT:${id * pixelSpacingX} ${packetNum * pixelSpacingY} ${id * pixelSpacingX} ${packetNum * pixelSpacingY} 5 #${hexString} ${System.currentTimeMillis}")
      if (packetNum < numberTestPackets){
        self ! "send"
        packetNum += 1
      }

    case "close" =>
      super.close()

    case ReceivedMessage(message, timeReceived) =>
      val splitCol : Seq[String] = message.split(":", 2)
      if (splitCol(0).compareTo("PAINT") == 0){
        val senderNum : Seq[String] = splitCol(1).split(" ", 7)
        if (senderNum(0).compareTo((id * pixelSpacingX).toString()) == 0){
          // senderNum(1) is the packetNum and senderNum(6) is the timestamp
          Benchmark.delayArray(id - 1)((senderNum(1).toInt / pixelSpacingY) - 1) = timeReceived - senderNum(6).toLong
        }
      }
      Benchmark.lastReceived = timeReceived

    case x => println("I have no idea how to handle what you just sent me. " + x)
  }

  override def onMessage(message: String): Unit = {
    //Thread.sleep(delay); - no forced delay for now
    val timeReceived = System.currentTimeMillis
    self ! ReceivedMessage(message, timeReceived)
  }
  override def onClose(code: Int, reason: String, remote: Boolean): Unit = {
    println("This " + id + " is being closed!")
    Benchmark.clientMap -= id
  }
  override def onOpen(handshakedata: ServerHandshake): Unit = { }
  override def onError(ex: Exception): Unit = println("Ahh, I am client " + id + " and I am in error! " + ex)

}
