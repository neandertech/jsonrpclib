package jsonrpclib

private[jsonrpclib] trait PlatformCompat {
  def executionContextLoop(): Unit = ()
}
