name := "AkkaTrainingApril2020"
version := "1.0"
scalaVersion := "2.13.2"

lazy val akkaVersion = "2.6.4"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion % Test,
  "org.scalatest" %% "scalatest" % "3.1.1" % Test,

  // used by Akka Persistence exercise
  "com.typesafe.akka" %% "akka-persistence-typed" % akkaVersion,
  "com.typesafe.akka" %% "akka-serialization-jackson" % akkaVersion,

  // used by CQRS exercise
  "com.typesafe.akka" %% "akka-serialization-jackson" % akkaVersion,
  "com.typesafe.akka" %% "akka-persistence-query" % akkaVersion,
  "com.github.dnvriend" %% "akka-persistence-jdbc" % "3.5.2",       // Akka persistence saves to mySql
  "mysql" % "mysql-connector-java" % "8.0.19" % Test,               // Akka persistence saves to mySql
  "com.lightbend.akka" %% "akka-stream-alpakka-slick" % "2.0.0-RC2" // Akka-stream Flow to Slick Sink (mySql)
)

