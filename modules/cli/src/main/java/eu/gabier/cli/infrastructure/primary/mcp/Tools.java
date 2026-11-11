package eu.gabier.cli.infrastructure.primary.mcp;

import eu.gabier.cli.domain.Operation;
import eu.gabier.cli.domain.Operations;
import eu.gabier.cli.domain.Parameter;
import eu.gabier.protocol.Json;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The tools of the server, one for each operation it is given, generated from its descriptor and from nothing else: a
 * parameter added to an operation shows in the schema of its tool with no other file edited. The tool is named as the
 * operation, described by its summary and its notes, and takes the parameters of the operation as the properties of an
 * object (JSON Schema 2020-12, no other property allowed); a position is an integer from 1. The served tools end with
 * {@code guide}, which asks the guide of the cli and is no operation: it is the one tool declared here, by hand.
 */
final class Tools {

    /**
     * The tool that answers a question to the guide of the cli. It is no operation of the daemon (it asks no daemon),
     * so it has no descriptor in {@code Operations}: it is declared here, in the adapter, and nowhere below it.
     */
    static final String GUIDE = "guide";

    /** The operations that write nothing, which a host may run with no confirmation (the hint {@code readOnlyHint}). */
    private static final Set<String> WRITE_NOTHING =
            Set.of("status", "diagnostics", "diagnostics-all", "references", "actions", "affected-tests");

    /**
     * What a person asks the daemon for by the command line only: starting and stopping it is not a tool, and a tool
     * answers once, so following the problems, which the daemon tells as they come, is not one either.
     */
    private static final Set<String> NOT_TOOLS = Set.of("start", "stop", "watch", "watch-all");

    private static final String GUIDE_DESCRIPTION = """
        Ask which tool does a task on Java, in English, before editing the code by hand: it answers the one to three \
        entries of the guide that fit the task, each with its confidence and what to do, or says that nothing matches. \
        A move that the language server does not offer is answered as by hand, with the reason, so that you do not look \
        for it. The answers are written as command lines: a command is the tool of the same name, the words in angle \
        brackets are its parameters, and a parameter written name=true is that boolean parameter set to true.\
        """;

    private static final String GUIDE_TASK =
            "the task, in English, such as: turn this string concatenation into a text block";

    private final List<Operation> operations;
    private final boolean guided;

    /**
     * The tools of these operations, in this order, and no other.
     *
     * @param operations the operations to list
     */
    Tools(List<Operation> operations) {
        this(operations, false);
    }

    private Tools(List<Operation> operations, boolean guided) {
        this.operations = List.copyOf(operations);
        this.guided = guided;
    }

    /**
     * The tools of the operations of the daemon, but those of the daemon's own life, {@code start} and {@code stop},
     * and then the guide.
     */
    static Tools served() {
        return new Tools(
                Operations.all().stream()
                        .filter(operation -> !NOT_TOOLS.contains(operation.method()))
                        .toList(),
                true);
    }

    /** The operation of the tool of this name, if there is one; the guide is no operation. */
    Optional<Operation> find(String name) {
        return operations.stream()
                .filter(operation -> operation.method().equals(name))
                .findFirst();
    }

    /** Whether this is the name of the tool that asks the guide, which these tools hold. */
    boolean isGuide(String name) {
        return guided && name.equals(GUIDE);
    }

    /** Whether a tool of this name is among these, the guide included. */
    boolean has(String name) {
        return find(name).isPresent() || isGuide(name);
    }

    /** The tools as a JSON array. */
    String json() {
        return Stream.concat(operations.stream().map(Tools::tool), guided ? Stream.of(guide()) : Stream.empty())
                .collect(Collectors.joining(",", "[", "]"));
    }

    private static String guide() {
        return "{\"name\":" + Json.quote(GUIDE) + ",\"description\":" + Json.quote(GUIDE_DESCRIPTION)
                + ",\"inputSchema\":{\"type\":\"object\",\"properties\":{\"task\":{\"type\":\"string\","
                + "\"description\":" + Json.quote(GUIDE_TASK)
                + "}},\"required\":[\"task\"],\"additionalProperties\":false},"
                + "\"annotations\":{\"readOnlyHint\":true}}";
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
            case STRING, PATH -> "\"type\":\"string\"";
            case INTEGER -> "\"type\":\"integer\"";
            case POSITION -> "\"type\":\"integer\",\"minimum\":1";
            case BOOLEAN -> "\"type\":\"boolean\"";
            case ARRAY, PATHS -> "\"type\":\"array\",\"items\":{\"type\":\"string\"}";
        };

        return "{" + shape + ",\"description\":" + Json.quote(parameter.description()) + "}";
    }
}
