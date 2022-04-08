package me.binwang.scala2grpc

import cats.effect.{IO, Resource}
import com.typesafe.scalalogging.Logger

import java.io.{File, PrintWriter}
import scala.reflect.runtime.universe._

class CodeGenerator(codePackage: String, translatorPackage: String, outputDirectory: String,
                    customTypeMap: Map[String, Type] = Map(), implicitTranslatorClass: Option[Class[_]] = None) {


  private val logger = Logger(classOf[CodeGenerator])

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
      .map(t => s"      case s: ${t.toString} => ${handlerName(t)}.partial(new ${className(t)}(s, ex, cs))")
      .mkString("\n")

    val code = s"""package $codePackage
       |
       |import akka.actor.ActorSystem
       |import $GENERATOR_PACKAGE.AbstractGRPCServer
       |import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
       |import scala.concurrent.{Future, ExecutionContext}
       |import cats.effect.{ContextShift, IO}
       |
       |class GRPCServer extends AbstractGRPCServer {
       |  override def getHandlers(services: Seq[Any])(implicit actorSystem: ActorSystem, ex: ExecutionContext, cs: ContextShift[IO]):
       |      Seq[PartialFunction[HttpRequest, Future[HttpResponse]]]= {
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

  def generateServiceCode(serviceType: Type): String = {
    s"""${generateCodeHeader(serviceType)}
       |${generateClass(serviceType)}
       |""".stripMargin
  }

  private def generateCodeHeader(serviceType: Type): String = {
    val result = s"""package $codePackage
       |
       |import akka.NotUsed
       |import akka.stream.scaladsl.Source
       |import cats.effect.{ContextShift, IO}
       |import ${serviceType.toString}
       |
       |import scala.concurrent.{ExecutionContext, Future}
       |
       |import io.scalaland.chimney.dsl._
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
       |$methodStr
       |}
       |""".stripMargin
  }

  private def generateClassHeader(serviceType: Type): String = {
    val serviceTypeName = serviceType.toString.split('.').last
    s"""class ${className(serviceType)}(
       |    val $serviceVarName: $serviceTypeName,
       |    implicit val ex: ExecutionContext,
       |    implicit val cs: ContextShift[IO]) extends ${Names.apiName(serviceType)}""".stripMargin
  }

  private def generateMethodCode(method: MethodSymbol): String = {
    logger.info(s"Generate method $method")
    val methodName = Names.serviceMethodName(method)
    val returnName = Names.responseMsgName(method)
    val returnType = method.returnType
    val (returnSig, insideType, mapMethod) =  if (returnType.typeSymbol == typeOf[IO[_]].typeSymbol) {
      (s"Future[$returnName]", returnType.typeArgs.head, "toFuture")
    } else if (returnType.typeSymbol == typeOf[fs2.Stream[IO, _]].typeSymbol) {
      (s"Source[$returnName, NotUsed]", returnType.typeArgs(1), "toAkka")
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
    s"""
       |  override def $methodName(in: ${Names.requestMsgName(method)}): $returnSig = {
       |    $serviceVarName.$methodName(${generateMethodParams(method)})$transformResult
       |      .$mapMethod
       |  }
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
        val rawParam = if (deep > 0) "in" else s"in.$paramName.get"
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

  private def handlerName(serviceType: Type): String = {
    s"${Names.apiName(serviceType)}Handler"
  }

}
