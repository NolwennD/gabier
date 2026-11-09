package eu.gabier.cli.infrastructure.primary.mcp;

import eu.gabier.cli.application.AskTheGuide;
import eu.gabier.cli.application.GuideText;
import eu.gabier.cli.application.OperationResult;
import eu.gabier.cli.application.Problems;
import eu.gabier.cli.application.ProjectOperations;
import eu.gabier.cli.application.ResultText;
import eu.gabier.cli.domain.DaemonRefusal;
import eu.gabier.cli.domain.Guide;
import eu.gabier.cli.domain.Operation;
import eu.gabier.cli.domain.Operations;
import eu.gabier.protocol.DaemonStatus;
import eu.gabier.protocol.Json;
import eu.gabier.protocol.Problem;
import eu.gabier.protocol.ProblemMapper;
import eu.gabier.shared.error.domain.GeneratorException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * The calls of the tools: the arguments of a call are the values of an operation, which the one entry point of the
 * application runs ({@link ProjectOperations}), in the project of the files it names, and the answer is the text that the command line prints for the same
 * result and, beside it, the result as the daemon gave it. A call that does not work (a refusal of the daemon, a value
 * that does not fit the operation, a daemon that cannot be reached) is a result that says so ({@code isError}) and holds
 * the problem and the pointer to {@code gabier help} that the command line gives, so that the agent reads it and tries
 * again; a call that is no call (a tool that does not exist, arguments that are not an object) is an error of the
 * protocol. No failure of an operation ends the server. The tool {@code guide}, which is no operation, is a call of its
 * own: it asks the guide, never the daemon.
 */
final class ToolCalls {

    private static final String NO_PROBLEMS = "no problems";

    private final Tools tools;
    private final ProjectOperations operations;
    private final Function<Operation, String> commandOf;
    private final Guide guide;
    private final PrintStream err;

    /**
     * The calls of the tools.
     *
     * @param tools the tools that can be called
     * @param backend what the tools run: the operations and the guide
     * @param err where a call that failed unexpectedly is logged, with its cause
     */
    ToolCalls(Tools tools, McpServer.Backend backend, PrintStream err) {
        this.tools = tools;
        this.operations = backend.operations();
        this.commandOf = backend.commandOf();
        this.guide = backend.guide();
        this.err = err;
    }

    /** What a call is, once it is read: an answer already, or an operation that asks the daemon and waits for it. */
    sealed interface Prepared {}

    /**
     * A call that needs no wait: the guide, or values that do not fit the operation, which are refused before any
     * daemon is asked.
     *
     * @param members the members of the result
     */
    record Done(String members) implements Prepared {}

    /**
     * An operation that is run once the daemon answers.
     *
     * @param operation the operation
     * @param run what asks the daemon
     * @param file the file as the call gave it, for the text of {@code diagnostics}
     * @param dryRun whether the call asked to write nothing
     * @param project the folder of the project whose daemon is asked
     */
    record Waiting(Operation operation, Supplier<OperationResult> run, String file, boolean dryRun, Path project)
            implements Prepared {

        /** Whether the daemon is asked: {@code status} asks a daemon that runs, and starts none, so it never waits. */
        boolean asksDaemon() {
            return !operation.equals(Operations.STATUS);
        }
    }

    /**
     * Reads the call the params name: the checks that need no daemon are made here, so that what is wrong in itself is
     * answered at once.
     *
     * @return the call, ready to run
     * @throws McpFailure when the name is not a text or names no tool, or the arguments are not an object
     */
    Prepared prepare(Map<String, @Nullable Object> params) {
        if (!(params.get("name") instanceof String name)) {
            throw McpFailure.invalidParams("name must be a text");
        }
        if (tools.isGuide(name)) {
            return new Done(askTheGuide(arguments(params)));
        }
        var operation = tools.find(name).orElseThrow(() -> McpFailure.invalidParams("Unknown tool: " + name));
        var arguments = arguments(params);
        var values = given(arguments);
        var file = arguments.get("file") instanceof String given ? given : "";
        var dryRun = Boolean.TRUE.equals(values.get("dryRun"));
        try {
            var prepared = operations.prepare(operation, values);

            return new Waiting(operation, prepared.run(), file, dryRun, prepared.project());
        } catch (RuntimeException invalid) {
            return new Done(refused(operation, invalid));
        }
    }

    /**
     * Runs the call and waits for the daemon: the members of the result, which is a failed call when the operation
     * does not work. It never throws, an {@code Error} of the process (a class or a library that is missing) included:
     * what it meets is the result of this call, and the server goes on.
     */
    String complete(Waiting waiting) {
        try {
            return success(waiting.run().get(), waiting.file(), waiting.dryRun());
        } catch (RuntimeException | Error failure) {
            return refused(waiting.operation(), failure);
        }
    }

    /**
     * Whether the call waits for the warm-up, which starts the daemon of the project the server was launched in: a call
     * that asks the daemon of another project meets no start of its own to wait for, and starts its daemon itself.
     */
    boolean awaitsTheWarmUp(Waiting waiting) {
        return waiting.asksDaemon() && waiting.project().equals(operations.launched());
    }

    /** The members of the result of a call that was given up after this long. */
    String timedOut(Duration bound) {
        return failed("call", CallTimedOutException.after(bound));
    }

    /** Where the daemon of the project of the call is, asked on a connection of its own: none when it does not answer. */
    Optional<DaemonStatus> status(Waiting waiting) {
        try {
            return Optional.of(operations.statusOf(waiting.project()));
        } catch (RuntimeException notRunning) {
            return Optional.empty();
        }
    }

    private String refused(Operation operation, Throwable failure) {
        if (failure instanceof DaemonRefusal refusal) {
            var problem = Problems.of(refusal);

            return failure(problem, Problems.pointer(problem, commandOf.apply(operation)));
        }

        return failed(
                operation.method(),
                failure instanceof RuntimeException unexpected ? unexpected : new IllegalStateException(failure));
    }

    /**
     * The guide asked a question: the text of its answer, in the words of {@code help --for} but for the pointer to a way
     * of reading the whole guide that only the command line has, and with the commands it names written as the tools
     * that do them. A task that nothing in the guide answers is an answer, not an error. The guide asks no daemon.
     */
    private String askTheGuide(Map<String, @Nullable Object> arguments) {
        try {
            var task = AskTheGuide.taskIn(given(arguments));
            var text = GuideText.answer(task, AskTheGuide.answer(task, guide));

            return "\"content\":[{\"type\":\"text\",\"text\":" + Json.quote(ToolWords.of(text, tools))
                    + "}],\"isError\":false";
        } catch (RuntimeException failure) {
            return failed(Tools.GUIDE, failure);
        }
    }

    /** A call that did not work, as a result that says so: the problem, which only a failure gabier knows tells in full. */
    private String failed(String tool, RuntimeException failure) {
        logIfUnexpected(tool, failure);

        return failure(ProblemMapper.toProblem(failure), Optional.empty());
    }

    /**
     * What the agent reads of a failure that gabier does not know is the generic problem, which keeps paths and secrets
     * out of it; the person who runs the server reads the whole failure here, on the standard error. A failure that
     * gabier knows (a refusal, a value that does not fit) says everything in its problem.
     */
    private void logIfUnexpected(String tool, RuntimeException failure) {
        if (failure instanceof GeneratorException) {
            return;
        }
        err.println("gabier mcp: the call of " + tool + " failed");
        failure.printStackTrace(err);
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

    /** The arguments that are given: one that is null is not. */
    private static Map<String, Object> given(Map<String, @Nullable Object> arguments) {
        var given = new LinkedHashMap<String, Object>();
        arguments.forEach((name, value) -> {
            if (value != null) {
                given.put(name, value);
            }
        });

        return given;
    }

    private static String success(OperationResult result, String file, boolean dryRun) {
        return switch (result) {
            case OperationResult.Reported reported ->
                answer(ResultText.lines(reported.status()), reported.status().toJson());
            case OperationResult.Diagnosed diagnosed ->
                answer(problemsOf(file, diagnosed), diagnosed.diagnostics().toJson());
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

    /**
     * The problems of the file as the command line prints them, which are none for a file with no problem: a text block
     * with nothing in it is what hosts and the Claude API reject, so the call says {@code no problems}.
     */
    private static List<String> problemsOf(String file, OperationResult.Diagnosed diagnosed) {
        var lines = ResultText.lines(file, diagnosed.diagnostics());

        return lines.isEmpty() ? List.of(NO_PROBLEMS) : lines;
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
