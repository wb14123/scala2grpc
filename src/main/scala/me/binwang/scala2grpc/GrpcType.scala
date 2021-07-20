package me.binwang.scala2grpc

case class GrpcType(val name: String)

object GrpcType {
  val String: GrpcType = GrpcType("string")
  val Int64: GrpcType = GrpcType("int64")
  val Int32: GrpcType = GrpcType("int32")
}
