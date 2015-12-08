
name := "PaintChat v1 (Akka-http, single node)"

version := "1.0"

scalaVersion := "2.11.7"

scalacOptions ++= Seq(
  "-deprecation",
  "-feature",
  "-language:postfixOps",
  "-target:jvm-1.8"
)

val akkaVersion = "2.4.0"
// val akkaHttpVersion = "2.0-M1"
val akkaHttpVersion = "1.0"
val playVersion = "2.4.3"

libraryDependencies ++= {
  Seq(
  	"com.typesafe.akka" %% "akka-cluster" % akkaVersion,
  	"com.typesafe.akka" %% "akka-cluster-tools" % akkaVersion,
    "com.typesafe.akka" %% "akka-stream-experimental" % akkaHttpVersion,
    "com.typesafe.akka" %% "akka-http-core-experimental" % akkaHttpVersion,
    "com.typesafe.akka" %% "akka-http-experimental" % akkaHttpVersion,
    "com.typesafe.play" %% "play-json" % playVersion
  )
}
