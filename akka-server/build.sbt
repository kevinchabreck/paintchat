
name := "draw.ws server"
version := "1.0"
scalaVersion := "2.11.6"

resolvers ++= Seq(
	"Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/",
	"spray repo" at "http://repo.spray.io"
)

libraryDependencies ++= Seq(
  	"com.typesafe.akka" % "akka-actor_2.11" % "2.3.9",
	"io.spray" %% "spray-can" % "1.3.3",
	"com.wandoulabs.akka" %% "spray-websocket" % "0.1.4",
	"io.spray" %%  "spray-json" % "1.3.1"
)
