package me.binwang.scala2grpc

import sbt.Keys.{resourceManaged, unmanagedSources}
import sbt._

object Scala2GrpcPlugin extends AutoPlugin {

  object autoImport {
    val sources = taskKey[Seq[File]]("sources")
    val target = settingKey[File]("target")
    val generate = TaskKey[Unit]("scala2grpc", "generate grpc files")
  }

  import autoImport._

  val generateTask = Def.task { new GrpcGenerator(sources.value, target.value).generate() }

  override lazy val projectSettings = Seq(
    sources := (unmanagedSources in Compile).value,
    generate := generateTask.value,
  )

}
