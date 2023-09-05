package me.binwang.scala2grpc

import me.binwang.scala2grpc.Scala2GrpcPlugin.autoImport.{generateGRPCCode, generateProto, grpcGenCodeDirectory, grpcGeneratorMainClass, protobufDirectory}
import sbt._
import sbt.Keys._

object Scala2GrpcPlugin extends AutoPlugin {
  object autoImport {
    val protobufDirectory = settingKey[File]("The output directory for generated protocol buffer file")
    val grpcGenCodeDirectory = settingKey[File]("The output directory for generated grpc code")
    val grpcGeneratorMainClass = settingKey[String]("The main class for GRPC generator")
    val generateProto = taskKey[Unit]("Generate protocol buffer files")
    val generateGRPCCode = taskKey[Unit]("Generate grpc service code")
  }


  override lazy val projectSettings = Seq(
    protobufDirectory := baseDirectory.value / "src" / "main" / "protobuf",
    generateProto := Def.taskDyn {
      val outputPath = protobufDirectory.value.getPath
      val mainClass = grpcGeneratorMainClass.value
      Def.task {
        (Compile / runMain).toTask(s" $mainClass proto $outputPath").value
      }
    }.value,
    grpcGenCodeDirectory := crossTarget.value / "grpc-gen" / "main",
    generateGRPCCode := Def.taskDyn {
      val outputPath = grpcGenCodeDirectory.value.getPath
      val mainClass = grpcGeneratorMainClass.value
      Def.task {
        (Compile / runMain).toTask(s" $mainClass code $outputPath").value
      }
    }.value ,
    generateGRPCCode := (generateGRPCCode dependsOn generateProto).value,
    Compile / unmanagedSourceDirectories += grpcGenCodeDirectory.value,
    cleanFiles += protobufDirectory.value / "grpc-api.proto",
  )
}
