package eu.gabier.cli.infrastructure.primary;

import eu.gabier.cli.application.OperationResult;
import eu.gabier.cli.application.ResultText;
import eu.gabier.cli.domain.DaemonNotification;
import eu.gabier.cli.domain.DaemonRefusal;
import eu.gabier.cli.domain.Operation;
import eu.gabier.cli.domain.Operations;
import eu.gabier.protocol.Json;
import eu.gabier.protocol.JsonRpc;
import eu.gabier.protocol.Problem;
import eu.gabier.protocol.RpcNotification;
import eu.gabier.protocol.TerminalText;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

/**
 * {@code diagnostics <file>...} or {@code diagnostics --all}: one line per problem, {@code file:line:column: severity: message}, the files in the
 * order given, each as the user typed it, and the position from 1 as an editor shows it (the daemon counts from 0); with
 * {@code --json}, the result as the daemon gave it. The daemon gets the absolute paths, not cleaned, one to a line, so a
 * file name with a line break is a usage error, and asks the language server for all the files at once. The line is
 * neutralised as the text of a problem is ({@link TerminalText}): the message quotes source text and the file is typed,
 * and neither may drive the terminal or split the line. With {@code --all} no file is given: the answer is the files of
 * the project that the language server reports a problem for, and each is shown as the daemon names it, by its absolute
 * path.
 */
final class Diagnostics implements Capability {

    static final Command COMMAND = Command.called("diagnostics")
            .usage("""
                gabier [--json] diagnostics <file>...
                gabier [--json] diagnostics --all
                gabier [--json] diagnostics --watch <file>...
                gabier [--json] diagnostics --watch\
                """)
            .example("""
                $ gabier diagnostics src/main/java/demo/Broken.java
                src/main/java/demo/Broken.java:5:16: error: Type mismatch: cannot convert from String to int
                $ gabier diagnostics --all
                /work/demo/src/main/java/demo/Broken.java:5:16: error: Type mismatch: cannot convert from String to int
                $ gabier diagnostics --watch
                /work/demo/src/main/java/demo/Broken.java:5:16: error: Type mismatch: cannot convert from String to int
                /work/demo/src/main/java/demo/Broken.java: no problems\
                """)
            .asksTheDaemon(Operations.DIAGNOSTICS, Operations.DIAGNOSTICS_ALL, Operations.WATCH, Operations.WATCH_ALL)
            .withNotes("""
                --watch keeps running, starts by printing the problems as they are (a line "no problems" when there is
                none), then prints, after each edit that jdtls has built, the files whose problems changed: their problems
                in the format above, and for a file that became clean the line "<file>: no problems". It prints each line
                as soon as it has it. With no file it follows the project, with files it follows those files. If the
                daemon cannot follow the disk any more it says so with a line "gabier: notice: ..." and goes on. With
                --json it prints one {"files":[...]} object per line (the first is the problems as they are), and a notice
                as {"notice":"..."}. It ends when it is killed (SIGTERM, Ctrl-C: nothing is printed), with the problem of
                the daemon when the daemon ends the subscription, and with daemon-connection-failed when the daemon goes
                away. After an edit the project is read again, which takes a second or two on a large one; a file that breaks and
                is fixed between two of these reads is not shown.\
                """);

    private static final String ALL = "--all";
    private static final String WATCH = "--watch";
    private static final String NO_PROBLEMS = "no problems";

    @Override
    public Command command() {
        return COMMAND;
    }

    @Override
    public void run(Invocation invocation) {
        var watch = invocation.arguments().has(WATCH);
        var arguments = invocation.arguments().without(WATCH);
        if (invocation.arguments().has(ALL)) {
            arguments.without(ALL).exactly(0);
            if (watch) {
                throw followUntilItEnds(invocation, Operations.WATCH_ALL, Map.of(), Map.of());
            }
            show(invocation, List.of(), Operations.DIAGNOSTICS_ALL, Map.of());

            return;
        }
        if (watch && arguments.operands().isEmpty()) {
            throw followUntilItEnds(invocation, Operations.WATCH_ALL, Map.of(), Map.of());
        }
        arguments = arguments.atLeastOne();
        var shown = arguments;
        var files = IntStream.range(0, arguments.operands().size())
                .mapToObj(shown::file)
                .toList();
        if (files.stream()
                .anyMatch(file ->
                        file.toString().contains("\n") || file.toString().contains("\r"))) {
            throw UsageException.unsupportedArguments(shown.all());
        }
        var values = Map.<String, Object>of(
                "files", files.stream().map(Path::toString).toList());
        if (watch) {
            var typed = new HashMap<String, String>();
            IntStream.range(0, files.size())
                    .forEach(index -> typed.put(
                            files.get(index).toString(), shown.operands().get(index)));
            throw followUntilItEnds(invocation, Operations.WATCH, values, typed);
        }
        show(invocation, shown.operands(), Operations.DIAGNOSTICS, values);
    }

    private static void show(
            Invocation invocation, List<String> shown, Operation operation, Map<String, Object> values) {
        var result = invocation.operations().run(operation, values);
        var diagnostics = ((OperationResult.Diagnosed) result).diagnostics();
        if (invocation.json()) {
            invocation.out().println(diagnostics.toJson());

            return;
        }
        ResultText.lines(shown, diagnostics).forEach(invocation.out()::println);
    }

    /**
     * Prints the problems as they are, then what changes, each line as soon as it comes, until the daemon ends the
     * subscription (its problem is the refusal of the command) or goes away (the failure of the connection). What ended
     * it is returned, for the caller to throw: the command never ends any other way.
     */
    private static RuntimeException followUntilItEnds(
            Invocation invocation, Operation operation, Map<String, Object> values, Map<String, String> typed) {
        var followed = invocation.operations().follow(operation, values);
        var following = followed.following();
        try {
            if (invocation.json()) {
                print(invocation, followed.started().toJson());
            } else if (followed.started().files().isEmpty()) {
                print(invocation, NO_PROBLEMS);
            } else {
                ResultText.followed(typed, followed.started()).forEach(line -> print(invocation, line));
            }
            while (true) {
                DaemonNotification notification;
                try {
                    notification = following.next();
                } catch (RuntimeException goneAway) {
                    return goneAway;
                }
                var ended = tell(invocation, typed, notification);
                if (ended.isPresent()) {
                    return ended.get();
                }
            }
        } finally {
            following.close();
        }
    }

    /** Shows what the daemon tells; the refusal that ends the subscription when it is that, nothing when it is not. */
    private static Optional<RuntimeException> tell(
            Invocation invocation, Map<String, String> typed, DaemonNotification notification) {
        var message = new RpcNotification(notification.method(), notification.params());
        switch (notification.method()) {
            case JsonRpc.PROBLEMS -> {
                if (invocation.json()) {
                    print(invocation, message.problems().toJson());
                } else {
                    ResultText.followed(typed, message.problems()).forEach(line -> print(invocation, line));
                }
            }
            case JsonRpc.NOTICE ->
                print(
                        invocation,
                        invocation.json()
                                ? "{\"notice\":" + Json.quote(message.notice()) + "}"
                                : "gabier: notice: " + TerminalText.neutralise(message.notice()));
            case JsonRpc.ENDED -> {
                return Optional.of(refusal(message.ended()));
            }
            default -> {
                // A notification of a later version of the daemon: nothing to show.
            }
        }

        return Optional.empty();
    }

    private static DaemonRefusal refusal(Problem problem) {
        return new DaemonRefusal(
                problem.type(),
                problem.title(),
                problem.detail(),
                problem.key(),
                problem.parameters(),
                problem.category());
    }

    private static void print(Invocation invocation, String line) {
        invocation.out().println(line);
        invocation.out().flush();
    }
}
