# Scala2grpc


Scala2grpc is a SBT plugin to make it non-invasive to use gRPC with Scala.

Normally gRPC generates models and API interfaces. And in order to create a gRPC server, the program needs to use the generated models to implement generated interfaces. This makes the whole program depends on gRPC very heavily.

With this SBT plugin, you can write your code in pure Scala without think about depending on gRPC. It can generate gRPC proto file from Scala classes, and also provides an easy to use interface to let you create a gRPC server from those classes. You don't need to change any existing Scala code in order to do that, so the gRPC part doesn't pollute your pure Scala code at all.

The generated gRPC proto file is well formated so you can track it with version control system and share it with other clients.

Read [this blog post](https://www.binwang.me/2022-05-02-A-Library-to-Make-It-Easier-to-Use-Scala-with-GRPC.html) for more details.


## Example

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
val peopleService = new PeopleService()
val handlers = GenerateGRPC.getHandlers(Seq(peopleService))
val service = ServiceHandler.concatOrNotFound(handlers: _*)

Http().newServerAt(host, port).bind(service).flatMap { _ =>
      println(s"Started GRPC server at $host:$port")
}

```

See usages below for more details about how to use it.


## Usage

### 1. Add dependencies and config `build.sbt`:


```
// file project/plugin.sbt


addSbtPlugin("me.binwang.scala2grpc" % "plugin" % "0.1.0")
addSbtPlugin("com.lightbend.akka.grpc" % "sbt-akka-grpc" % "2.1.0") // needed if you want to run GRPC server

```

```
// file build.sbt

enablePlugins(Scala2GrpcPlugin)
enablePlugins(AkkaGrpcPlugin) // needed if you want to run GRPC server


grpcGeneratorMainClass := "me.binwang.example.GenerateGRPC" // Set this class to the object defined below

libraryDependencies += "me.binwang.scala2grpc" %% "generator" % "0.1.0"

```

### 2. Create an object to implement `GRPCGenerator`


```Scala
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

```Scala
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

Sometimes the generated proto file is conflict with the modified model classes. If this the case, you can delete the proto file and re-generate it.

**If you only want to generate gRPC proto file** without be able to run gRPC server, run this task instead:

```
sbt generateProto
```

You don't need to run clean in this case.


### 5. Create a gRPC server

The object `GenerateGRPC` you defined above inherited a method `getHandlers` that generates gRPC handlers for akka-grpc. Here is an example about how to use it:

```Scala
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

## Publish

For maintainers only

```
sbt +publishLocal
sbt +publishSigned

// only for non snapshot version
sbt sonatypeBundleRelease
```

