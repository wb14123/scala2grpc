import sbt.Keys.{libraryDependencies, organization}

name := "scala2grpc"

version := "0.1.0-SNAPSHOT"
organization := "me.binwang"

lazy val scala212 = "2.12.13"
lazy val scala213 = "2.13.1"

ThisBuild / scalaVersion := scala212

resolvers += Resolver.bintrayRepo("streamz", "maven") // for streamz

val akkaVersion = "2.6.9"

val supportedScalaVersions = List(scala212, scala213)

lazy val root = (project in file("."))
  .aggregate(generator, plugin)
  .settings(
    crossScalaVersions := Nil
  )

lazy val generator = (project in file("generator"))
  .withId("scala2grpc-generator")
  .enablePlugins(AkkaGrpcPlugin)
  .settings(
    organization := "me.binwang",
    crossScalaVersions := supportedScalaVersions,
    libraryDependencies ++= Seq(
      "co.fs2" %% "fs2-core" % "2.5.10",

      // akka
      "com.github.krasserm" %% "streamz-converter" % "0.13-RC4",
      "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
      "com.typesafe.akka" %% "akka-stream" % akkaVersion,
      "com.typesafe.akka" %% "akka-http" % "10.2.7",

      // log
      "ch.qos.logback" % "logback-classic" % "1.2.3",
      "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2",
    )
  )

lazy val plugin = (project in file("plugin"))
  .withId("scala2grpc-plugin")
  .enablePlugins(SbtPlugin)
  .settings(
    organization := "me.binwang",
    name := "scala2grpc-plugin",
    scalaVersion := scala212
  )