name := "todo-notification-consumer"

organization := "com.alper.todo"

version := "0.1.0-SNAPSHOT"

scalaVersion := "2.13.18"

libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play-json" % "2.10.5",
  "org.scalatest"     %% "scalatest" % "3.2.19" % Test
)

Test / unmanagedSourceDirectories += baseDirectory.value / "test"
