
package me.binwang.scala2grpc

import cats.effect.{IO, Resource}
import io.grpc.ServerServiceDefinition


trait AbstractGRPCServer {
  def getServiceDefinitions(services: Seq[Any]): Seq[Resource[IO, ServerServiceDefinition]]
}