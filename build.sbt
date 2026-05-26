name := """todo-play-app"""
organization := "com.alper.todo"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.13.18"

val pac4jPlayVersion = "12.0.0-PLAY2.9"
val pac4jVersion     = "6.0.6"

libraryDependencies ++= Seq(
  guice,
  jdbc,
  ws,
  evolutions,
  "org.apache.kafka"        %  "kafka-clients"        % "3.7.1",
  "com.microsoft.sqlserver" %  "mssql-jdbc"          % "12.8.1.jre11",
  "org.apache.commons"      %  "commons-email"        % "1.5",
  "org.pac4j"               %% "play-pac4j"           % pac4jPlayVersion,
  "org.pac4j"               %  "pac4j-core"           % pac4jVersion,
  "org.pac4j"               %  "pac4j-http"           % pac4jVersion,
  "org.apache.shiro"        %  "shiro-crypto-cipher"  % "1.13.0",
  "org.scalatestplus.play"  %% "scalatestplus-play"   % "7.0.2" % Test
)

// pac4j'nin çektiği Jackson 2.17.x, Akka ile çakışıyor — Play 2.9'un beklediği versiyona sabitle
dependencyOverrides ++= Seq(
  "com.fasterxml.jackson.core"   % "jackson-core"        % "2.14.3",
  "com.fasterxml.jackson.core"   % "jackson-databind"    % "2.14.3",
  "com.fasterxml.jackson.core"   % "jackson-annotations" % "2.14.3",
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.14.3"
)
