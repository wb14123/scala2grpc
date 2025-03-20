package me.binwang.scala2grpc

import org.jsoup.Jsoup
import org.jsoup.nodes.Element

import java.io.File
import scala.jdk.CollectionConverters._
import scala.reflect.runtime.universe.{MethodSymbol, Type}

class ScalaDocParser(docRoot: String) {

  def getClassDoc(cls: Type): String = {
    getClassElement(cls).getElementsByClass("comment").text()
  }

  def getMethodDoc(cls: Type, method: MethodSymbol): String = {
    getMethodElement(cls, method).map{ e =>
      val comment = e.select(".fullcomment > .comment").text()
      val returnSection = getParamDoc(e, "returns")
      val returnCmt = if (returnSection.nonEmpty) {
        "Returns " + returnSection
      } else ""
      if (comment.nonEmpty && returnCmt.nonEmpty) {
        comment + "\n" + returnCmt
      } else {
        comment + returnCmt
      }
    }.getOrElse("")
  }

  def getMethodParamDoc(cls: Type, method: MethodSymbol, paramName: String): String = {
    getMethodElement(cls, method) match {
      case None => ""
      case Some(e) => getParamDoc(e, paramName)
    }
  }

  def getClassParamDoc(cls: Type, paramName: String): String = {
    getParamDoc(getClassElement(cls), paramName)
  }

  private def getClassElement(cls: Type): Element = {
    val html = getClassDocHtml(cls)
    html.getElementById("comment")
  }

  private def getParamDoc(elm: Element, paramName: String): String = {
    val paramElements = Option(elm.getElementsByClass("paramcmts").first())
      .map(_.children().asScala)
      .getOrElse(Seq())
    val keyIdx = paramElements.zipWithIndex.find(_._1.text().equals(paramName)).map(_._2)
    if (keyIdx.isEmpty) {
      ""
    } else {
      paramElements(keyIdx.get+1).getElementsByClass("cmt").text()
    }
  }

  private def getMethodElement(cls: Type, method: MethodSymbol): Option[Element] = {
    val html = getClassDocHtml(cls)
    val methodName = s"${cls.toString}#${method.name.toString}"
    Option(html
      .getElementById("allMembers")
      .getElementsByAttributeValue("name", methodName)
      .first())
  }

  private def getClassDocHtml(cls: Type): Element = {
    /*
     This is kind of hack to get the real Enum object that defines the enum value.
     It first checks if it's an enum value, and if so, cut off the last piece after `.` for the class name,
     so it get the parent object that defines the enum value.
     */
    val clsName = if (cls.typeSymbol.asClass.fullName.equals("scala.Enumeration.Value")) {
      cls.toString.split('.').dropRight(1).mkString(".") + "$"
    } else {
      cls.toString
    }
    val classUrl = clsName.replace('.', '/')
    val path = s"$docRoot/$classUrl.html"
    Jsoup.parse(new File(path)).body()
  }
}
