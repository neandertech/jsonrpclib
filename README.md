[![CI](https://github.com/neandertech/jsonrpclib/actions/workflows/ci.yml/badge.svg)](https://github.com/neandertech/jsonrpclib/actions/workflows/ci.yml)

[![jsonrpclib-fs2 Scala version support](https://index.scala-lang.org/neandertech/jsonrpclib/jsonrpclib-fs2/latest-by-scala-version.svg?platform=jvm](https://index.scala-lang.org/neandertech/jsonrpclib/jsonrpclib-fs2)

[![jsonrpclib-fs2 Scala version support](https://index.scala-lang.org/neandertech/jsonrpclib/jsonrpclib-fs2/latest-by-scala-version.svg?platform=sjs1](https://index.scala-lang.org/neandertech/jsonrpclib/jsonrpclib-fs2)


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

See the examples folder
