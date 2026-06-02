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

    expect.same(
      rawMessage,
      RawMessage(jsonrpc = "2.0", result = Some(None), id = Some(NumberId(3)))
    ) &&
    expect.same(rawMessage.toMessage, Right(ResponseMessage(NumberId(3), Payload.NullPayload))) &&
    expect.same(
      invalidRawMessage,
      RawMessage(jsonrpc = "2.0", result = None, id = Some(NumberId(3)))
    ) &&
    expect(invalidRawMessage.toMessage.isLeft, invalidRawMessage.toMessage.toString)
  }

  test("request with omitted or null params decodes to empty object") {
    // JSON-RPC 2.0 §4: the `params` member MAY be omitted.
    val omitted = readFromString[Json]("""{"jsonrpc":"2.0","method":"m","id":1}""")
      .as[RawMessage]
      .flatMap(_.toMessage.left.map(e => new RuntimeException(e.getMessage)))
      .fold(throw _, identity)

    val nulled = readFromString[Json]("""{"jsonrpc":"2.0","method":"m","params":null,"id":2}""")
      .as[RawMessage]
      .flatMap(_.toMessage.left.map(e => new RuntimeException(e.getMessage)))
      .fold(throw _, identity)

    val notif = readFromString[Json]("""{"jsonrpc":"2.0","method":"m"}""")
      .as[RawMessage]
      .flatMap(_.toMessage.left.map(e => new RuntimeException(e.getMessage)))
      .fold(throw _, identity)

    expect.same(omitted, InputMessage.RequestMessage("m", NumberId(1), Payload.Empty)) &&
    expect.same(nulled, InputMessage.RequestMessage("m", NumberId(2), Payload.Empty)) &&
    expect.same(notif, InputMessage.NotificationMessage("m", Payload.Empty))
  }

  test("request message serialization elides empty params") {
    val input: Message = InputMessage.RequestMessage("my/method", CallId.NumberId(1), Payload.Empty)
    val expected = """{"jsonrpc":"2.0","method":"my/method","id":1}"""
    val result = writeToString(input.asJson)

    expect(result == expected, s"Expected: $expected, got: $result")
  }

  test("request message serialization with params") {
    val input: Message = InputMessage.RequestMessage(
      "greet",
      CallId.NumberId(0),
      Payload(Json.obj("name" -> Json.fromString("Client")))
    )
    val expected = """{"jsonrpc":"2.0","method":"greet","params":{"name":"Client"},"id":0}"""
    val result = writeToString(input.asJson)

    expect(result == expected, s"Expected: $expected, got: $result")
  }

  test("notification message serialization elides empty params") {
    val input: Message = InputMessage.NotificationMessage("my/method", Payload.Empty)
    val expected = """{"jsonrpc":"2.0","method":"my/method"}"""
    val result = writeToString(input.asJson)

    expect(result == expected, s"Expected: $expected, got: $result")
  }

  test("response message serialization") {
    val input: Message = OutputMessage.ResponseMessage(CallId.NumberId(1), Payload.NullPayload)
    val expected = """{"jsonrpc":"2.0","id":1,"result":null}"""
    val result = writeToString(input.asJson)

    expect(result == expected, s"Expected: $expected, got: $result")
  }

  test("response message serialization with nested results") {
    val input: Message =
      OutputMessage.ResponseMessage(CallId.NumberId(1), Payload(Json.obj("result" -> Json.fromInt(1))))
    val expected = """{"jsonrpc":"2.0","id":1,"result":{"result":1}}"""
    val result = writeToString(input.asJson)

    expect(result == expected, s"Expected: $expected, got: $result")
  }

  test("error message serialization") {
    val input: Message = OutputMessage.ErrorMessage(
      CallId.NumberId(1),
      ErrorPayload(-32603, "Internal error", None)
    )
    val expected = """{"jsonrpc":"2.0","error":{"code":-32603,"message":"Internal error","data":null},"id":1}"""
    val result = writeToString(input.asJson)

    expect(result == expected, s"Expected: $expected, got: $result")
  }

  test("input message codec decodes requests and notifications") {
    def decodeInput(s: String) = readFromString[Json](s).as[InputMessage]

    expect.same(
      decodeInput("""{"jsonrpc":"2.0","method":"greet","id":1}"""),
      Right(InputMessage.RequestMessage("greet", CallId.NumberId(1), Payload.Empty))
    ) &&
    expect.same(
      decodeInput("""{"jsonrpc":"2.0","method":"ping"}"""),
      Right(InputMessage.NotificationMessage("ping", Payload.Empty))
    ) &&
    expect(decodeInput("""{"jsonrpc":"2.0","id":1,"result":null}""").isLeft)
  }

  test("input message codec serializes via Message encoder") {
    val input: InputMessage = InputMessage.RequestMessage("greet", CallId.NumberId(0), Payload.Empty)
    val expected = """{"jsonrpc":"2.0","method":"greet","id":0}"""

    expect(writeToString(input.asJson) == expected)
  }

  test("output message codec decodes responses and errors") {
    def decodeOutput(s: String) = readFromString[Json](s).as[OutputMessage]

    expect.same(
      decodeOutput("""{"jsonrpc":"2.0","id":1,"result":null}"""),
      Right(OutputMessage.ResponseMessage(CallId.NumberId(1), Payload.NullPayload))
    ) &&
    expect.same(
      decodeOutput("""{"jsonrpc":"2.0","error":{"code":-32603,"message":"Internal error","data":null},"id":1}"""),
      Right(OutputMessage.ErrorMessage(CallId.NumberId(1), ErrorPayload(-32603, "Internal error", None)))
    ) &&
    expect(decodeOutput("""{"jsonrpc":"2.0","method":"greet","id":1}""").isLeft)
  }

}
