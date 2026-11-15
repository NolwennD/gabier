package eu.gabier.cli.infrastructure.primary;

import eu.gabier.cli.application.OperationResult;
import eu.gabier.cli.application.ResultText;
import eu.gabier.cli.domain.Operations;
import java.time.Duration;
import java.util.Map;

/**
 * {@code test [--timeout=<seconds>] [<test-class>...]}: runs the test classes given, or with none the tests that what
 * changed on disk affects, and prints how they went, as text or, with {@code --json}, as the daemon gave it. Tests that
 * fail are an answer: the command prints them and ends with the exit code 3, no other command ends with it.
 */
final class RunTests implements Capability {

    static final Command COMMAND = Command.called("test")
            .usage("""
                gabier [--json] test [--timeout=<seconds>] <test-class>...
                gabier [--json] test [--timeout=<seconds>]\
                """)
            .example("""
                $ gabier test demo.LedgerTest
                ran 3 tests: 2 passed, 1 failed, 0 skipped
                FAILED demo.LedgerTest withdrawsMoreThanTheBalance
                  org.opentest4j.AssertionFailedError: expected: 10 but was: 5
                  at demo.Ledger.withdraw(Ledger.java:17)
                $ echo $?
                3\
                """)
            .asksTheDaemon(Operations.TEST, Operations.TEST_CHANGED)
            .withNotes("""
                With no class, the tests of what changed on disk since the last answer of the daemon are run, the ones\
                 that affected-tests lists with no file; with classes, the classes named, as the names affected-tests\
                 prints. The tests print nothing: the answer is the counts, then each failed test.
                --timeout=<seconds> is the time the tests may take in all, a whole number from 1; without it, 300\
                 seconds.
                The exit code is 0 when no test failed, 3 when some failed (the report is on the standard output, as\
                 with 0), and 2 or 1 when the call is refused, as for the other commands. With --json the standard\
                 output is {"ran":3,"passed":2,"failed":1,"skipped":0,"failures":[{"class":...,"method":...,\
                "exception":...,"message":...,"where":...}]}: method and where are missing when there is none.\
                """);

    static final String TIMEOUT = "--timeout=";
    static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(5);

    @Override
    public Command command() {
        return COMMAND;
    }

    @Override
    public void run(Invocation invocation) {
        var arguments = invocation.arguments().withoutOptions(TIMEOUT);
        var seconds = seconds(invocation.arguments());
        var classes = arguments.operands();
        if (classes.stream().anyMatch(RunTests::isNotAClassName)) {
            throw UsageException.unsupportedArguments(arguments.all());
        }
        var result = classes.isEmpty()
                ? invocation.operations().run(Operations.TEST_CHANGED, Map.of("timeout", seconds))
                : invocation.operations().run(Operations.TEST, Map.of("classes", classes, "timeout", seconds));
        var ran = ((OperationResult.Ran) result).ran();
        if (invocation.json()) {
            invocation.out().println(ran.toJson());
        } else {
            ResultText.lines(ran).forEach(invocation.out()::println);
        }
        if (ran.failed() > 0) {
            throw new TestsFailed();
        }
    }

    /** The words that no class is named by: the option {@code --dry-run}, which the command has not, or a line break. */
    private static boolean isNotAClassName(String word) {
        return word.equals(Arguments.DRY_RUN) || word.contains("\n") || word.contains("\r");
    }

    private static long seconds(Arguments arguments) {
        var given = arguments.valueOf(TIMEOUT);
        if (given.isEmpty()) {
            return DEFAULT_TIMEOUT.toSeconds();
        }
        try {
            var seconds = Integer.parseInt(given.get());
            if (seconds >= 1) {
                return seconds;
            }
        } catch (NumberFormatException notANumber) {
            // The same refusal as a number below one.
        }

        throw UsageException.invalidTimeout(arguments.all(), given.get());
    }
}
