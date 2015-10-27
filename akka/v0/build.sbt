
name := "PaintChat (Spray implementation)"

version := "1.0"

scalaVersion := "2.11.7"

scalacOptions ++= Seq(
  "-language:postfixOps"
)

libraryDependencies ++= Seq(
  "io.spray" %% "spray-can" % "1.3.3",
  "io.spray" %%  "spray-json" % "1.3.1",
  "com.wandoulabs.akka" %% "spray-websocket" % "0.1.4"
)
