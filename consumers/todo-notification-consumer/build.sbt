name := "todo-notification-consumer"

organization := "com.alper.todo"

version := "0.1.0-SNAPSHOT"

scalaVersion := "2.13.18"

libraryDependencies ++= Seq(
  "org.apache.kafka"   %  "kafka-clients" % "3.7.1",
  "com.typesafe"       %  "config"        % "1.4.3",
  "com.typesafe.play" %% "play-json" % "2.10.5",
  "org.scalatest"     %% "scalatest" % "3.2.19" % Test
)

Compile / unmanagedResourceDirectories += baseDirectory.value / "conf"
Test / unmanagedSourceDirectories += baseDirectory.value / "test"
