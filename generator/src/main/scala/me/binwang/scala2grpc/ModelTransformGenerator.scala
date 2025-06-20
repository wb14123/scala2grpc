package me.binwang.scala2grpc

import org.slf4j.LoggerFactory

import java.io.{File, PrintWriter}
import scala.reflect.runtime.universe._

class ModelTransformGenerator(codePackage: String, grpcPackage: String, outputDirectory: String,
    customTypeMap: Map[String, Type] = Map(), implicitTranslatorClass: Option[Class[_]]) {

  private val logger = LoggerFactory.getLogger(this.getClass)
  private val keepTypes: Set[String] = customTypeMap.keySet ++ ProtoGenerator.typeMap.keySet.map(_.toString)

  def writeTranslator(modelType: Type): Unit = {
    val outputFile = new File(outputDirectory, Names.translatorClassName(modelType) + ".scala")
    val writer = new PrintWriter(outputFile)
    writer.print(generateModel(modelType))
    writer.close()
  }

  def generateModel(messageType: Type): String = {
    logger.info(s"Generate model $messageType")

    val typeName = messageType.toString
    val typeBase = Names.baseTypeName(messageType)
    val grpcType = s"$grpcPackage.grpc_api.$typeBase"
    val className = Names.translatorClassName(messageType)

    if (Names.isEnum(messageType)) {
      generateEnumTranslator(className, grpcType, typeName)
    } else {
      val fields= messageType.members.sorted
        .collect { case m: MethodSymbol if m.isCaseAccessor => m }
      val fieldsFromGrpcParams = fields
        .map { field => generateFromGRPCField(field.name.toString, field.returnType) }
        .map {"      " + _}
        .mkString(",\n")
      val fieldsToGrpcParams = fields
        .map { field => generateToGRPCField(field.name.toString, grpcType, field.returnType) }
        .map {"      " + _}
        .mkString(",\n")

      val importTranslator = if (implicitTranslatorClass.isDefined) {
        "import " + implicitTranslatorClass.get.getName.dropRight(1) + "._"
      } else {
        ""
      }

      s"""
        |package $codePackage
        |
        |$importTranslator
        |import com.google.protobuf.ByteString
        |
        |object $className {
        |  def fromGRPC(obj: $grpcType): $typeName = {
        |    $typeName(
        |$fieldsFromGrpcParams
        |    )
        |  }
        |
        |  def toGRPC(obj: $typeName): $grpcType = {
        |    $grpcType(
        |$fieldsToGrpcParams
        |    )
        |  }
        |}
      """.stripMargin
    }
  }

  private def generateEnumTranslator(className: String, grpcType: String, typeName: String): String = {
    val enumClass = typeName.split('.').dropRight(1).mkString(".")
    s"""
       |package $codePackage
       |
       |object $className {
       |  def fromGRPC(obj: $grpcType): $typeName = {
       |    $enumClass.withName(obj.name)
       |  }
       |
       |  def toGRPC(obj: $typeName): $grpcType = {
       |    $grpcType.fromValue(obj.id)
       |  }
       |}
    """.stripMargin
  }

  private def generateFromGRPCField(name: String, typ: Type, deep: Int = 0, rawNameOpt: Option[String] = None): String = {
    val translatorClass = Names.translatorClassName(typ)
    val rawName = rawNameOpt.getOrElse(name)
    if (isByteArray(typ)) {
      s"obj.$name.toByteArray"
    } else if (keepTypes.contains(typ.toString)) {
      if (name.isEmpty) {
        s"obj"
      } else {
        s"obj.$name"
      }
    } else if (typ.typeSymbol == typeOf[Option[_]].typeSymbol) {
      val curField = if (deep == 0) rawName else rawName + "Option"
      val nextRawName = if (typ.typeArgs.head.typeArgs.isEmpty) "" else curField
      s"obj.$curField.map(obj => ${generateFromGRPCField(nextRawName, typ.typeArgs.head, deep + 1, Some(nextRawName))})"
    } else if (typ.typeSymbol == typeOf[Seq[_]].typeSymbol) {
      val curField = if (deep == 0) rawName else rawName + "List"
      val nextRawName = if (typ.typeArgs.head.typeArgs.isEmpty) "" else curField
      s"obj.$curField.map(obj => ${generateFromGRPCField(nextRawName, typ.typeArgs.head, deep + 1, Some(nextRawName))})"
    } else {
      if (name.isEmpty) {
        s"$translatorClass.fromGRPC(obj)"
      } else if (Names.isEnum(typ)) {
        s"$translatorClass.fromGRPC(obj.$name)"
      } else {
        s"$translatorClass.fromGRPC(obj.$name.get)"
      }
    }
  }

  private def generateToGRPCField(name: String, classGRPCType: String, typ: Type, deep: Int = 0,
                                  rawNameOpt: Option[String] = None, nestedClassNameOpt: Option[String] = None): String = {
    val translatorClass = Names.translatorClassName(typ)
    val rawName = rawNameOpt.getOrElse(name)
    val nestedClassName = nestedClassNameOpt.getOrElse(rawName.capitalize)
    val field = if (deep == 0) s"obj.$name" else "obj"
    if (isByteArray(typ)) {
      s"ByteString.copyFrom($field)"
    } else if (keepTypes.contains(typ.toString)) {
      field
    } else if (typ.typeSymbol == typeOf[Option[_]].typeSymbol || typ.typeSymbol == typeOf[Seq[_]].typeSymbol) {
      if (deep == 0) {
        s"$field.map(obj => ${generateToGRPCField(
          name, classGRPCType, typ.typeArgs.head, deep + 1, Some(rawName), nestedClassNameOpt)})"
      } else {
        val nextNestedClassName = if (typ.typeSymbol == typeOf[Option[_]].typeSymbol) {
          nestedClassName + "Option"
        } else {
          nestedClassName + "List"
        }
        val nextOptClass = classGRPCType + "." + nextNestedClassName
        s"$nextOptClass($field.map(obj => ${
          generateToGRPCField(
            nextNestedClassName, nextOptClass, typ.typeArgs.head, deep + 1, Some(rawName), Some(nextNestedClassName))
        }))"
      }
    } else {
      if (deep == 0 && !Names.isEnum(typ)) {
        s"Some($translatorClass.toGRPC($field))"
      } else {
        s"$translatorClass.toGRPC($field)"
      }
    }
  }

  private def isByteArray(typ: Type): Boolean = {
    typ.typeSymbol == typeOf[Array[Byte]].typeSymbol &&
      typ.typeArgs.headOption.exists(_.typeSymbol == typeOf[Byte].typeSymbol)
  }

}
