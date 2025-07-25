package jsonrpclib

import jsonrpclib.ModelUtils.assembleModel
import jsonrpclib.ModelUtils.eventsWithoutLocations
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.validation.Severity
import software.amazon.smithy.model.validation.ValidationEvent
import weaver._

object JsonPayloadValidatorSpec extends FunSuite {
  test("no error when jsonRpcPayload is used on the input, output or error structure's member") {

    assembleModel(
      """$version: "2"
        |namespace test
        |
        |use jsonrpclib#jsonRpc
        |use jsonrpclib#jsonRpcRequest
        |use jsonrpclib#jsonRpcPayload
        |
        |@jsonRpc
        |service MyService {
        |  operations: [OpA]
        |}
        |
        |@jsonRpcRequest("foo")
        |operation OpA {
        |  input: OpInput
        |  output: OpOutput
        |  errors: [OpError]
        |}
        |
        |structure OpInput {
        |  @jsonRpcPayload
        |  data: String  
        |}
        |
        |structure OpOutput {
        |  @jsonRpcPayload
        |  data: String  
        |}
        |
        |@error("client")
        |structure OpError {
        |  @jsonRpcPayload
        |  data: String  
        |}
        |
        |""".stripMargin
    ).unwrap()

    success
  }
  test("return an error when jsonRpcPayload is used in a nested structure") {
    val events = eventsWithoutLocations(
      assembleModel(
        """$version: "2"
        |namespace test
        |
        |use jsonrpclib#jsonRpc
        |use jsonrpclib#jsonRpcRequest
        |use jsonrpclib#jsonRpcPayload
        |
        |@jsonRpc
        |service MyService {
        |  operations: [OpA]
        |}
        |
        |@jsonRpcRequest("foo")
        |operation OpA {
        |  input: OpInput
        |}
        |
        |structure OpInput {
        |  data: NestedStructure  
        |}
        |
        |structure NestedStructure {
        |  @jsonRpcPayload
        |  data: String  
        |}
        |""".stripMargin
      )
    )

    val expected = ValidationEvent
      .builder()
      .id("jsonRpcPayload.OnlyTopLevel")
      .shapeId(ShapeId.fromParts("test", "NestedStructure", "data"))
      .severity(Severity.ERROR)
      .message(
        "Found an incompatible shape when validating the constraints of the `jsonrpclib#jsonRpcPayload` trait attached to `test#NestedStructure$data`: jsonRpcPayload can only be used on the top level of an operation input/output/error."
      )
      .build()

    assert(events.contains(expected))
  }

}
