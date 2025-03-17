import sbt.Keys.{libraryDependencies, organization, publishTo}
import sbt.url
import xerial.sbt.Sonatype.autoImport.sonatypePublishToBundle

lazy val scala212 = "2.12.20"
lazy val scala213 = "2.13.16"

ThisBuild / version := "1.1.0"
ThisBuild / organization := "me.binwang.scala2grpc"
ThisBuild / scalaVersion := scala213
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


val supportedScalaVersions = List(scala212, scala213)

lazy val root = (project in file("."))
  .aggregate(generator, plugin)
  .settings(
    name := "scala2grpc",
    crossScalaVersions := Nil,
  )

lazy val generator = (project in file("generator"))
  .enablePlugins(Fs2Grpc)
  .settings(
    name := "generator",
    crossScalaVersions := supportedScalaVersions,
    libraryDependencies ++= Seq(
      "io.grpc" % "grpc-netty-shaded" % scalapb.compiler.Version.grpcJavaVersion,
      "org.scala-lang" % "scala-reflect" % "2.13.16",
      "org.jsoup" % "jsoup" % "1.19.1",

      // this project doesn't use cats effect, IO style logger is only used in generated file
      "org.typelevel" %% "log4cats-slf4j"   % "2.7.0",
    )
  )

lazy val plugin = (project in file("plugin"))
  .enablePlugins(SbtPlugin)
  .settings(
    name := "plugin",
    scalaVersion := scala212,
  )