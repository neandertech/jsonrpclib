package jsonrpclib.validation;

import jsonrpclib.JsonNotificationTrait;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.validation.AbstractValidator;
import software.amazon.smithy.model.validation.ValidationEvent;
import software.amazon.smithy.model.selector.Selector;
import software.amazon.smithy.model.shapes.Shape;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import software.amazon.smithy.model.validation.*;

public class JsonPayloadValidator extends AbstractValidator {
	// Precompile the selector
	private static final Selector DISALLOWED_LOCATIONS_SELECTOR = Selector
			.parse("$allowedShapes(:root(operation -[input, output, error]-> structure > member))"
					+ "[trait|jsonrpclib#jsonPayload]:not(:in(${allowedShapes}))");

	@Override
	public List<ValidationEvent> validate(Model model) {
		// Run the selector against the model
		Set<Shape> invalidShapes = DISALLOWED_LOCATIONS_SELECTOR.select(model);

		// Emit a validation event for each violation
		return invalidShapes.stream().map(shape -> error(shape,
				"`@jsonPayload` can only be used on top-level members of operation input/output/error structures."))
				.collect(Collectors.toList());
	}
}
