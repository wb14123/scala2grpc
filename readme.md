# Scala2grpc

A scala plugin to generate GRPC proto file from Scala classes.

A GRPC server wrapper that only needs to provide Scala classes.


## Usage

### 1. Add dependencies and config `build.sbt`:


```
// file project/plugin.sbt


addSbtPlugin("me.binwang" % "scala2grpc-plugin" % "0.1.0-SNAPSHOT")
addSbtPlugin("com.lightbend.akka.grpc" % "sbt-akka-grpc" % "2.1.0") // needed if you want to run GRPC server

```

```
// file build.sbt

import me.binwang.scala2grpc.Scala2GrpcPlugin.AutoImport.grpcGeneratorMainClass

enablePlugins(Scala2GrpcPlugin)
enablePlugins(AkkaGrpcPlugin) // needed if you want to run GRPC server


grpcGeneratorMainClass := "me.binwang.example.GenerateGRPC" // Set this class to the object defined below

libraryDependencies += "me.binwang" %% "scala2grpc-generator" % "0.1.0-SNAPSHOT"

```

### 2. Create an object to implement `GRPCGenerator`


```
import me.binwang.scala2grpc.GRPCGenerator

object GenerateGRPC extends GRPCGenerator {

  // Add `option java_package = "me.binwang.example.grpc";` to the top of the generated GRPC proto file
  override val protoJavaPackage: String = "me.binwang.example.grpc"

  // Add `package example` at the top of generated GRPC proto file
  override val protoPackage: String = "example"

  // Optional: if there are unsupported types in Scala classes, use this map to define the type mapping
  override val customTypeMap = Map(
    typeOf[ZonedDateTime].toString -> typeOf[Long],
  )

  // Optional: if there are unsupported types in Scala classes, use this class to define implicit transform methods
  override val implicitTransformClass: Option[Class[_]] = Some(ModelTranslator.getClass)

  // The classes to transform into GRPC messages. Only case classes are supported.
  override val modelClasses = Seq(
    typeOf[ExampleCaseClass1],
    typeOf[ExampleCaseClass2],
    ...
  )

  // The classes to transform into GRPC services
  override val serviceClasses = Seq(
    typeOf[ServiceClass1],
    typeOf[ServiceClass2],
    ...
  )
}

```

### 3. (Optional) Define an object for implicit type transform methods

If you have defined `customTypeMap`, you need to set `implicitTransformClass` to an object that contains the implicit methods to transform these types. For example:

```

object ModelTranslator extends GrpcTypeTranslator {

  implicit def longToDateTime(timestamp: Long): ZonedDateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault())
  implicit def dateTimeToLong(datetime: ZonedDateTime): Long = datetime.toInstant.toEpochMilli
}

```


### 4. Run sbt task to generate GRPC proto and code

Now you can run sbt task to generate grpc files. If any of the model or service classes has been changed, you also need to re-run the task before compile the code.


```
sbt clean generateGRPCCode
```

Then compile the code (or run other tasks)

```
sbt compile
```

`clean` is needed because there maybe outdated generated classes.

**If you only want to generate GRPC proto file** without be able to run GRPC server, run this task instead:

```
sbt generateProto
```

You don't need to run clean in this case.


### 5. Create a GRPC server

The object `GenerateGRPC` you defined above inheritted a method `getHandlers` that generates GRPC handlers for akka-grpc. Here is an example about how to use it:

```
  def main(args: Array[String]): Unit = {
    val exampleService1 = new ExampleService1(...) // create an instance of your service class
    val exampleService2 = new ExampleService2(...) // create an instance of your service class

    val handlers = GenerateGRPC.getHandlers(Seq(exampleService1, exampleService2))

    val webService = WebHandler.grpcWebHandler(handlers: _*) // for grpc web
    val service = ServiceHandler.concatOrNotFound(handlers: _*)

    val host = "0.0.0.0"
    val port = 8888
    val webPort = 8889 // for grpc web

    Http().newServerAt(host, port).bind(service).flatMap { _ =>
      println(s"Started GRPC server at $host:$port")
      Http().newServerAt(host, webPort).bind(webService) // for grpc web
    }.flatMap {_ =>
      println(s"Started GRPC web server at $host:$webPort")
    }
 }

```
