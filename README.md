[![CI](https://github.com/neandertech/jsonrpclib/actions/workflows/ci.yml/badge.svg)](https://github.com/neandertech/jsonrpclib/actions/workflows/ci.yml)

# jsonrpclib

This is a cross-platform, cross-scala-version [jsonrpc](https://www.jsonrpc.org/) library that provides construct for bidirectional communication using
the jsonrpc protocol.

This library does not enforce any transport, and works as long as you can provide input/output byte streams.


## Dev Notes

### Scala-native

See
* https://github.com/scala-native/scala-native/blob/63d07093f6d0a6e9de28cd8f9fb6bc1d6596c6ec/test-interface/src/main/scala/scala/scalanative/testinterface/NativeRPC.scala


### Scala-js

See
* https://github.com/scala-js/scala-js-js-envs/blob/main/nodejs-env/src/main/scala/org/scalajs/jsenv/nodejs/ComSupport.scala#L245
* https://github.com/scala-js/scala-js/blob/0708917912938714d52be1426364f78a3d1fd269/test-bridge/src/main/scala/org/scalajs/testing/bridge/JSRPC.scala
