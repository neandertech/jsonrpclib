package jsonrpclib

import jsonrpclib.ModelUtils.assembleModel
import jsonrpclib.ModelUtils.eventsWithoutLocations
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.validation.Severity
import software.amazon.smithy.model.validation.ValidationEvent
import weaver._

object UniqueJsonRpcMethodNamesValidatorSpec extends FunSuite {
  test("no error when all jsonRpc method names are unique within a service") {

    assembleModel(
      """$version: "2"
        |namespace test
        |
        |use jsonrpclib#jsonRpc
        |use jsonrpclib#jsonRpcRequest
        |use jsonrpclib#jsonRpcNotification
        |
        |@jsonRpc
        |service MyService {
        |  operations: [OpA, OpB]
        |}
        |
        |@jsonRpcRequest("foo")
        |operation OpA {}
        |
        |@jsonRpcNotification("bar")
        |operation OpB {}
        |""".stripMargin
    ).unwrap()

    success
  }
  test("return an error when two operations use the same jsonRpc method name in a service") {
    val events = eventsWithoutLocations(
      assembleModel(
        """$version: "2"
          |namespace test
          |
          |use jsonrpclib#jsonRpc
          |use jsonrpclib#jsonRpcRequest
          |use jsonrpclib#jsonRpcNotification
          |
          |@jsonRpc
          |service MyService {
          |  operations: [OpA, OpB]
          |}
          |
          |@jsonRpcRequest("foo")
          |operation OpA {}
          |
          |@jsonRpcNotification("foo")
          |operation OpB {} // duplicate method name "foo"
          |""".stripMargin
      )
    )

    val expected = ValidationEvent
      .builder()
      .id("UniqueJsonRpcMethodNames")
      .shapeId(ShapeId.fromParts("test", "MyService"))
      .severity(Severity.ERROR)
      .message(
        "Duplicate JSON-RPC method name `foo` in service `test#MyService`. It is used by: test#OpA, test#OpB"
      )
      .build()

    assert(events.contains(expected))
  }

  test("no error if two services use the same operation") {
    assembleModel(
      """$version: "2"
          |namespace test
          |
          |use jsonrpclib#jsonRpc
          |use jsonrpclib#jsonRpcRequest
          |use jsonrpclib#jsonRpcNotification
          |
          |@jsonRpc
          |service MyService {
          |  operations: [OpA]
          |}
          |
          |@jsonRpc
          |service MyOtherService {
          |  operations: [OpA]
          |}
          |
          |@jsonRpcRequest("foo")
          |operation OpA {}
          |
          |""".stripMargin
    ).unwrap()
    success
  }

  test("no error if two services use the same operation") {
    assembleModel(
      """$version: "2"
        |namespace test
        |
        |use jsonrpclib#jsonRpcRequest
        |use jsonrpclib#jsonRpcNotification
        |
        |
        |service NonJsonRpcService {
        |  operations: [OpA]
        |}
        |
        |@jsonRpcRequest("foo")
        |operation OpA {}
        |
        |@jsonRpcNotification("foo")
        |operation OpB {} // duplicate method name "foo"
        |""".stripMargin
    ).unwrap()
    success
  }

}
