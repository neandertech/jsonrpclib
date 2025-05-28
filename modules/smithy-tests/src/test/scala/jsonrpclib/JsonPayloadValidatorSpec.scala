package jsonrpclib

import jsonrpclib.ModelUtils.assembleModel
import jsonrpclib.ModelUtils.eventsWithoutLocations
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.validation.Severity
import software.amazon.smithy.model.validation.ValidationEvent
import weaver._

object JsonPayloadValidatorSpec extends FunSuite {
  test("no error when jsonPayload is used on the input, output or error structure's member") {

    assembleModel(
      """$version: "2"
        |namespace test
        |
        |use jsonrpclib#jsonRPC
        |use jsonrpclib#jsonRequest
        |use jsonrpclib#jsonPayload
        |
        |@jsonRPC
        |service MyService {
        |  operations: [OpA]
        |}
        |
        |@jsonRequest("foo")
        |operation OpA {
        |  input: OpInput
        |  output: OpOutput
        |  errors: [OpError]
        |}
        |
        |structure OpInput {
        |  @jsonPayload
        |  data: String  
        |}
        |
        |structure OpOutput {
        |  @jsonPayload
        |  data: String  
        |}
        |
        |@error("client")
        |structure OpError {
        |  @jsonPayload
        |  data: String  
        |}
        |
        |""".stripMargin
    ).unwrap()

    success
  }
  test("return an error when jsonPayload is used in a nested structure") {
    val events = eventsWithoutLocations(
      assembleModel(
        """$version: "2"
        |namespace test
        |
        |use jsonrpclib#jsonRPC
        |use jsonrpclib#jsonRequest
        |use jsonrpclib#jsonPayload
        |
        |@jsonRPC
        |service MyService {
        |  operations: [OpA]
        |}
        |
        |@jsonRequest("foo")
        |operation OpA {
        |  input: OpInput
        |}
        |
        |structure OpInput {
        |  data: NestedStructure  
        |}
        |
        |structure NestedStructure {
        |  @jsonPayload
        |  data: String  
        |}
        |""".stripMargin
      )
    )

    val expected = ValidationEvent
      .builder()
      .id("jsonPayload.OnlyTopLevel")
      .shapeId(ShapeId.fromParts("test", "NestedStructure", "data"))
      .severity(Severity.ERROR)
      .message(
        "Found an incompatible shape when validating the constraints of the `jsonrpclib#jsonPayload` trait attached to `test#NestedStructure$data`: jsonPayload can only be used on the top level of an operation input/output/error."
      )
      .build()

    assert(events.contains(expected))
  }

}
