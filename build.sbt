name := "scala2grpc"

version := "0.1.0-SNAPSHOT"
organization := "me.binwang"
scalaVersion := "2.12.13"

libraryDependencies ++= Seq(
  "org.scalameta" %% "scalameta" % "4.4.8",
)

lazy val root = (project in file("."))
  .enablePlugins(SbtPlugin)
  .settings(
    name := "scala2grpc",
  )