package me.binwang.scala2grpc

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import cats.effect.{ContextShift, IO}

import java.io.File
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.runtime.universe._

trait GRPCGenerator {
  val protoJavaPackage: String
  val protoPackage: String

  val modelClasses: Seq[Type]
  val serviceClasses: Seq[Type]

  val customTypeMap: Map[String, Type] = Map()
  val implicitTransformClass: Option[Class[_]] = Some(DefaultGrpcTypeTranslator.getClass)

  def main(args: Array[String]): Unit = {
    if (args.length < 2) {
      printUsage()
    } else if (args(0).equals("proto")) {
      generateProtoFile(args(1))
    } else if (args(0).equals("code")) {
      generateCode(args(1))
    } else {
      printUsage()
    }
  }

  def getHandlers(services: Seq[Any])(implicit actorSystem: ActorSystem, ex: ExecutionContext, cs: ContextShift[IO]): Seq[PartialFunction[HttpRequest, Future[HttpResponse]]] = {
    Class
      .forName(s"$protoJavaPackage.GRPCServer")
      .getDeclaredConstructor()
      .newInstance()
      .asInstanceOf[AbstractGRPCServer]
      .getHandlers(services)
  }

  private def printUsage(): Unit = {
    println(
      """Usage:
        |  Generate protocol buffer file: <exec> proto <output_dir>
        |  Generate Scala code from protocol buffer file: <exec> code <output_dir>
        |""".stripMargin)
  }

  private def generateProtoFile(protoOutputDirectory: String): Unit = {
    val protoGenerator = new ProtoGenerator(protoJavaPackage, protoPackage, protoOutputDirectory, customTypeMap)
    modelClasses.foreach(protoGenerator.addModelToFile)
    serviceClasses.foreach(protoGenerator.addAPIToFile)
    protoGenerator.write()
  }

  private def generateCode(codeRootDirectory: String): Unit = {
    val serviceOutputDirectory = codeRootDirectory + "/" + protoJavaPackage.replace('.', '/')
    new File(serviceOutputDirectory).mkdirs()
    val modelTransformGenerator = new ModelTransformGenerator(protoJavaPackage, protoJavaPackage,
      serviceOutputDirectory, customTypeMap)
    val codeGenerator = new CodeGenerator(protoJavaPackage, protoJavaPackage, serviceOutputDirectory,
      customTypeMap, implicitTransformClass)
    modelClasses.foreach(modelTransformGenerator.writeTranslator)
    serviceClasses.foreach(codeGenerator.writeService)
    codeGenerator.writeGRPCServerFile(serviceClasses)
  }


}
