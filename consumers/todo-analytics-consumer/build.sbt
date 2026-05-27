ThisBuild / scalaVersion := "2.13.16"

name := "todo-analytics-consumer"

Compile / unmanagedResourceDirectories += baseDirectory.value / "conf"

libraryDependencies ++= Seq(
  "org.apache.kafka" % "kafka-clients" % "3.8.1",
  "com.typesafe.play" %% "play-json" % "2.10.7",
  "com.typesafe" % "config" % "1.4.3",
  "com.microsoft.sqlserver" % "mssql-jdbc" % "12.8.1.jre11",
  "org.scalatest" %% "scalatest" % "3.2.19" % Test
)
