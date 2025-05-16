package jsonrpclib.validation;

import jsonrpclib.JsonNotificationTrait;
import jsonrpclib.JsonRPCTrait;
import jsonrpclib.JsonRequestTrait;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.validation.AbstractValidator;
import software.amazon.smithy.model.validation.ValidationEvent;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class JsonRpcOperationValidator extends AbstractValidator {

    @Override
    public List<ValidationEvent> validate(Model model) {
        return model.getServiceShapes().stream()
            .filter(service -> service.hasTrait(JsonRPCTrait.class))
            .flatMap(service -> validateService(model, service))
            .collect(Collectors.toList());
    }

    private Stream<ValidationEvent> validateService(Model model, ServiceShape service) {
        return service.getAllOperations().stream()
            .map(model::expectShape)
            .filter(Shape::isOperationShape)
            .map(shape -> shape.asOperationShape().orElseThrow())
            .filter(op -> !hasJsonRpcMethod(op))
            .map(op -> error(op, String.format(
                "Operation `%s` is part of service `%s` marked with @jsonRPC but is missing @jsonRequest or @jsonNotification.",
                op.getId(), service.getId())));
    }

    private boolean hasJsonRpcMethod(OperationShape op) {
        return op.hasTrait(JsonRequestTrait.ID) || op.hasTrait(JsonNotificationTrait.ID);
    }
}

