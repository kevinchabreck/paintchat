lazy val root = (project in file(".")).
  settings(
    name := "draw.ws server",
    version := "1.0",
    scalaVersion := "2.11.6",
    resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/",
    resolvers += "spray repo" at "http://repo.spray.io",
    libraryDependencies += "com.typesafe.akka" % "akka-actor_2.11" % "2.3.9",
    libraryDependencies += "io.spray" %% "spray-can" % "1.3.3",
    libraryDependencies += "com.wandoulabs.akka" %% "spray-websocket" % "0.1.4",
    libraryDependencies += "io.spray" %%  "spray-json" % "1.3.1"
  )