package eu.gabier.cli.application;

import eu.gabier.cli.application.InvalidValuesException.OperationName;
import eu.gabier.cli.domain.Daemon;
import eu.gabier.cli.domain.Operation;
import eu.gabier.cli.domain.Parameter;
import eu.gabier.protocol.ChangedFiles;
import eu.gabier.protocol.CodeActions;
import eu.gabier.protocol.DaemonStatus;
import eu.gabier.protocol.FileDiagnostics;
import eu.gabier.protocol.FoundReferences;
import eu.gabier.shared.error.domain.GeneratorException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Supplier;

/**
 * The one way to run an operation, whatever front end asks for it: it checks the named values against the descriptor of
 * the operation, turns them into what the daemon reads, asks the daemon and decodes the answer. A front end reads its
 * own words into values, calls {@link #run}, and shows the result.
 *
 * <p>The values are the kinds the parameters declare ({@code Parameter.Type}): a text, a whole number, a position
 * (a whole number counted from 1), {@code true} or {@code false}, a list of texts. A whole number is a {@code Number}
 * with no fraction, whatever its class ({@code Integer}, {@code Long}, what a JSON reader gives). The daemon reads
 * texts: a position is sent from 0, a boolean as {@code true} or {@code false}, and a list as its elements one to a
 * line, which is why an element has no line break and is not empty. A refusal of the daemon goes through as it came
 * ({@code DaemonRefusal}).
 */
public final class OperationRunner {

    private final Daemon daemon;

    /**
     * The runner of the operations of this daemon.
     *
     * @param daemon the way to the daemon of the project
     */
    public OperationRunner(Daemon daemon) {
        this.daemon = daemon;
    }

    /**
     * Runs the operation with these values, by the name of their parameter.
     *
     * @throws GeneratorException (the key {@code operation-invalid-values}, which any front end turns into a problem
     *     as it does a refusal of the daemon) when a required value is missing, a value is unknown, or one is not of
     *     the kind its parameter holds; a value that is unknown is said before one that is missing
     * @throws IllegalArgumentException when the operation is not one that asks the daemon for a result to decode
     */
    public OperationResult run(Operation operation, Map<String, Object> values) {
        return prepare(operation, values).get();
    }

    /**
     * Checks the values and gives what asks the daemon, which is not asked yet: a front end that must not wait for the
     * daemon to refuse a call that is wrong in itself checks first, and asks after.
     *
     * @throws GeneratorException when {@link #run} would, before it asks the daemon
     * @throws IllegalArgumentException when the operation is not one that asks the daemon for a result to decode
     */
    public Supplier<OperationResult> prepare(Operation operation, Map<String, Object> values) {
        var kind = kindOf(operation);
        var parameters = parameters(operation, values);

        return () -> ask(operation, kind, parameters);
    }

    private OperationResult ask(Operation operation, Kind kind, Map<String, String> parameters) {
        var result = (kind == Kind.REPORTED
                        ? daemon.askRunning(operation.method(), false)
                        : daemon.ask(operation.method(), parameters))
                .result();

        return switch (kind) {
            case REPORTED -> new OperationResult.Reported(DaemonStatus.of(result));
            case DIAGNOSED -> new OperationResult.Diagnosed(FileDiagnostics.of(result));
            case REFERENCED -> new OperationResult.Referenced(FoundReferences.of(result));
            case OFFERED -> new OperationResult.Offered(CodeActions.of(result));
            case CHANGED -> new OperationResult.Changed(ChangedFiles.of(result));
        };
    }

    /** What an operation answers, which decides how the members of its result are decoded. */
    private enum Kind {
        REPORTED,
        DIAGNOSED,
        REFERENCED,
        OFFERED,
        CHANGED
    }

    private static Kind kindOf(Operation operation) {
        return switch (operation.method()) {
            case "status" -> Kind.REPORTED;
            case "diagnostics" -> Kind.DIAGNOSED;
            case "references" -> Kind.REFERENCED;
            case "actions" -> Kind.OFFERED;
            case "rename",
                    "rename-token",
                    "organize-imports",
                    "cleanup",
                    "extract-variable",
                    "extract-method",
                    "extract-constant",
                    "extract-field",
                    "move-type-to-file",
                    "move",
                    "inline",
                    "create",
                    "apply-action" -> Kind.CHANGED;
            default ->
                throw new IllegalArgumentException("The operation " + operation.method() + " is not run through run");
        };
    }

    /** The parameters of the daemon: every value as a text, the positions from 0. */
    static Map<String, String> parameters(Operation operation, Map<String, Object> values) {
        for (var name : new TreeSet<>(values.keySet())) {
            if (!operation.parameterNames().contains(name)) {
                throw InvalidValuesException.unknown(OperationName.of(operation), name);
            }
        }
        var parameters = new HashMap<String, String>();
        for (var parameter : operation.parameters()) {
            var value = values.get(parameter.name());
            if (value == null) {
                if (parameter.required()) {
                    throw InvalidValuesException.missing(OperationName.of(operation), parameter.name());
                }
            } else {
                parameters.put(parameter.name(), text(operation, parameter, value));
            }
        }

        return parameters;
    }

    private static String text(Operation operation, Parameter parameter, Object value) {
        return switch (parameter.type()) {
            case STRING -> textOf(operation, parameter, value);
            case PATH -> {
                var path = textOf(operation, parameter, value);
                if (path.isEmpty()) {
                    throw InvalidValuesException.invalid(OperationName.of(operation), parameter.name(), "not be empty");
                }
                yield path;
            }
            case INTEGER -> String.valueOf(wholeNumber(operation, parameter, value));
            case POSITION -> String.valueOf(zeroBased(operation, parameter, value));
            case BOOLEAN -> {
                if (value instanceof Boolean flag) {
                    yield flag.toString();
                }
                throw InvalidValuesException.invalid(OperationName.of(operation), parameter.name(), "be true or false");
            }
            case ARRAY, PATHS -> String.join("\n", elements(operation, parameter, value));
        };
    }

    private static String textOf(Operation operation, Parameter parameter, Object value) {
        if (value instanceof String text) {
            return text;
        }

        throw InvalidValuesException.invalid(OperationName.of(operation), parameter.name(), "be a text");
    }

    /** A position as the daemon counts it: the whole number the front end gave, counted from 1, less one. */
    private static long zeroBased(Operation operation, Parameter parameter, Object value) {
        var number = wholeNumber(operation, parameter, value);
        if (number < 1) {
            throw InvalidValuesException.invalid(
                    OperationName.of(operation), parameter.name(), "be a position, from 1");
        }

        return number - 1;
    }

    /**
     * A whole number as a front end gives it: an {@code Integer}, a {@code Long}, or what a JSON reader gives (a {@code
     * BigDecimal}, a {@code Double}) when it has no fraction ({@code 6.0} is 6).
     */
    private static long wholeNumber(Operation operation, Parameter parameter, Object value) {
        if (value instanceof Number number) {
            try {
                return new BigDecimal(number.toString()).longValueExact();
            } catch (NumberFormatException | ArithmeticException notWhole) {
                // A fraction, a number out of range, NaN or an infinity: the refusal below.
            }
        }

        throw InvalidValuesException.invalid(OperationName.of(operation), parameter.name(), "be a whole number");
    }

    private static List<String> elements(Operation operation, Parameter parameter, Object value) {
        if (!(value instanceof List<?> list) || !list.stream().allMatch(String.class::isInstance)) {
            throw InvalidValuesException.invalid(OperationName.of(operation), parameter.name(), "be a list of texts");
        }
        var texts = list.stream().map(String.class::cast).toList();
        if (texts.stream().anyMatch(text -> text.contains("\n") || text.contains("\r"))) {
            throw InvalidValuesException.invalid(
                    OperationName.of(operation), parameter.name(), "not have a line break in an element");
        }
        if (texts.stream().anyMatch(String::isEmpty)) {
            throw InvalidValuesException.invalid(
                    OperationName.of(operation), parameter.name(), "not have an empty element");
        }

        return texts;
    }
}
