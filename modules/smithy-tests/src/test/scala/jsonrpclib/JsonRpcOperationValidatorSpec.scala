package jsonrpclib

import jsonrpclib.ModelUtils.assembleModel
import jsonrpclib.ModelUtils.eventsWithoutLocations
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.validation.Severity
import software.amazon.smithy.model.validation.ValidationEvent
import weaver._

object JsonRpcOperationValidatorSpec extends FunSuite {
  test("no error when all operations in @jsonRpc service are properly annotated") {
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
        |@jsonRpcRequest("methodA")
        |operation OpA {}
        |
        |@jsonRpcNotification("methodB")
        |operation OpB {
        |  output: unit
        |}
        |""".stripMargin
    )
    success
  }

  test("return an error when a @jsonRpc service has an operation without @jsonRpcRequest or @jsonRpcNotification") {
    val events = eventsWithoutLocations(
      assembleModel(
        """$version: "2"
          |namespace test
          |
          |use jsonrpclib#jsonRpc
          |use jsonrpclib#jsonRpcRequest
          |
          |@jsonRpc
          |service MyService {
          |  operations: [GoodOp, BadOp]
          |}
          |
          |@jsonRpcRequest("good")
          |operation GoodOp {}
          |
          |operation BadOp {} // ❌ missing jsonRpcRequest or jsonRpcNotification
          |""".stripMargin
      )
    )

    val expected =
      ValidationEvent
        .builder()
        .id("JsonRpcOperation")
        .shapeId(ShapeId.fromParts("test", "BadOp"))
        .severity(Severity.ERROR)
        .message(
          "Operation is part of service `test#MyService` marked with @jsonRpc but is missing @jsonRpcRequest or @jsonRpcNotification."
        )
        .build()

    assert(events.contains(expected))
  }
}
