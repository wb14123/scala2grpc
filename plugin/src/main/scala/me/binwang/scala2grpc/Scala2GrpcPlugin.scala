package me.binwang.scala2grpc

import me.binwang.scala2grpc.Scala2GrpcPlugin.autoImport.{generateGRPCCode, generateProto, grpcGenCodeDirectory, grpcGeneratorMainClass, protobufDirectory, scalaDocDirectory}
import sbt.*
import sbt.Keys.*

object Scala2GrpcPlugin extends AutoPlugin {
  object autoImport {
    val protobufDirectory = settingKey[File]("The output directory for generated protocol buffer file")
    val scalaDocDirectory = settingKey[File]("The root directory for generated html scala doc")
    val grpcGenCodeDirectory = settingKey[File]("The output directory for generated grpc code")
    val grpcGeneratorMainClass = settingKey[String]("The main class for GRPC generator")
    val generateProto = taskKey[Unit]("Generate protocol buffer files")
    val generateGRPCCode = taskKey[Unit]("Generate grpc service code")
  }


  override lazy val projectSettings = Seq(
    protobufDirectory := baseDirectory.value / "src" / "main" / "protobuf",
    // TODO: fix this to not hardcode the scaladoc path
    scalaDocDirectory := baseDirectory.value / "target" / "scala-2.13" / "api",
    generateProto := Def.taskDyn {
      val outputPath = protobufDirectory.value.getPath
      val scalaDocPath = scalaDocDirectory.value.getPath
      val mainClass = grpcGeneratorMainClass.value
      Def.task {
        (Compile / runMain).toTask(s" $mainClass proto $outputPath $scalaDocPath").value
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
