
name := "PaintChat"

version := "2.0"

scalaVersion := "2.11.7"

scalacOptions ++= Seq(
  "-deprecation",
  "-language:postfixOps",
  "-target:jvm-1.8"
)

resolvers += "krasserm at bintray" at "http://dl.bintray.com/krasserm/maven"

val akkaVersion = "2.4.1"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-cluster" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster-tools" % akkaVersion,
  "com.typesafe.akka" % "akka-cluster-metrics_2.11" % akkaVersion,
  "com.typesafe.akka" %% "akka-persistence" % akkaVersion,
  "com.github.krasserm" %% "akka-persistence-cassandra3" % "0.5",
  "org.slf4j" % "slf4j-simple" % "1.6.4",
  "io.spray" %% "spray-can" % "1.3.3",
  "com.wandoulabs.akka" %% "spray-websocket" % "0.1.4",
  "com.typesafe.play" %% "play-json" % "2.4.4"
)
