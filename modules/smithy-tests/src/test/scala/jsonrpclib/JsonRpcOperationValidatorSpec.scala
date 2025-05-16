package jsonrpclib

import jsonrpclib.ModelUtils.assembleModel
import jsonrpclib.ModelUtils.eventsWithoutLocations
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.validation.Severity
import software.amazon.smithy.model.validation.ValidationEvent
import weaver._

object JsonRpcOperationValidatorSpec extends FunSuite {
  test("no error when all operations in @jsonRPC service are properly annotated") {
    assembleModel(
      """$version: "2"
        |namespace test
        |
        |use jsonrpclib#jsonRPC
        |use jsonrpclib#jsonRequest
        |use jsonrpclib#jsonNotification
        |
        |@jsonRPC
        |service MyService {
        |  operations: [OpA, OpB]
        |}
        |
        |@jsonRequest("methodA")
        |operation OpA {}
        |
        |@jsonNotification("methodB")
        |operation OpB {
        |  output: unit
        |}
        |""".stripMargin
    )
    success
  }

  test("return an error when a @jsonRPC service has an operation without @jsonRequest or @jsonNotification") {
    val events = eventsWithoutLocations(
      assembleModel(
        """$version: "2"
          |namespace test
          |
          |use jsonrpclib#jsonRPC
          |use jsonrpclib#jsonRequest
          |
          |@jsonRPC
          |service MyService {
          |  operations: [GoodOp, BadOp]
          |}
          |
          |@jsonRequest("good")
          |operation GoodOp {}
          |
          |operation BadOp {} // ❌ missing jsonRequest or jsonNotification
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
          "Operation `test#BadOp` is part of service `test#MyService` marked with @jsonRPC but is missing @jsonRequest or @jsonNotification."
        )
        .build()

    assert(events.contains(expected))
  }
}
