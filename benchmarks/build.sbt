
name := "paintchat-benchmarks"

version := "1.0"

scalaVersion := "2.11.7"

scalacOptions ++= Seq(
  "-deprecation",
  "-feature",
  "-target:jvm-1.8"
)

libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play-json" % "2.4.4",
  "com.typesafe.play" %% "play-ws" % "2.4.4",
  "com.typesafe.akka" %% "akka-actor" % "2.4.1",
  "com.typesafe" % "config" % "1.3.0",
  "org.java-websocket" % "Java-WebSocket" % "1.3.0"
)

libraryDependencies ~= { _ map {
  case m if m.organization == "com.typesafe.play" =>
    m.exclude("commons-logging", "commons-logging").
      exclude("com.typesafe.play", "sbt-link")
  case m => m
}}
