package jsonrpclib

import software.amazon.smithy.model.validation.ValidatedResult
import software.amazon.smithy.model.validation.ValidationEvent
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.SourceLocation

import scala.jdk.CollectionConverters._

private object ModelUtils {

  def assembleModel(text: String): ValidatedResult[Model] = {
    Model
      .assembler()
      .discoverModels()
      .addUnparsedModel(
        "test.smithy",
        text
      )
      .assemble()
  }

  def eventsWithoutLocations(result: ValidatedResult[?]): List[ValidationEvent] = {
    if (!result.isBroken) sys.error("Expected a broken result")
    result.getValidationEvents.asScala.toList.map(e => e.toBuilder.sourceLocation(SourceLocation.NONE).build())
  }
}
