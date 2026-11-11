package eu.gabier.cli.infrastructure.primary;

import eu.gabier.cli.application.OperationResult;
import eu.gabier.cli.application.ResultText;
import eu.gabier.cli.domain.Operations;
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
            .usage("gabier [--json] affected-tests <file>...")
            .example("""
                $ gabier affected-tests src/main/java/demo/Ledger.java
                demo.LedgerTest
                demo.app.BankTest\
                """)
            .asksTheDaemon(Operations.AFFECTED_TESTS)
            .withNotes("""
                The files are typed as paths, from the working directory; a file that is not a Java source is left out.\
                 A change that affects no test prints "no affected tests" and exits with 0; with --json, an empty list.
                The command lists the tests, it runs none: the names are classes, which a test runner takes as they are.\
                """);

    @Override
    public Command command() {
        return COMMAND;
    }

    @Override
    public void run(Invocation invocation) {
        var arguments = invocation.arguments().atLeastOne();
        var indexes = IntStream.range(0, arguments.operands().size());
        List<Path> files = indexes.mapToObj(arguments::file).toList();
        if (files.stream()
                .anyMatch(file ->
                        file.toString().contains("\n") || file.toString().contains("\r"))) {
            throw UsageException.unsupportedArguments(arguments.all());
        }
        var result = invocation
                .operations()
                .run(
                        Operations.AFFECTED_TESTS,
                        Map.of("files", files.stream().map(Path::toString).toList()));
        var selected = ((OperationResult.Selected) result).selected();
        if (invocation.json()) {
            invocation.out().println(selected.toJson());

            return;
        }
        ResultText.lines(selected).forEach(invocation.out()::println);
    }
}
