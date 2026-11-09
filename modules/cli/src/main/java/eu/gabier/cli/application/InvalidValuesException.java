package eu.gabier.cli.application;

import eu.gabier.shared.error.domain.GeneratorException;

/**
 * The values given to run an operation (or to ask the guide) are not those it takes: one is missing, one is unknown, or
 * one is not of the kind its parameter holds. The problem names the operation and the parameter.
 */
final class InvalidValuesException extends GeneratorException {

    private InvalidValuesException(GeneratorExceptionBuilder builder) {
        super(builder);
    }

    /** A required value that was not given. */
    static InvalidValuesException missing(String operation, String parameter) {
        return of(operation, parameter, "The operation " + operation + " needs the value " + parameter);
    }

    /** A value that no parameter of the operation has. */
    static InvalidValuesException unknown(String operation, String parameter) {
        return of(operation, parameter, "The operation " + operation + " takes no value named " + parameter);
    }

    /** A value of another kind than its parameter's, or that its parameter cannot hold; {@code expected} says which. */
    static InvalidValuesException invalid(String operation, String parameter, String expected) {
        return of(
                operation,
                parameter,
                "The value " + parameter + " of the operation " + operation + " must " + expected);
    }

    private static InvalidValuesException of(String operation, String parameter, String message) {
        return new InvalidValuesException(GeneratorException.badRequest(OperationErrorKey.INVALID_VALUES)
                .message(message)
                .addParameter("operation", operation)
                .addParameter("parameter", parameter));
    }
}
