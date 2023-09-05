package me.binwang.scala2grpc

import cats.effect.{IO, Resource}
import io.grpc.{ServerBuilder, ServerServiceDefinition}
import org.typelevel.log4cats.Logger

import java.io.File
import scala.reflect.runtime.universe._

trait GRPCGenerator {
  val protoJavaPackage: String
  val protoPackage: String

  val modelClasses: Seq[Type]
  val serviceClasses: Seq[Type]

  implicit val logger: Logger[IO]

  val customTypeMap: Map[String, Type] = Map()
  val implicitTransformClass: Option[Class[_]] = Some(DefaultGrpcTypeTranslator.getClass)

  implicit val grpcHook: GrpcHook = new DefaultGrpcHook()

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

  def getServiceDefinitions(services: Seq[Any]): Seq[Resource[IO, ServerServiceDefinition]] = {
    Class
      .forName(s"$protoJavaPackage.GRPCServer")
      .getDeclaredConstructor()
      .newInstance()
      .asInstanceOf[AbstractGRPCServer]
      .getServiceDefinitions(services)
  }

  def addServicesToServerBuilder[T <: ServerBuilder[T]](serverBuilder: T, services: Seq[Any]): Resource[IO, T] = {
    val svcDefs = getServiceDefinitions(services)
    if (svcDefs.isEmpty) {
      Resource.pure(serverBuilder)
    } else {
      val sb = svcDefs.head.map(serverBuilder.addService)
      svcDefs.tail.foldRight(sb) { case (serviceDefResource, curBuilder) =>
        serviceDefResource.flatMap(x => curBuilder.map(_.addService(x)))
      }
    }
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
      serviceOutputDirectory, customTypeMap, implicitTransformClass)
    val codeGenerator = new CodeGenerator(protoJavaPackage, protoJavaPackage, serviceOutputDirectory,
      customTypeMap, implicitTransformClass)
    modelClasses.foreach(modelTransformGenerator.writeTranslator)
    serviceClasses.foreach(codeGenerator.writeService)
    codeGenerator.writeGRPCServerFile(serviceClasses)
  }


}
