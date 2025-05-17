package jsonrpclib

import com.github.plokhotnyuk.jsoniter_scala.circe.JsoniterScalaCodec._
import com.github.plokhotnyuk.jsoniter_scala.core._
import io.circe.syntax._
import io.circe.Json
import jsonrpclib.internals._
import jsonrpclib.CallId.NumberId
import jsonrpclib.OutputMessage.ResponseMessage
import weaver._

object RawMessageSpec extends FunSuite {
  test("json parsing with null result") {
    // This is a perfectly valid response object, as result field has to be present,
    // but can be null: https://www.jsonrpc.org/specification#response_object
    val rawMessage = readFromString[Json](""" {"jsonrpc":"2.0","id":3,"result":null}""".trim)
      .as[RawMessage]
      .fold(throw _, identity)

    // This, on the other hand, is an invalid response message, as result field is missing
    val invalidRawMessage =
      readFromString[Json](""" {"jsonrpc":"2.0","id":3} """.trim).as[RawMessage].fold(throw _, identity)

    assert.same(
      rawMessage,
      RawMessage(jsonrpc = "2.0", result = Some(None), id = Some(NumberId(3)))
    ) &&
    assert.same(rawMessage.toMessage, Right(ResponseMessage(NumberId(3), Payload.NullPayload))) &&
    assert.same(
      invalidRawMessage,
      RawMessage(jsonrpc = "2.0", result = None, id = Some(NumberId(3)))
    ) &&
    assert(invalidRawMessage.toMessage.isLeft, invalidRawMessage.toMessage.toString)
  }

  test("request message serialization") {
    val input: Message = InputMessage.RequestMessage("my/method", CallId.NumberId(1), None)
    val expected = """{"jsonrpc":"2.0","method":"my/method","id":1}"""
    val result = writeToString(input.asJson)

    assert(result == expected, s"Expected: $expected, got: $result")
  }

  test("notification message serialization") {
    val input: Message = InputMessage.NotificationMessage("my/method", None)
    val expected = """{"jsonrpc":"2.0","method":"my/method"}"""
    val result = writeToString(input.asJson)

    assert(result == expected, s"Expected: $expected, got: $result")
  }

  test("response message serialization") {
    val input: Message = OutputMessage.ResponseMessage(CallId.NumberId(1), Payload.NullPayload)
    val expected = """{"jsonrpc":"2.0","id":1,"result":null}"""
    val result = writeToString(input.asJson)

    assert(result == expected, s"Expected: $expected, got: $result")
  }

  test("error message serialization") {
    val input: Message = OutputMessage.ErrorMessage(
      CallId.NumberId(1),
      ErrorPayload(-32603, "Internal error", None)
    )
    val expected = """{"jsonrpc":"2.0","error":{"code":-32603,"message":"Internal error","data":null},"id":1}"""
    val result = writeToString(input.asJson)

    assert(result == expected, s"Expected: $expected, got: $result")
  }

}
