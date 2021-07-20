package me.binwang.scala2grpc

import scala.meta._
import java.io.File

class GrpcGenerator(modelSources: Seq[File], serviceSources: Seq[File], targetDirectory: File,
                    customTypeMapping: Map[String, GrpcType]
    ) {

  val typeMap: Map[String, GrpcType] = Map(
    "String" -> GrpcType.String,
    "Int" -> GrpcType.Int32,
    "Long" -> GrpcType.Int64,
  ) ++ customTypeMapping

  def generate(): Unit = {
    println(modelSources)
    println(serviceSources)
    println(targetDirectory)

    val file = modelSources(3)
    println(file)
    val source = fileToTree(file)
    sourceToCaseClasses(source).foreach(caseClassToProtoFile)
  }

  def fileToTree(file: File): Source = {
    file.parse[Source].get
  }

  def sourceToCaseClasses(source: Source): List[Defn.Class] = {
    source.collect {
      case x: Defn.Class => x
    }
  }

  def caseClassToProtoFile(cls: Defn.Class): Unit = {
    val messageTypeStr = cls.name.toString.split('.').last
    val fields = cls.ctor.paramss.head.map { x =>
      (x.name.toString(), x.decltpe.get)
    }
    val fieldMsg = generateFieldsMsg(fields)
    print(fieldMsg)
  }

  private def generateFieldsMsg(fields: List[(String, Type)]): String = {
    fields.zipWithIndex.map { case ((name, typ), idx) =>
      s"    ${getGRPCType(typ)} $name = ${idx + 1};\n"
    }.mkString
  }


  private def getGRPCType(typ: Type): String = {
    val (realType, prefix) = typ match {
      case Type.Apply(Type.Name("Option"), realTypes) => (realTypes.head, "optional")
      case Type.Apply(Type.Name("Seq"), realTypes) => (realTypes.head, "repeated")
      case realType: Type.Name => (realType, "")
    }

    val typStr = realType.toString.split('.').last
    val grpcType = typeMap.get(typStr) match {
      case None => typStr
      case Some(grpcType) => grpcType.name
    }
    if (prefix.equals("")) {
      grpcType
    } else {
      prefix + " " + grpcType
    }

  }


}
