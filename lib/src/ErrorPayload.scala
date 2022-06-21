package jsonrpclib

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker

private[jsonrpclib] case class ErrorPayload(code: Int, message: String, data: Option[Payload])

private[jsonrpclib] object ErrorPayload {

  implicit val rawMessageStubJsonValueCodecs: JsonValueCodec[ErrorPayload] =
    JsonCodecMaker.make

}
