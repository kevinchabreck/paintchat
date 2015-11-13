
name := "PaintChat v2 (Spray, clustered)"

version := "2.0"

scalaVersion := "2.11.7"

assemblyJarName in assembly := "paintchat-v2.jar"

scalacOptions ++= Seq(
  "-deprecation",
  "-language:postfixOps",
  "-target:jvm-1.8"
)

val akkaVersion = "2.4.0"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-cluster" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster-tools" % akkaVersion,
  "io.spray" %% "spray-can" % "1.3.3",
  "com.wandoulabs.akka" %% "spray-websocket" % "0.1.4",
  "com.typesafe.play" %% "play-json" % "2.4.3"
)
