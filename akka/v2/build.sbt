
name := "PaintChat v2 (Spray, clustered)"

version := "1.0"

scalaVersion := "2.11.7"

scalacOptions ++= Seq(
  "-deprecation",
  "-language:postfixOps",
  "-target:jvm-1.8"
)

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-cluster" % "2.4.0",
  "io.spray" %% "spray-can" % "1.3.3",
  "io.spray" %%  "spray-json" % "1.3.1",
  "com.wandoulabs.akka" %% "spray-websocket" % "0.1.4",
  "com.typesafe.play" %% "play-json" % "2.4.3"
)
