[![CI](https://github.com/neandertech/jsonrpclib/actions/workflows/ci.yml/badge.svg)](https://github.com/neandertech/jsonrpclib/actions/workflows/ci.yml)

[![jsonrpclib-fs2 Scala version support](https://index.scala-lang.org/neandertech/jsonrpclib/jsonrpclib-fs2/latest-by-scala-version.svg?platform=jvm)](https://index.scala-lang.org/neandertech/jsonrpclib/jsonrpclib-fs2)

[![jsonrpclib-fs2 Scala version support](https://index.scala-lang.org/neandertech/jsonrpclib/jsonrpclib-fs2/latest-by-scala-version.svg?platform=sjs1)](https://index.scala-lang.org/neandertech/jsonrpclib/jsonrpclib-fs2)


# jsonrpclib

This is a cross-platform, cross-scala-version library that provides construct for bidirectional communication using the [jsonrpc](https://www.jsonrpc.org/) protocol. It is built on top of [fs2](https://fs2.io/#/) and [jsoniter-scala](https://github.com/plokhotnyuk/jsoniter-scala)

This library does not enforce any transport, and can work on top of stdin/stdout or other channels.

## Installation

The dependencies below are following [cross-platform semantics](http://youforgotapercentagesignoracolon.com/).
Adapt according to your needs

### SBT

```scala
libraryDependencies += "tech.neander" %%% "jsonrpclib-fs2" % version
```

### Mill

```scala
override def ivyDeps = super.ivyDeps() ++ Agg(ivy"tech.neander::jsonrpclib-fs2::$version")
```

### Scala-cli

```scala
//> using lib "tech.neander::jsonrpclib-fs2:<VERSION>"
```

## Usage

**/!\ Please be aware that this library is in its early days and offers strictly no guarantee with regards to backward compatibility**

See the modules/examples folder.

## Smithy Integration

You can now use `jsonrpclib` directly with [Smithy](https://smithy.io/) and [smithy4s](https://disneystreaming.github.io/smithy4s/), enabling type-safe, 
schema-first JSON-RPC APIs with minimal boilerplate.

This integration is supported by the following modules:

```scala
// Defines the Smithy protocol for JSON-RPC
libraryDependencies += "tech.neander" % "jsonrpclib-smithy" % <version>

// Provides smithy4s client/server bindings for JSON-RPC
libraryDependencies += "tech.neander" %%% "jsonrpclib-smithy4s" % <version>
```

With these modules, you can:

- Annotate your Smithy operations with `@jsonRpcRequest` or `@jsonRpcNotification`
- Generate client and server interfaces using smithy4s
- Use ClientStub to invoke remote services over JSON-RPC
- Use ServerEndpoints to expose service implementations via a Channel

This allows you to define your API once in Smithy and interact with it as a fully typed JSON-RPC service—without writing manual encoders, decoders, or dispatch logic.
