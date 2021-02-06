package me.binwang.scala2grpc

import java.io.File

class GrpcGenerator(sources: Seq[File], targetDirectory: File) {

  def generate(): Unit = {
    println(sources)
    println(targetDirectory)
  }

}
