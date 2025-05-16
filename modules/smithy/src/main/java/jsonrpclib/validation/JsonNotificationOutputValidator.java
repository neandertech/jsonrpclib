package jsonrpclib.validation;

import jsonrpclib.JsonNotificationTrait;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.validation.AbstractValidator;
import software.amazon.smithy.model.validation.ValidationEvent;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Validates that operations marked with @jsonNotification don't have any output.
 */
public class JsonNotificationOutputValidator extends AbstractValidator {

    @Override
    public List<ValidationEvent> validate(Model model) {
        return model.shapes(OperationShape.class)
            .filter(op -> op.hasTrait(JsonNotificationTrait.ID))
            .flatMap(op -> {
                ShapeId outputShapeId = op.getOutputShape();
                if (outputShapeId != ShapeId.from("smithy.api#Unit")) {
                    return Stream.of(error(op, String.format(
                        "Operation `%s` marked as @jsonNotification must not have output defined, but found `%s`.",
                        op.getId(), outputShapeId)));
                } else {
                    return Stream.empty();
                }
            })
            .collect(Collectors.toUnmodifiableList());
    }
}
