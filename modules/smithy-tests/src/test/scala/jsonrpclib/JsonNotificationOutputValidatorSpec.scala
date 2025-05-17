package jsonrpclib

import jsonrpclib.ModelUtils.assembleModel
import jsonrpclib.ModelUtils.eventsWithoutLocations
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.validation.Severity
import software.amazon.smithy.model.validation.ValidationEvent
import weaver._

object JsonNotificationOutputValidatorSpec extends FunSuite {
  test("no error when a @jsonNotification operation has unit output") {
    assembleModel(
      """$version: "2"
        |namespace test
        |
        |use jsonrpclib#jsonNotification
        |
        |@jsonNotification("notify")
        |operation NotifySomething {
        |}
        |""".stripMargin
    )
    success
  }
  test("return an error when a @jsonNotification operation does not have unit output") {
    val events = eventsWithoutLocations(
      assembleModel(
        """$version: "2"
          |namespace test
          |
          |use jsonrpclib#jsonNotification
          |
          |@jsonNotification("notify")
          |operation NotifySomething {
          |  output:={
          |    message: String
          |  }
          |}
          |
          |""".stripMargin
      )
    )

    val expected = ValidationEvent
      .builder()
      .id("JsonNotificationOutput")
      .shapeId(ShapeId.fromParts("test", "NotifySomething"))
      .severity(Severity.ERROR)
      .message(
        "Operation marked as @jsonNotification must not return anything, but found `test#NotifySomethingOutput`."
      )
      .build()

    assert(events.contains(expected))
  }

}
