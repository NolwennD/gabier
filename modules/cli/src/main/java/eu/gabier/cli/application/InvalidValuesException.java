package eu.gabier.cli.application;

import eu.gabier.cli.domain.Operation;
import eu.gabier.shared.error.domain.GeneratorException;

/**
 * The values given to run an operation (or to ask the guide) are not those it takes: one is missing, one is unknown, or
 * one is not of the kind its parameter holds. The problem names the operation and the parameter.
 */
final class InvalidValuesException extends GeneratorException {

    /**
     * What the values were given to: the name of an operation, or of the guide, which is no operation. A type of its own,
     * so that the name of the operation and the name of the parameter, two texts, are never swapped.
     */
    record OperationName(String value) {

        /** The name of the operation of the daemon. */
        static OperationName of(Operation operation) {
            return new OperationName(operation.method());
        }
    }

    private InvalidValuesException(GeneratorExceptionBuilder builder) {
        super(builder);
    }

    /** A required value that was not given. */
    static InvalidValuesException missing(OperationName operation, String parameter) {
        return of(operation, parameter, "The operation " + operation.value() + " needs the value " + parameter);
    }

    /** A value that no parameter of the operation has. */
    static InvalidValuesException unknown(OperationName operation, String parameter) {
        return of(operation, parameter, "The operation " + operation.value() + " takes no value named " + parameter);
    }

    /** A value of another kind than its parameter's, or that its parameter cannot hold; {@code expected} says which. */
    static InvalidValuesException invalid(OperationName operation, String parameter, String expected) {
        return of(
                operation,
                parameter,
                "The value " + parameter + " of the operation " + operation.value() + " must " + expected);
    }

    private static InvalidValuesException of(OperationName operation, String parameter, String message) {
        return new InvalidValuesException(GeneratorException.badRequest(OperationErrorKey.INVALID_VALUES)
                .message(message)
                .addParameter("operation", operation.value())
                .addParameter("parameter", parameter));
    }
}
