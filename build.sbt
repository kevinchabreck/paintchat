
name := "PaintChat"

version := "2.0"

scalaVersion := "2.12.4"

scalacOptions ++= Seq(
  "-deprecation",
  "-language:postfixOps",
  "-target:jvm-1.8"
)

resolvers += "krasserm at bintray" at "http://dl.bintray.com/krasserm/maven"

val akkaVersion = "2.5.9"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-cluster" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster-tools" % akkaVersion,
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "com.typesafe.akka" %% "akka-persistence" % akkaVersion,
  "com.typesafe.akka" %% "akka-persistence-cassandra" % "0.80",
  "com.typesafe.akka" %% "akka-http"   % "10.1.0-RC1",
  "com.typesafe.play" %% "play-json" % "2.6.8",
  "com.lightbend.akka.management" %% "akka-management-cluster-bootstrap" % "0.10.0",
  "com.lightbend.akka.discovery" %% "akka-discovery-dns" % "0.10.0",
  "org.apache.commons" % "commons-lang3" % "3.7",
  "org.slf4j" % "slf4j-simple" % "1.7.25"
)
