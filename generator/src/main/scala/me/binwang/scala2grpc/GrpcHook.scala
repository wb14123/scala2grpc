package me.binwang.scala2grpc

import cats.effect.{Clock, IO}
import io.grpc.Metadata
import org.typelevel.log4cats.Logger

trait GrpcHook {
  def wrapIO[T](context: GrpcIOContext[T]): IO[T]
  def wrapStream[T](context: GrpcStreamContext[T]): fs2.Stream[IO, T]
}

case class GrpcIOContext[T](
  apiName: String,
  request: Any,
  response: IO[T],
  metadata: Metadata,
)


case class GrpcStreamContext[T](
  apiName: String,
  request: Any,
  response: fs2.Stream[IO, T],
  metadata: Metadata,
)

class DefaultGrpcHook(implicit logger: Logger[IO]) extends GrpcHook {

  private def trimString(s: String, length: Int): String = {
    if (s.length > length) {
      s.substring(0, length) + " ..."
    } else {
      s
    }
  }

  override def wrapIO[T](context: GrpcIOContext[T]): IO[T] = {
    for {
      startTime <- Clock[IO].monotonic
      requestStr = trimString(context.request.toString, 1024)
      _ <- logger.info(s"GRPC API started, API: ${context.apiName}, request: $requestStr")
      res <- context.response
      endTime <- Clock[IO].monotonic
      _ <- logger.info(s"GRPC API finished, API: ${context.apiName}, request: $requestStr, time used: ${endTime - startTime}")
    } yield res
  }

  override def wrapStream[T](context: GrpcStreamContext[T]): fs2.Stream[IO, T] = {
    val requestStr = trimString(context.request.toString, 1024)
    for {
      _ <- fs2.Stream.eval(logger.info(s"GRPC stream API started, API: ${context.apiName}, request: $requestStr"))
      res <- context.response
    } yield res
  }
}