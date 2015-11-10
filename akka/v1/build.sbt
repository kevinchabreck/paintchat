
name := "PaintChat v1 (Akka-http, single node)"

version := "1.0"

scalaVersion := "2.11.7"

libraryDependencies ++= {
  val akkaHttpVersion = "1.0"
  Seq(
    "com.typesafe.akka" %% "akka-stream-experimental" % akkaHttpVersion,
    "com.typesafe.akka" %% "akka-http-core-experimental" % akkaHttpVersion,
    "com.typesafe.akka" %% "akka-http-experimental" % akkaHttpVersion,
    "com.typesafe.play" %% "play-json" % "2.4.3"
  )
}
