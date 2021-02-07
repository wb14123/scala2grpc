package me.binwang.scala2grpc

import java.io.File

class GrpcGenerator(modelSources: Seq[File], serviceSources: Seq[File], targetDirectory: File) {

  def generate(): Unit = {
    println(modelSources)
    println(serviceSources)
    println(targetDirectory)
  }

}
