package jsonrpclib.validation;

import jsonrpclib.JsonNotificationTrait;
import jsonrpclib.JsonRPCTrait;
import jsonrpclib.JsonRequestTrait;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.traits.StringTrait;
import software.amazon.smithy.model.validation.AbstractValidator;
import software.amazon.smithy.model.validation.ValidationEvent;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class UniqueJsonRpcMethodNamesValidator extends AbstractValidator {

    @Override
    public List<ValidationEvent> validate(Model model) {
        return model.getShapesWithTrait(JsonRPCTrait.class).stream()
            .flatMap(service -> validateService(service.asServiceShape().orElseThrow(), model))
            .collect(Collectors.toList());
    }

    private Stream<ValidationEvent> validateService(ServiceShape service, Model model) {
        Map<String, List<OperationShape>> methodsToOps = service.getAllOperations().stream()
            .map(model::expectShape)
            .map(shape -> shape.asOperationShape().orElseThrow())
            .flatMap(op -> getJsonRpcMethodName(op).map(name -> Map.entry(name, op)).stream())
            .collect(Collectors.groupingBy(
                Map.Entry::getKey,
                Collectors.mapping(Map.Entry::getValue, Collectors.toList())
            ));

        // Emit a validation error for each method name that occurs more than once
        return methodsToOps.entrySet().stream()
            .filter(entry -> entry.getValue().size() > 1)
            .flatMap(entry -> entry.getValue().stream()
                .map(op ->
                    error(service, String.format(
                        "Duplicate JSON-RPC method name `%s` in service `%s`. It is used by: %s",
                        entry.getKey(),
                        service.getId(),
                        entry.getValue().stream()
                            .map(OperationShape::getId)
                            .map(Object::toString)
                            .collect(Collectors.joining(", "))
                    )))
            );
    }

    private Optional<String> getJsonRpcMethodName(OperationShape operation) {
        return operation.getTrait(JsonRequestTrait.class)
            .map(StringTrait::getValue)
            .or(() -> operation.getTrait(JsonNotificationTrait.class).map(StringTrait::getValue));
    }
}

