
name := "paintchat-benchmarks"

version := "1.0"

scalaVersion := "2.11.7"

scalacOptions ++= Seq(
  "-deprecation",
  "-feature",
  "-target:jvm-1.8"
)

libraryDependencies ++= Seq(
  "com.typesafe" % "config" % "1.3.0",
  "org.java-websocket" % "Java-WebSocket" % "1.3.0"
)
