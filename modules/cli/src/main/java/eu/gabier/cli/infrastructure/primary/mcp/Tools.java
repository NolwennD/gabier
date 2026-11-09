package eu.gabier.cli.infrastructure.primary.mcp;

import eu.gabier.cli.domain.Operation;
import eu.gabier.cli.domain.Operations;
import eu.gabier.cli.domain.Parameter;
import eu.gabier.protocol.Json;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The tools of the server, one for each operation it is given, generated from its descriptor and from nothing else: a
 * parameter added to an operation shows in the schema of its tool with no other file edited. The tool is named as the
 * operation, described by its summary and its notes, and takes the parameters of the operation as the properties of an
 * object (JSON Schema 2020-12, no other property allowed); a position is an integer from 1.
 */
final class Tools {

    /** The operations that write nothing, which a host may run with no confirmation (the hint {@code readOnlyHint}). */
    private static final Set<String> WRITE_NOTHING = Set.of("status", "diagnostics", "references", "actions");

    /** What a person asks the daemon for by the command line only: starting and stopping it is not a tool. */
    private static final Set<String> NOT_TOOLS = Set.of("start", "stop");

    private final List<Operation> operations;

    /**
     * The tools of these operations, in this order.
     *
     * @param operations the operations to list
     */
    Tools(List<Operation> operations) {
        this.operations = List.copyOf(operations);
    }

    /** The tools of the operations of the daemon, but those of the daemon's own life, {@code start} and {@code stop}. */
    static Tools served() {
        return new Tools(Operations.all().stream()
                .filter(operation -> !NOT_TOOLS.contains(operation.method()))
                .toList());
    }

    /** The tools as a JSON array. */
    String json() {
        return operations.stream().map(Tools::tool).collect(Collectors.joining(",", "[", "]"));
    }

    private static String tool(Operation operation) {
        var description =
                operation.notes().isEmpty() ? operation.summary() : operation.summary() + "\n\n" + operation.notes();
        var annotations = WRITE_NOTHING.contains(operation.method()) ? ",\"annotations\":{\"readOnlyHint\":true}" : "";

        return "{\"name\":" + Json.quote(operation.method()) + ",\"description\":" + Json.quote(description)
                + ",\"inputSchema\":" + schema(operation) + annotations + "}";
    }

    private static String schema(Operation operation) {
        var parameters = operation.parameters();
        var properties = parameters.isEmpty()
                ? ""
                : parameters.stream()
                        .map(parameter -> Json.quote(parameter.name()) + ":" + property(parameter))
                        .collect(Collectors.joining(",", ",\"properties\":{", "}"));
        var required = parameters.stream()
                .filter(Parameter::required)
                .map(parameter -> Json.quote(parameter.name()))
                .toList();
        var requiredMembers =
                required.isEmpty() ? "" : required.stream().collect(Collectors.joining(",", ",\"required\":[", "]"));

        return "{\"type\":\"object\"" + properties + requiredMembers + ",\"additionalProperties\":false}";
    }

    private static String property(Parameter parameter) {
        var shape = switch (parameter.type()) {
            case STRING -> "\"type\":\"string\"";
            case INTEGER -> "\"type\":\"integer\"";
            case POSITION -> "\"type\":\"integer\",\"minimum\":1";
            case BOOLEAN -> "\"type\":\"boolean\"";
            case ARRAY -> "\"type\":\"array\",\"items\":{\"type\":\"string\"}";
        };

        return "{" + shape + ",\"description\":" + Json.quote(parameter.description()) + "}";
    }
}
