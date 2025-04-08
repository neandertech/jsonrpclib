package jsonrpclib.internals

import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
import com.github.plokhotnyuk.jsoniter_scala.macros.CodecMakerConfig
import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec

// This is in a separate file from RawMessage
// as workaround for https://github.com/plokhotnyuk/jsoniter-scala/issues/564
private[jsonrpclib] object RawMessageCodec {

  implicit val rawMessageJsonValueCodecs: JsonValueCodec[RawMessage] =
    JsonCodecMaker.make(CodecMakerConfig.withSkipNestedOptionValues(true))

}
