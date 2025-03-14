package me.binwang.scala2grpc

import org.jsoup.Jsoup
import org.jsoup.nodes.Element

import java.io.File
import scala.jdk.CollectionConverters._
import scala.reflect.runtime.universe.{ClassSymbol, MethodSymbol}

class ScalaDocParser(docRoot: String) {

  def getClassDoc(cls: ClassSymbol): String = {
    val html = getClassDocHtml(cls)
    html.getElementById("comment").getElementsByClass("comment").text()
  }

  def getMethodDoc(cls: ClassSymbol, method: MethodSymbol): String = {
    // TODO: also include @return comment
    getMethodElement(cls, method).flatMap(e => Option(
      e.getElementsByClass("shortcomment")
        .first()
        .text()
    )).getOrElse("")
  }

  def getMethodParamDoc(cls: ClassSymbol, method: MethodSymbol, paramName: String): String = {
    val paramElements = getMethodElement(cls, method)
      .flatMap { e =>Option(e.getElementsByClass("paramcmts").first())}
      .map(_.children().asScala)
      .getOrElse(Seq())
    val keyIdx = paramElements.zipWithIndex.find(_._1.text().equals(paramName)).map(_._2)
    if (keyIdx.isEmpty) {
      ""
    } else {
      paramElements(keyIdx.get+1).getElementsByClass("cmt").text()
    }
  }

  private def getMethodElement(cls: ClassSymbol, method: MethodSymbol): Option[Element] = {
    val html = getClassDocHtml(cls)
    val methodName = s"${cls.fullName}#${method.name.toString}"
    Option(html
      .getElementById("allMembers")
      .getElementsByAttributeValue("name", methodName)
      .first())
  }

  private def getClassDocHtml(cls: ClassSymbol): Element = {
    val classUrl = cls.fullName.replace('.', '/')
    val path = s"$docRoot/$classUrl.html"
    Jsoup.parse(new File(path)).body()
  }
}
