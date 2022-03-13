package me.binwang.scala2grpc

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import cats.effect.{ContextShift, IO}

import scala.concurrent.{ExecutionContext, Future}


trait AbstractGRPCServer {
  def getHandlers(services: Seq[Any])(implicit actorSystem: ActorSystem, ex: ExecutionContext, cs: ContextShift[IO]
    ): Seq[PartialFunction[HttpRequest, Future[HttpResponse]]]
}
