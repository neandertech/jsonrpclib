package jsonrpclib.validation;

import jsonrpclib.JsonRpcNotificationTrait;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.validation.AbstractValidator;
import software.amazon.smithy.model.validation.ValidationEvent;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Validates that operations marked with @jsonNotification don't have any
 * output.
 */
public class JsonNotificationOutputValidator extends AbstractValidator {

    @Override
    public List<ValidationEvent> validate(Model model) {
        return model.getShapesWithTrait(JsonRpcNotificationTrait.ID).stream().flatMap(op -> {
            ShapeId outputShapeId = op.asOperationShape().orElseThrow().getOutputShape();
            var outputShape = model.expectShape(outputShapeId);
            if (outputShape.asStructureShape().map(s -> !s.members().isEmpty()).orElse(true)) {
                return Stream.of(error(op, String.format(
                    "Operation marked as @jsonNotification must not return anything, but found `%s`.", outputShapeId)));
            } else {
                return Stream.empty();
            }
        }).collect(Collectors.toUnmodifiableList());
    }
}
