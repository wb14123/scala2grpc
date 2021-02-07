package me.binwang.scala2grpc

import sbt._

object Scala2GrpcPlugin extends AutoPlugin {

  object autoImport {
    val grpcScalaModelSources = taskKey[Seq[File]]("sources for Scala case classes")
    val grpcScalaServiceSources = taskKey[Seq[File]]("sources for Scala services")
    val grpcFileTarget = settingKey[File]("target directory for GRPC protobuf files")
    val generate = TaskKey[Unit]("scala2grpc", "generate grpc files")
  }

  import autoImport._

  val generateTask = Def.task {
    new GrpcGenerator(grpcScalaModelSources.value, grpcScalaServiceSources.value, grpcFileTarget.value).generate()
  }

  override lazy val projectSettings = Seq(
    generate := generateTask.value,
  )

}
