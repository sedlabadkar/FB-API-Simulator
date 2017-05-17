name := "Project4"
 
version := "1.0"

scalaVersion := "2.11.7"

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"

fork in run := true 

libraryDependencies ++= {
  val akkaVersion = "2.3.6"
  Seq(
   "io.spray" % "spray-routing_2.11" % "1.3.3",
  "io.spray" % "spray-can_2.11" % "1.3.3",
  "io.spray" %  "spray-json_2.11" % "1.3.2",
 "io.spray" %  "spray-client_2.11" % "1.3.2",
  "com.typesafe.akka" %% "akka-actor" % "2.3.6",
  "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
  "com.typesafe.akka" %% "akka-remote" % "2.3.6",
  "com.typesafe" % "config" % "1.3.0",
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion % "test",
  "ch.qos.logback" % "logback-classic" % "1.0.12"
 )}

libraryDependencies <++= scalaVersion(v =>
  Seq("org.scala-lang" % "scala-actors" % v)
)
