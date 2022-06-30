package me.binwang.scala2grpc

import com.typesafe.scalalogging.Logger

import scala.reflect.runtime.universe
import scala.reflect.runtime.universe._

object Names {

  private val logger = Logger(classOf[ProtoGenerator])

  def apiName(typ: Type): String = {
    typ.typeSymbol.name.toString.replace("Service", "API")
  }

  def methodName(method: MethodSymbol): String = {
    method.name.toString.capitalize
  }

  def serviceMethodName(method: MethodSymbol): String = {
    lowerFirstChar(methodName(method))
  }

  def requestMsgName(method: MethodSymbol): String = {
    methodName(method) + "Request"
  }

  def responseMsgName(method: MethodSymbol): String = {
    methodName(method) + "Response"
  }

  def serviceTypeName(typ: Type): String = {
    baseTypeName(typ)
  }

  def translatorClassName(typ: Type): String = {
    baseTypeName(typ) + "Translator"
  }

  def baseTypeName(typ: Type): String = {
    typ.toString.split('.').last
  }

  def isEnum(typ: Type): Boolean = {
    typ.baseClasses.exists(_.fullName == "scala.Enumeration.Value")
  }

  def filterMethodsFromType(typ: Type): Seq[universe.MethodSymbol] = {
    val defaultMethodPattern = """(.+)\$default\$(\d+)""".r
    typ
      .decls
      .sorted
      .collect { case m: MethodSymbol if m.isPublic && !m.isConstructor && !m.isImplicit && !m.isGetter
        && !m.isSetter && !m.isStatic => m}
      .filter {m =>
        val isDefault = defaultMethodPattern.findFirstMatchIn(m.name.toString).isDefined
        if (isDefault) {
          logger.info(s"Skip auto generated default method $m")
        }
        !isDefault
      }
  }

  private def lowerFirstChar(str: String): String = {
    str.head.toLower + str.tail
  }

}
