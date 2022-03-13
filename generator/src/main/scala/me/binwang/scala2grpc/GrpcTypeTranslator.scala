package me.binwang.scala2grpc

import akka.NotUsed
import akka.grpc.GrpcServiceException
import akka.grpc.scaladsl.MetadataBuilder
import akka.stream.scaladsl.Source
import cats.effect.{ContextShift, IO}
import com.typesafe.scalalogging.Logger
import io.grpc.Status
import streamz.converter._

import scala.concurrent.{ExecutionContext, Future}
import scala.language.implicitConversions
import scala.util.{Failure, Success}

trait GrpcTypeTranslator {

  private val logger = Logger(this.getClass)

  def buildGrpcError(error: Throwable): GrpcServiceException = {
    val exceptionMetadata = new MetadataBuilder()
      .addText("err_msg", error.toString)
      .build()
    logger.error("Error while handling request", error)
    val status = Status.UNKNOWN.withCause(error)
    new GrpcServiceException(status, exceptionMetadata)
  }

  implicit class StreamConverter[A](fs2Stream: fs2.Stream[IO, A]) {
    def toAkka(implicit cs: ContextShift[IO]): Source[A, NotUsed] = {
      val s: fs2.Stream[IO, A] = fs2Stream.handleErrorWith(e => fs2.Stream.raiseError[IO](buildGrpcError(e)))
      Source.fromGraph(s.toSource)
    }
  }

  implicit class IOToFuture[T](io: IO[T]) {
    def toFuture(implicit ex: ExecutionContext): Future[T] = {
      io.unsafeToFuture().transform {
        case Failure(error) =>
          Failure(buildGrpcError(error))
        case Success(v) => Success(v)
      }
    }
  }


}
