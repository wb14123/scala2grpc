package me.binwang.scala2grpc

import cats.effect.{Clock, IO}
import io.grpc.{Metadata, Status, StatusRuntimeException}
import org.typelevel.log4cats.LoggerFactory

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

class ChainedGrpcHook(hooks: Seq[GrpcHook]) extends GrpcHook {
  override def wrapIO[T](context: GrpcIOContext[T]): IO[T] = {
    hooks.foldRight(context.response){ case (hook, resp) => hook.wrapIO(context.copy(response = resp))}
  }

  override def wrapStream[T](context: GrpcStreamContext[T]): fs2.Stream[IO, T] = {
    hooks.foldRight(context.response){ case (hook, resp) => hook.wrapStream(context.copy(response = resp))}
  }
}

class ErrorWrapperHook(implicit loggerFactory: LoggerFactory[IO]) extends GrpcHook {
  private val logger = LoggerFactory.getLoggerFromClass[IO](this.getClass)

  protected def mapError(err: Throwable, metadata: Metadata): StatusRuntimeException = {
    metadata.put(Metadata.Key.of("err_msg", Metadata.ASCII_STRING_MARSHALLER), err.toString)
    val status = Status.UNKNOWN.withCause(err)
    new StatusRuntimeException(status, metadata)
  }

  private def handleAttempt[T](metadata: Metadata, msg: String)(result: Either[Throwable, T]): IO[Either[Throwable, T]] = {
    result match {
      case Left(err) => logger.error(err)(msg).map(_ => Left(mapError(err, metadata)))
      case x => IO.pure(x)
    }
  }

  override def wrapIO[T](context: GrpcIOContext[T]): IO[T] = {
    context.response
      .attempt
      .flatMap(handleAttempt[T](context.metadata, s"GRPC API failed, API: ${context.apiName}"))
      .rethrow
  }

  override def wrapStream[T](context: GrpcStreamContext[T]): fs2.Stream[IO, T] = {
    context.response
      .attempt
      .evalMap(handleAttempt[T](context.metadata, s"GRPC stream API error, API: ${context.apiName}"))
      .rethrow
  }
}

class RequestLoggerHook(implicit loggerFactory: LoggerFactory[IO]) extends GrpcHook {
  private val logger = LoggerFactory.getLoggerFromClass[IO](this.getClass)

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