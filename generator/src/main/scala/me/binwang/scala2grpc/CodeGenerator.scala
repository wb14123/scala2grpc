package me.binwang.scala2grpc

import cats.effect.{IO, Resource}
import org.slf4j.LoggerFactory

import java.io.{File, PrintWriter}
import scala.reflect.runtime.universe._

class CodeGenerator(codePackage: String, translatorPackage: String, outputDirectory: String,
    customTypeMap: Map[String, Type] = Map(), implicitTranslatorClass: Option[Class[_]] = None) {


  private val logger = LoggerFactory.getLogger(this.getClass)

  private val GENERATOR_PACKAGE = getClass.getPackage.getName

  private val serviceVarName = "myService"
  private final val PRIMITIVE_TYPES = List("Int", "Boolean", "String", "Double", "Long")

  def writeService(serviceType: Type): Unit = {
    logger.info(s"Generate service implementation for $serviceType")
    val outputFile = new File(outputDirectory, className(serviceType) + ".scala")
    val writer = new PrintWriter(outputFile)
    writer.print(generateServiceCode(serviceType))
    writer.close()
  }

  def writeGRPCServerFile(serviceClasses: Seq[Type]): Unit = {
    logger.info("Generate GRPC service file")

    val params = serviceClasses
      .map(t => s"      case s: ${t.toString} => ${Names.fs2GrpcName(t)}.bindServiceResource[IO](new ${className(t)}(s))")
      .mkString("\n")

    val code = s"""package $codePackage
       |
       |import $GENERATOR_PACKAGE.AbstractGRPCServer
       |import $codePackage.grpc_api._
       |
       |import cats.effect.{IO, Resource}
       |import io.grpc.ServerServiceDefinition
       |import me.binwang.scala2grpc.GrpcHook
       |
       |class GRPCServer extends AbstractGRPCServer {
       |
       |  override def getServiceDefinitions(services: Seq[Any])
       |      (implicit grpcIOHook: GrpcHook): Seq[Resource[IO, ServerServiceDefinition]] = {
       |    services.map {
       |$params
       |      case x => throw new Exception(s"No match service implementation found for $$x")
       |    }
       |  }
       |}
       |""".stripMargin

    val outputFile = new File(outputDirectory, "GRPCServer.scala")
    val writer = new PrintWriter(outputFile)
    writer.print(code)
    writer.close()
  }

  private def generateServiceCode(serviceType: Type): String = {
    s"""${generateCodeHeader(serviceType)}
       |${generateClass(serviceType)}
       |""".stripMargin
  }

  private def generateCodeHeader(serviceType: Type): String = {
    val result = s"""package $codePackage
       |

       |import cats.effect.IO
       |import cats.effect.implicits._
       |import io.grpc.Metadata
       |import $codePackage.grpc_api._
       |import ${serviceType.toString}
       |import me.binwang.scala2grpc.GrpcHook
       |import me.binwang.scala2grpc.GrpcIOContext
       |import me.binwang.scala2grpc.GrpcStreamContext
       |
       |""".stripMargin
    if (implicitTranslatorClass.isDefined) {
      result + "\nimport " + implicitTranslatorClass.get.getName.dropRight(1) + "._"
    } else {
      result
    }
  }

  private def generateClass(serviceType: Type): String = {

    val methods = Names.filterMethodsFromType(serviceType)

    val methodStr = methods.map(generateMethodCode).mkString("")

    s"""
       |${generateClassHeader(serviceType)} {
       |
       |$methodStr
       |}
       |""".stripMargin
  }

  private def generateClassHeader(serviceType: Type): String = {
    val serviceTypeName = serviceType.toString.split('.').last
    s"""class ${className(serviceType)}(val $serviceVarName: $serviceTypeName)
       |  (implicit val grpcHook: GrpcHook) extends ${Names.apiName(serviceType)}Fs2Grpc[IO, Metadata]""".stripMargin
  }

  private def generateMethodCode(method: MethodSymbol): String = {
    logger.info(s"Generate method $method")
    val methodName = Names.serviceMethodName(method)
    val returnName = Names.responseMsgName(method)
    val returnType = method.returnType
    val (returnSig, insideType, isIO) =  if (returnType.typeSymbol == typeOf[IO[_]].typeSymbol) {
      (s"IO[$returnName]", returnType.typeArgs.head, true)
    } else if (returnType.typeSymbol == typeOf[fs2.Stream[IO, _]].typeSymbol) {
      (s"fs2.Stream[IO, $returnName]", returnType.typeArgs(1), false)
    } else {
      throw new Exception(s"Method return type should be IO or fs2.Stream[IO, _], real method: $method")
    }
    val realType = customTypeMap.getOrElse(insideType.toString, insideType)
    val typeName = realType.toString.split('.').last
    val transformResult = if (typeName == "Unit") {
      s"""
         |      .map(_ => $returnName())""".stripMargin
    } else if (PRIMITIVE_TYPES.contains(typeName)) {
      s"""
         |      .map(x => $returnName(result = x))""".stripMargin
    } else {
      val insideTypeName = translatorPackage + "." + Names.translatorClassName(insideType)
      s"""
         |      .map($insideTypeName.toGRPC)
         |      .map(x => $returnName(result = Option(x)))""".stripMargin
    }
    val wrapResult = if (isIO) {
      wrapIOResult(method.name.toString)
    } else {
      wrapStreamResult(method.name.toString)
    }
    s"""
       |  override def $methodName(in: ${Names.requestMsgName(method)}, metadata: Metadata): $returnSig = {
       |    val response = $serviceVarName.$methodName(${generateMethodParams(method)})$transformResult
       |$wrapResult
       |  }
       |""".stripMargin
  }

  private def wrapIOResult(apiName: String): String = {
    s"""
       |    val context = GrpcIOContext("$apiName", in, response, metadata)
       |    grpcHook.wrapIO(context)
       |""".stripMargin
  }

  private def wrapStreamResult(apiName: String): String = {
    s"""
       |    val context = GrpcStreamContext("$apiName", in, response, metadata)
       |    grpcHook.wrapStream(context)
       |""".stripMargin
  }

  private def generateMethodParams(method: MethodSymbol): String = {
    method.paramLists.head.map(x => generateMethodParamCode(x.name.toString, x.typeSignature)).mkString(", ")
  }

  private def generateMethodParamCode(paramName: String, rawParamType: Type, deep: Int = 0): String = {
    /*
    Take care of wrapped types. The supported wrapped types are now hard coded, may need to think about how to let user
    pass in customer configurations.
     */
    val (paramType, wrapperType) = if (rawParamType.typeSymbol == typeOf[Resource[IO, _]].typeSymbol) {
      (rawParamType.typeArgs(1), None)
    } else if(rawParamType.typeSymbol == typeOf[Option[_]].typeSymbol) {
      (rawParamType.typeArgs.head, Some("Option"))
    } else if (rawParamType.typeSymbol == typeOf[Seq[_]].typeSymbol) {
      (rawParamType.typeArgs.head, Some("List"))
    } else {
      (rawParamType, None)
    }
    val realType = customTypeMap.getOrElse(paramType.toString, paramType)
    val typeName = realType.toString.split('.').last
    val wrappedParamName = if (deep > 0) {
      s"in.$paramName" + wrapperType.getOrElse("")
    } else {
      s"in.$paramName"
    }
    if (PRIMITIVE_TYPES.contains(typeName)) {
      wrappedParamName
    } else {
      if (wrapperType.isEmpty) {
        val insideTypeName = translatorPackage + "." + Names.translatorClassName(paramType)
        val rawParam = if (deep > 0) "in" else if (Names.isEnum(realType)) s"in.$paramName" else s"in.$paramName.get"
        s"$insideTypeName.fromGRPC($rawParam)"
      } else if (wrapperType.get.equals("Option")) {
        s"$wrappedParamName.map(in => ${generateMethodParamCode(paramName, paramType, deep + 1)})"
      } else {
        s"$wrappedParamName.map(in => ${generateMethodParamCode(paramName, paramType, deep + 1)})"
      }
    }
  }

  private def className(serviceType: Type): String = {
    s"${Names.apiName(serviceType)}Impl"
  }


}
