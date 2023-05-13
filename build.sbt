import sbt.Keys.{libraryDependencies, organization, publishTo}
import sbt.url
import xerial.sbt.Sonatype.autoImport.sonatypePublishToBundle

lazy val scala212 = "2.12.17"
lazy val scala213 = "2.13.10"

ThisBuild / version := "0.1.1-SNAPSHOT"
ThisBuild / organization := "me.binwang.scala2grpc"
ThisBuild / scalaVersion := scala212
ThisBuild / publishTo := sonatypePublishToBundle.value
ThisBuild / sonatypeCredentialHost := "s01.oss.sonatype.org"
ThisBuild / sonatypeRepository := "https://s01.oss.sonatype.org/service/local"
ThisBuild / licenses := Seq("AGPL" -> url("https://github.com/wb14123/scala2grpc/blob/master/LICENSE"))
ThisBuild / homepage := Some(url("https://github.com/wb14123/scala2grpc"))
ThisBuild / scmInfo := Some(ScmInfo(
  url("https://github.com/wb14123/scala2grpc"),
  "scm:https://github.com/wb14123/scala2grpc.git"
))
ThisBuild / developers := List(
  Developer(id="wb14123", name="Bin Wang", email="bin.wang@mail.binwang.me", url=url("https://www.binwang.me"))
)

ThisBuild / dependencyCheckAssemblyAnalyzerEnabled := Option(false)
// ThisBuild / dependencyCheckFailBuildOnCVSS := 4

val akkaVersion = "2.8.0"

val supportedScalaVersions = List(scala212, scala213)

lazy val root = (project in file("."))
  .aggregate(generator, plugin)
  .settings(
    crossScalaVersions := Nil
  )

lazy val generator = (project in file("generator"))
  .enablePlugins(AkkaGrpcPlugin)
  .settings(
    name := "generator",
    crossScalaVersions := supportedScalaVersions,
    libraryDependencies ++= Seq(
      "co.fs2" %% "fs2-core" % "2.5.10",

      // akka
      "me.binwang.streamz" %% "streamz-converter" % "0.13-RC4",
      "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
      "com.typesafe.akka" %% "akka-stream" % akkaVersion,
      "com.typesafe.akka" %% "akka-http" % "10.5.0",

      // log
      "ch.qos.logback" % "logback-classic" % "1.2.10",
      "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5",
    )
  )

lazy val plugin = (project in file("plugin"))
  .enablePlugins(SbtPlugin)
  .settings(
    name := "plugin",
    scalaVersion := scala212,
  )
