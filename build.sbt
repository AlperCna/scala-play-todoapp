name := """todo-play-app"""
organization := "com.alper.todo"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.13.18"

libraryDependencies ++= Seq(
  guice,
  jdbc,
  ws,
  evolutions,
  "com.microsoft.sqlserver" % "mssql-jdbc" % "12.8.1.jre11",
  "org.scalatestplus.play" %% "scalatestplus-play" % "7.0.2" % Test
)