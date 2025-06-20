package me.binwang.scala2grpc

import cats.effect.{IO, Resource}
import me.binwang.scala2grpc.Names._
import me.binwang.scala2grpc.ProtoGenerator.typeMap
import org.slf4j.LoggerFactory

import java.io.{File, PrintWriter}
import scala.reflect.runtime.universe._

object ProtoGenerator {
  object GRPCTypes {
    val STRING: String = "string"
    val INT32: String = "int32"
    val INT64: String = "int64"
    val BOOL: String = "bool"
    val DOUBLE: String = "double"
    val FLOAT: String = "float"
    val BYTES: String = "bytes"
  }

  val typeMap: Map[Type, String] = Map(
    typeOf[String] -> GRPCTypes.STRING,
    typeOf[Int] -> GRPCTypes.INT32,
    typeOf[Long] -> GRPCTypes.INT64,
    typeOf[Boolean] -> GRPCTypes.BOOL,
    typeOf[Double] -> GRPCTypes.DOUBLE,
    typeOf[Float] -> GRPCTypes.FLOAT,
    typeOf[Array[Byte]] -> GRPCTypes.BYTES,
  )
}

class ProtoGenerator(javaPackage: String, grpcPackage: String, header: Option[String],
    outputDirectory: String, scalaDocDirectory: String, customTypeMap: Map[String, Type] = Map()) {

  private val logger = LoggerFactory.getLogger(this.getClass)

  private val scalaDocParser = new ScalaDocParser(scalaDocDirectory)
  private var outputMessage = ""

  def addModelToFile(typ: Type): Unit = {
    val message = generateModel(typ)
    outputMessage += "\n"
    outputMessage += message
  }

  def addAPIToFile(serviceType: Type): Unit = {
    val message = generateAPI(serviceType)
    outputMessage += "\n"
    outputMessage += message
  }

  def write(): Unit = {
    val outputFile = new File(outputDirectory, "grpc-api.proto")
    val writer = new PrintWriter(outputFile)
    writer.print(messageHeader() + outputMessage)
    writer.close()
  }

  def generateModel(messageType: Type): String = {
    logger.info(s"Generate model $messageType")
    val comment = wrapGrpcComment(scalaDocParser.getClassDoc(messageType))
    val content = if (Names.isEnum(messageType)) {
      generateEnum(messageType)
    } else {
      val messageTypeStr = modelName(messageType)
      val fields = messageType.members.sorted
        .collect { case m: MethodSymbol if m.isCaseAccessor => m }
        .map { field => (field.name.toString, field.returnType) }
      val fieldsMsg = generateFieldsMsg(messageType, None, fields)
      s"message $messageTypeStr {\n$fieldsMsg}\n"
    }
    comment + content
  }

  private def generateEnum(typ: Type): String = {
    val messageTypeStr = modelName(typ)
    val className = typ.asInstanceOf[TypeRef].pre.typeSymbol.asClass.fullName
    val enumObject = getClass.getClassLoader.loadClass(className + "$").getField("MODULE$").get(null)
    val values = enumObject.getClass.getMethod("values").invoke(enumObject).asInstanceOf[scala.Enumeration#ValueSet]
    val valueFields = values.zipWithIndex.map{case (value, idx) =>
      val comment = wrapGrpcComment(scalaDocParser.getEnumValueDoc(typ, s"$className#$value"), 4)
      comment + s"    $value = $idx;"
    }.mkString("\n\n")
    s"enum $messageTypeStr {\n$valueFields\n}\n"
  }

  private def generateAPI(serviceType: Type): String = {
    logger.info(s"Generate API $serviceType")
    val methods = Names.filterMethodsFromType(serviceType)
    val requestMessages = methods.map(m => generateRequestMsg(serviceType, m)).mkString("")
    val responseMessages = methods.map(generateResponseMsg).mkString("\n")
    val methodsMessages = methods.map(m => generateMethodMsg(serviceType, m)).mkString("\n\n")

    val comment = wrapGrpcComment(scalaDocParser.getClassDoc(serviceType))
    val serviceMsg = s"""
                        |${comment}service ${apiName(serviceType)} {
                        |$methodsMessages
                        |}
                        |""".stripMargin
    requestMessages + responseMessages + serviceMsg
  }

  private def modelName(messageType: Type): String = {
    messageType.toString.split('.').last
  }

  private def messageHeader(): String = {

    s"""
       |syntax = "proto3";
       |
       |${header.getOrElse("")}
       |option java_multiple_files = true;
       |option java_package = "$javaPackage";
       |
       |package $grpcPackage;
       |
       |""".stripMargin

  }

  private def generateFieldsMsg(cls: Type, method: Option[MethodSymbol], fields: Seq[(String, Type)]): String = {
    fields.zipWithIndex.map { case ((name, typ), idx) =>
      val comment = if (method.isDefined) {
        scalaDocParser.getMethodParamDoc(cls, method.get, name)
      } else {
        scalaDocParser.getClassParamDoc(cls, name)
      }
      wrapGrpcComment(comment, 4) + generateField(name, typ, idx + 1)._1
    }.mkString("\n\n") + "\n"
  }

  private def getGRPCType(typ: Type): String = {
    val typName = typ.toString
    val typStr = typName.split('.').last
    typeMap.find(_._1 =:= typ) match {
      case None => customTypeMap.get(typName) match {
        case None => typStr
        case Some(targetTyp) => typeMap.getOrElse(targetTyp, typStr)
      }
      case Some((_, str)) => str
    }
  }

  private def generateField(name: String, originType: Type, index: Int, level: Int = 1): (String, String) = {

    if (originType.typeSymbol == typeOf[Resource[IO, _]].typeSymbol) {
      return generateField(name, originType.typeArgs(1), index, level)
    }

    val (typ, prefix) = if (originType.typeSymbol == typeOf[Option[_]].typeSymbol) {
      (originType.typeArgs.head, "optional ")
    } else if(originType.typeSymbol == typeOf[Seq[_]].typeSymbol) {
      (originType.typeArgs.head, "repeated ")
    } else {
      (originType, "")
    }

    val (msg, realName) = if (typ.typeSymbol == typeOf[Option[_]].typeSymbol) {
      generateField(name + "Option", typ, 1, level + 1) // optional is removed from proto3
    } else if(typ.typeSymbol == typeOf[Seq[_]].typeSymbol) {
      generateField(name + "List", typ, 1, level + 1)
    } else {
      ("", name)
    }

    val realType = msg match {
      case "" => getGRPCType(typ)
      case _ => realName.capitalize
    }

    val indent = " ".repeat(level * 4 - 1)
    val nestedMsg = msg match {
      case "" => ""
      case fieldMsg =>
        s"""|$indent message $realType {
            |$fieldMsg
            |$indent }
            |""".stripMargin
    }
    (s"$nestedMsg$indent $prefix$realType $name = $index;", name)
  }

  private def generateRequestMsg(cls: Type, method: MethodSymbol): String = {
    val methods = method.paramLists
    if (methods.isEmpty) {
      logger.warn("Method length is 0")
      return ""
    }
    if (methods.length != 1) {
      throw new Exception(s"Only support one method with the same name: $methods")
    }
    val fields = methods.head.map { field => (field.name.toString, field.typeSignature) }
    val fieldsMsg = generateFieldsMsg(cls, Some(method), fields)
    s"message ${requestMsgName(method)} {\n$fieldsMsg}\n\n"
  }

  private def generateResponseMsg(method: MethodSymbol): String = {
    val returnType = method.returnType
    val serviceType = if (returnType.typeSymbol == typeOf[IO[_]].typeSymbol) {
      returnType.typeArgs.head
    } else if (returnType.typeSymbol == typeOf[fs2.Stream[IO, _]].typeSymbol) {
      returnType.typeArgs(1)
    } else {
      throw new Exception(
        s"Method return type should be IO or fs2.Stream[IO, _], real method: $method, ${method.name}, real response type: $returnType. " +
          "This error may also caused by define default param value for method.")
    }
    val grpcType = getGRPCType(serviceType)
    if (grpcType.equals("Unit")) {
      return s"message ${responseMsgName(method)} {}\n"
    }
    s"""message ${responseMsgName(method)} {
       |${generateField("result", serviceType, 1)._1}
       |}
       |""".stripMargin
  }

  private def generateMethodMsg(cls: Type, method: MethodSymbol): String = {
    val returnType = method.returnType
    val isStream = returnType.typeSymbol == typeOf[fs2.Stream[IO, _]].typeSymbol
    val responseType = responseMsgName(method)
    val serviceTypeStr = if (isStream) {
      "stream " + responseType
    } else {
      responseType
    }
    val comment = wrapGrpcComment(scalaDocParser.getMethodDoc(cls, method), 4)
    comment + s"    rpc ${methodName(method)} (${requestMsgName(method)}) returns ($serviceTypeStr);"
  }

  private def wrapGrpcComment(comment: String, indent: Int = 0): String = {
    val indentStr = " ".repeat(indent)
    val lines = comment.split('\n').flatMap{l => breakLines(l, indent).split('\n')}
    if (comment.strip().isEmpty) {
      ""
    } else if (lines.length <= 1) {
      indentStr + "// " + comment + "\n"
    } else {
      ("/*" +: lines :+ "*/").map(indentStr + _).mkString("\n") + "\n"
    }
  }

  /*
   It's better to keep the line breaks in the source code file. But generated html doc has already lost the information,
   so this is the best we can do to break the long comment into multiple lines.
   */
  private def breakLines(line: String, indent: Int, maxWidth: Int = 80): String = {
    val commentPrefixLen = 3
    val maxLen = maxWidth - commentPrefixLen - indent
    if (line.length <= maxLen) {
      line
    } else {
      val words = line.split(' ')
      var result = ""
      var curLine = ""
      words.foreach { word =>
        if ((curLine.length + word.length + 1) > maxLen) {
          result += curLine
          curLine = "\n"
        }
        if (curLine.isEmpty || curLine.equals("\n")) {
          curLine += word
        } else {
          curLine += " " + word
        }
      }
      result + curLine
    }
  }
}
