package eu.gabier.cli.infrastructure.primary.mcp;

import eu.gabier.cli.application.OperationResult;
import eu.gabier.cli.application.OperationRunner;
import eu.gabier.cli.application.Problems;
import eu.gabier.cli.application.ResultText;
import eu.gabier.cli.domain.DaemonRefusal;
import eu.gabier.cli.domain.Operation;
import eu.gabier.protocol.Json;
import eu.gabier.protocol.Problem;
import eu.gabier.protocol.ProblemMapper;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * The calls of the tools: the arguments of a call are the values of an operation, which the one entry point of the
 * application runs ({@link OperationRunner}), and the answer is the text that the command line prints for the same
 * result and, beside it, the result as the daemon gave it. A call that does not work (a refusal of the daemon, a value
 * that does not fit the operation, a daemon that cannot be reached) is a result that says so ({@code isError}) and holds
 * the problem and the pointer to {@code gabier help} that the command line gives, so that the agent reads it and tries
 * again; a call that is no call (a tool that does not exist, arguments that are not an object) is an error of the
 * protocol. No failure of an operation ends the server.
 */
final class ToolCalls {

    /** The values that name files: a path of the project, which the daemon reads from its own folder, not the server's. */
    private static final Set<String> FILES = Set.of("file", "files");

    private final Tools tools;
    private final OperationRunner operations;
    private final Function<Operation, String> commandOf;

    /**
     * The calls of the tools.
     *
     * @param tools the tools that can be called
     * @param operations how an operation is run
     * @param commandOf the word of the command line that runs an operation, for the pointer to its help
     */
    ToolCalls(Tools tools, OperationRunner operations, Function<Operation, String> commandOf) {
        this.tools = tools;
        this.operations = operations;
        this.commandOf = commandOf;
    }

    /**
     * Calls the tool the params name with their arguments.
     *
     * @return the members of the result: {@code content}, {@code structuredContent} and {@code isError}
     * @throws McpFailure when the name is not a text or names no tool, or the arguments are not an object
     */
    String call(Map<String, @Nullable Object> params) {
        if (!(params.get("name") instanceof String name)) {
            throw McpFailure.invalidParams("name must be a text");
        }
        var operation = tools.find(name).orElseThrow(() -> McpFailure.invalidParams("Unknown tool: " + name));
        var arguments = arguments(params);
        var values = values(arguments);
        var file = arguments.get("file") instanceof String given ? given : "";
        var dryRun = Boolean.TRUE.equals(values.get("dryRun"));
        try {
            return success(operations.run(operation, values), file, dryRun);
        } catch (DaemonRefusal refusal) {
            var problem = Problems.of(refusal);

            return failure(problem, Problems.pointer(problem, commandOf.apply(operation)));
        } catch (RuntimeException failure) {
            return failure(ProblemMapper.toProblem(failure), Optional.empty());
        }
    }

    private static Map<String, @Nullable Object> arguments(Map<String, @Nullable Object> params) {
        var given = params.get("arguments");
        if (given instanceof Map<?, ?> members) {
            var arguments = new LinkedHashMap<String, @Nullable Object>();
            members.forEach((name, value) -> arguments.put(String.valueOf(name), value));

            return arguments;
        }
        if (params.containsKey("arguments")) {
            throw McpFailure.invalidParams("arguments must be an object");
        }

        return Map.of();
    }

    /** The values of the operation: a value that is null is not given, and a path is made absolute. */
    private static Map<String, Object> values(Map<String, @Nullable Object> arguments) {
        var values = new LinkedHashMap<String, Object>();
        arguments.forEach((name, value) -> {
            if (value != null) {
                values.put(name, FILES.contains(name) ? absolute(value) : value);
            }
        });

        return values;
    }

    private static Object absolute(Object value) {
        if (value instanceof String file) {
            return absolute(file);
        }
        if (value instanceof List<?> files) {
            return files.stream()
                    .map(file -> file instanceof String text ? absolute(text) : file)
                    .toList();
        }

        return value;
    }

    /** The path from the folder of the server, which the daemon cannot guess; a text that is no path is left to it. */
    private static String absolute(String file) {
        try {
            return Path.of(file).toAbsolutePath().toString();
        } catch (InvalidPathException notAPath) {
            return file;
        }
    }

    private static String success(OperationResult result, String file, boolean dryRun) {
        return switch (result) {
            case OperationResult.Reported reported ->
                answer(ResultText.lines(reported.status()), reported.status().toJson());
            case OperationResult.Diagnosed diagnosed ->
                answer(
                        ResultText.lines(file, diagnosed.diagnostics()),
                        diagnosed.diagnostics().toJson());
            case OperationResult.Referenced referenced ->
                answer(ResultText.lines(referenced.found()), referenced.found().toJson());
            case OperationResult.Offered offered ->
                answer(ResultText.lines(offered.offered()), offered.offered().toJson());
            case OperationResult.Changed changed ->
                answer(
                        ResultText.report(changed.changed(), dryRun, true),
                        changed.changed().toJson());
        };
    }

    private static String answer(List<String> lines, String structuredContent) {
        return members(String.join("\n", lines), structuredContent, false);
    }

    /** The problem as the command line shows it on the standard error, then the result as {@code --json} gives it. */
    private static String failure(Problem problem, Optional<String> pointer) {
        var lines = new ArrayList<String>();
        lines.add(problem.toText());
        pointer.ifPresent(lines::add);

        return members(String.join("\n", lines), pointer.map(problem::toJson).orElseGet(problem::toJson), true);
    }

    private static String members(String text, String structuredContent, boolean isError) {
        return "\"content\":[{\"type\":\"text\",\"text\":" + Json.quote(text) + "}],\"structuredContent\":"
                + structuredContent + ",\"isError\":" + isError;
    }
}
