# Scala2grpc


Scala2grpc is a SBT plugin to make it non-invasive to use gRPC with Scala.

Normally gRPC generates models and API interfaces. And in order to create a gRPC server, the program needs to use the generated models to implement generated interfaces. This makes the whole program depends on gRPC very heavily.

With this SBT plugin, you can write your code in pure Scala without think about depending on gRPC. It can generate gRPC proto file from Scala classes, and also provides an easy to use interface to let you create a gRPC server from those classes. You don't need to change any existing Scala code in order to do that, so the gRPC part doesn't pollute your pure Scala code at all.

The generated gRPC proto file is well formated so you can track it with version control system and share it with other clients.

Read [this blog post](https://www.binwang.me/2022-05-02-A-Library-to-Make-It-Easier-to-Use-Scala-with-GRPC.html) for more details.

## Versions

Version 1.x has breaking changes from version 0.x. If you need to check the document for v0.x, go to the [v0.x branch](https://github.com/wb14123/scala2grpc/tree/v0.x).

Version 1.x is using fs2-grpc and Cats Effect 3.x. Version 0.x is using Akka GRPC and Cats Effect 2.x.

## Example

For a more complete example, check [scala2grpc-example project](https://github.com/wb14123/scala2grpc-example). The following simple example will give you a taste of how this library works.

Define models and services in pure Scala:

```Scala
case class People(
    firstName: String,
    lastName: String,
)

class PeopleService() {
  def getName(people: People): IO[String] = {
    IO.pure(people.firstName + " " + people.lastName)
  }
}

```

After some configuration without touching the Scala code above, it will generate gRPC proto file like this:


```
// Define People

message People {
    string firstName = 1;
    string lastName = 2;
}


// Define me.binwang.scala2grpc.grpc.generator.PeopleService

message GetNameRequest {
    People people = 1;
}
message GetNameResponse {
    string result = 1;
}

service PeopleAPI {
    rpc GetName (GetNameRequest) returns (GetNameResponse);
}

```

It will also generate Scala files to let you start a gRPC service like this:

```Scala
object ExampleGrpcServer extends IOApp {
  override def run(args: List[String]): IO[ExitCode] = {

    val services = Seq(
      new PeopleService(),
    )

    val serverBuilder = NettyServerBuilder.forPort(9999)

    GenerateGRPC.addServicesToServerBuilder(serverBuilder, services).flatMap { sb =>
      fs2GrpcSyntaxServerBuilder(sb)
        .resource[IO]
        .evalMap(server => IO(server.start()))
    }.useForever

  }
}
```

See usages below for more details about how to use it.


## Usage

### 1. Add dependencies and config `build.sbt`:


```
// file project/plugin.sbt


addSbtPlugin("org.typelevel" % "sbt-fs2-grpc" % "2.7.5")
addSbtPlugin("me.binwang.scala2grpc" % "plugin" % "1.0.0")

```

```
// file build.sbt

enablePlugins(Fs2Grpc)
enablePlugins(Scala2GrpcPlugin)


grpcGeneratorMainClass := "me.binwang.example.GenerateGRPC" // Set this class to the object defined below

libraryDependencies += "me.binwang.scala2grpc" %% "generator" % "1.0.0"
```

### 2. Create an object to implement `GRPCGenerator`


```Scala
import me.binwang.scala2grpc.GRPCGenerator

object GenerateGRPC extends GRPCGenerator {

  // Add `option java_package = "me.binwang.example.grpc";` to the top of the generated GRPC proto file
  override val protoJavaPackage: String = "me.binwang.example.grpc"

  // Add `package example` at the top of generated GRPC proto file
  override val protoPackage: String = "example"

  // provide logging for `DefaultGrpcHook` (see step 4 for details)
  override implicit def loggerFactory: LoggerFactory[IO] = Slf4jFactory.create[IO]

  // Optional: if there are unsupported types in Scala classes, use this map to define the type mapping
  override val customTypeMap = Map(
    typeOf[ZonedDateTime].toString -> typeOf[Long],
  )

  // Optional: if there are unsupported types in Scala classes, use this class to define implicit transform methods
  // See step 3 for details.
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

```Scala
object ModelTranslator extends GrpcTypeTranslator {

  implicit def longToDateTime(timestamp: Long): ZonedDateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault())
  implicit def dateTimeToLong(datetime: ZonedDateTime): Long = datetime.toInstant.toEpochMilli
}

```

### 4. (Optional) Define custom GRPC hook

By default, `GRPCGenerator` provides `DefaultGrpcHook` as `grpcHook`:

```Scala
implicit def grpcHook: GrpcHook = new DefaultGrpcHook()
```

You can override it by providing a custom hook that implements `GrpcHook`:

```Scala
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
```

You are able to do anything in `wrapIO` and `wrapStream` as long as you return `context.response` at the end. For example, an implementation of print logs before and after the request could be:

```
  override def wrapIO[T](context: GrpcIOContext[T]): IO[T] = {
    for {
      _ <- logger.info(s"GRPC API started, API: ${context.apiName}")
      res <- context.response
      _ <- logger.info(s"GRPC API finished, API: ${context.apiName}")
    } yield res
  }
```

`DefaultGrpcHook` provides some default behaviours that is handy:

* Log before request, includes API name and request params.
* Log after request, include API name, request param and time used.
* Log error if there is any.
* Provides `mapError` method that you can override so that you can map exceptions to GRPC exceptions.

### 5. Run sbt task to generate GRPC proto and code

Now you can run sbt task to generate grpc files. If any of the model or service classes has been changed, you also need to re-run the task before compile the code. `clean` is needed because there maybe outdated generated classes.

```
sbt clean generateGRPCCode
```

Then compile the code (or run other tasks)

```
sbt compile
```


**If you only want to generate gRPC proto file** without be able to run gRPC server, run this task instead:

```
sbt clean generateProto
```


### 6. Create a gRPC server

The object `GenerateGRPC` you defined above inherited a method `addServicesToServerBuilder` that add service definitions to ServiceBuilder for fs2-grpc. Here is an example of how to use it:

```Scala
object ExampleGrpcServer extends IOApp {
  override def run(args: List[String]): IO[ExitCode] = {

    val services = Seq(
      new ExampleService(),
    )

    val serverBuilder = NettyServerBuilder.forPort(9999)

    GenerateGRPC.addServicesToServerBuilder(serverBuilder, services).flatMap { sb =>
      fs2GrpcSyntaxServerBuilder(sb)
        .resource[IO]
        .evalMap(server => IO(server.start()))
    }.useForever

  }
}
```

## Publish

For maintainers only

```
sbt +publishLocal
sbt +publishSigned

// only for non snapshot version
sbt sonatypeBundleRelease
```

