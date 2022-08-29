package jsonrpclib

private[jsonrpclib] trait PlatformCompat {
  def executionContextLoop(): Unit =
    scalanative.runtime.loop()
}
