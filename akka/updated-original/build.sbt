
name := "draw.ws server"

version := "1.0"

scalaVersion := "2.11.7"

libraryDependencies ++= {
  val akkaHttpVersion = "1.0-RC4"
  Seq(
    "io.spray" %% "spray-can" % "1.3.3",
    "com.wandoulabs.akka" %% "spray-websocket" % "0.1.4",
    "io.spray" %%  "spray-json" % "1.3.1",
    "com.typesafe.akka" %% "akka-stream-experimental" % akkaHttpVersion,
    "com.typesafe.akka" %% "akka-http-core-experimental" % akkaHttpVersion,
    "com.typesafe.akka" %% "akka-http-experimental" % akkaHttpVersion
  )
}
