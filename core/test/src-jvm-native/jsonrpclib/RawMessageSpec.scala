package jsonrpclib

import munit._
import com.github.plokhotnyuk.jsoniter_scala.core._
import internals._
import jsonrpclib.CallId.NumberId
import jsonrpclib.OutputMessage.ResponseMessage

class RawMessageSpec() extends FunSuite {
  test("json parsing with null result") {
    val rawMessage = readFromString[RawMessage](""" {"jsonrpc":"2.0","result":null,"id":3} """.trim)
    assertEquals(
      rawMessage,
      RawMessage(jsonrpc = "2.0", result = Some(None), id = Some(NumberId(3)))
    )

    assertEquals(rawMessage.toMessage, Right(ResponseMessage(NumberId(3), Payload.NullPayload)))
  }
}
