package eu.gabier.cli.infrastructure.primary;

import eu.gabier.cli.application.OperationResult;
import eu.gabier.cli.application.ResultText;
import eu.gabier.cli.domain.Operation;
import eu.gabier.cli.domain.Operations;
import eu.gabier.protocol.JsonRpc;
import eu.gabier.protocol.SelectedTests;
import eu.gabier.protocol.TerminalText;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * {@code affected-tests <file>...}: the test classes to run again after a change to these Java files, one per line, as
 * the binary names of the classes, in the order of the names; with {@code --json}, the result as the daemon gave it. The
 * daemon gets the absolute paths, not cleaned, one to a line, so a file name with a line break is a usage error. A
 * change that affects no test says {@code no affected tests} with the exit code 0. In text, every line is neutralised as
 * the text of a problem is ({@link TerminalText}): a class name comes from a class file, which is hostile text.
 */
final class AffectedTests implements Capability {

    static final Command COMMAND = Command.called("affected-tests")
            .usage("""
                gabier [--json] affected-tests <file>...
                gabier [--json] affected-tests
                gabier [--json] affected-tests --watch\
                """)
            .example("""
                $ gabier affected-tests src/main/java/demo/Ledger.java
                demo.LedgerTest
                demo.app.BankTest
                $ gabier affected-tests
                demo.LedgerTest
                $ gabier affected-tests --watch
                demo.LedgerTest
                demo.app.BankTest\
                """)
            .asksTheDaemon(
                    Operations.AFFECTED_TESTS, Operations.AFFECTED_TESTS_CHANGED, Operations.WATCH_AFFECTED_TESTS)
            .withNotes("""
                With no file, the tests of what changed on disk since the last answer of the daemon are listed (it\
                 follows the files itself, so none is named): the answer forgets them, a second call answers "no\
                 affected tests", and the daemon of the project, not the command, keeps them.
                The files are typed as paths, from the working directory; a file that is not a Java source is left out.\
                 A change that affects no test prints "no affected tests" and exits with 0; with --json, an empty list.
                --watch keeps running: it starts by printing the tests of the changes so far (what a call with no file\
                 prints, and nothing when no test is affected), then prints, after each series of changes on disk that\
                 jdtls has built, the tests they affect, one per line, as soon as it has them; a change that affects no\
                 test prints nothing. It takes no file and no --dry-run. A change that cannot be answered (a changed file\
                 does not compile) is told on the standard error as the problem object of its refusal, the command goes\
                 on, the changes are kept and their tests are listed once the code compiles. If the daemon cannot follow\
                 the disk any more it says so with a line "gabier: notice: ..." and goes on. With --json it prints one\
                 {"tests":[...]} object per line and a notice as {"notice":"..."}. It ends when it is killed (SIGTERM,\
                 Ctrl-C: nothing is printed), with the problem of the daemon when the daemon ends the subscription, and\
                 with daemon-connection-failed when the daemon goes away. What it prints is used up: the changes belong to\
                 the daemon, so a call with no file is not told again what this one was told, and the one that is told\
                 first uses them up; two watchers are told the same tests.
                The command lists the tests, it runs none: the names are classes, which a test runner takes as they are.\
                """);

    private static final String WATCH = "--watch";

    @Override
    public Command command() {
        return COMMAND;
    }

    @Override
    public void run(Invocation invocation) {
        if (invocation.arguments().has(WATCH)) {
            invocation.arguments().without(WATCH).exactly(0);

            throw followUntilItEnds(invocation);
        }
        if (invocation.arguments().operands().isEmpty()) {
            show(invocation, Operations.AFFECTED_TESTS_CHANGED, Map.of());

            return;
        }
        var arguments = invocation.arguments().atLeastOne();
        var indexes = IntStream.range(0, arguments.operands().size());
        List<Path> files = indexes.mapToObj(arguments::file).toList();
        if (files.stream()
                .anyMatch(file ->
                        file.toString().contains("\n") || file.toString().contains("\r"))) {
            throw UsageException.unsupportedArguments(arguments.all());
        }
        show(
                invocation,
                Operations.AFFECTED_TESTS,
                Map.of("files", files.stream().map(Path::toString).toList()));
    }

    private static void show(Invocation invocation, Operation operation, Map<String, Object> values) {
        var result = invocation.operations().run(operation, values);
        var selected = ((OperationResult.Selected) result).selected();
        if (invocation.json()) {
            invocation.out().println(selected.toJson());

            return;
        }
        ResultText.lines(selected).forEach(invocation.out()::println);
    }

    /**
     * Prints the tests of the changes so far, then the tests of each change, each line as soon as it comes, until the
     * daemon ends the subscription (its problem is the refusal of the command) or goes away (the failure of the
     * connection). What ended it is returned, for the caller to throw: the command never ends any other way.
     */
    private static RuntimeException followUntilItEnds(Invocation invocation) {
        var followed = invocation.operations().follow(Operations.WATCH_AFFECTED_TESTS, Map.of());
        var following = followed.following();
        try {
            tell(invocation, ((OperationResult.Selected) followed.started()).selected());

            return Streaming.readUntilItEnds(invocation, following, message -> {
                switch (message.method()) {
                    case JsonRpc.AFFECTED_TESTS -> tell(invocation, message.affectedTests());
                    case JsonRpc.REFUSAL -> {
                        var refusal = message.refusal();
                        invocation.err().println(invocation.json() ? refusal.toJson() : refusal.toText());
                        invocation.err().flush();
                    }
                    default -> {
                        // A notification of a later version of the daemon: nothing to show.
                    }
                }
            });
        } finally {
            following.close();
        }
    }

    /** Prints the tests as the call does, each line as soon as it is there; nothing when no test is affected. */
    private static void tell(Invocation invocation, SelectedTests selected) {
        if (selected.tests().isEmpty()) {
            return;
        }
        if (invocation.json()) {
            Streaming.print(invocation, selected.toJson());
        } else {
            ResultText.lines(selected).forEach(line -> Streaming.print(invocation, line));
        }
    }
}
