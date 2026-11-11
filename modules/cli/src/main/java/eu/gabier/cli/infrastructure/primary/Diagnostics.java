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
 * {@code diagnostics <file>...}: one line per problem, {@code file:line:column: severity: message}, the files in the
 * order given, each as the user typed it, and the position from 1 as an editor shows it (the daemon counts from 0); with
 * {@code --json}, the result as the daemon gave it. The daemon gets the absolute paths, not cleaned, one to a line, so a
 * file name with a line break is a usage error, and asks the language server for all the files at once. The line is
 * neutralised as the text of a problem is ({@link TerminalText}): the message quotes source text and the file is typed,
 * and neither may drive the terminal or split the line.
 */
final class Diagnostics implements Capability {

    static final Command COMMAND = Command.called("diagnostics")
            .usage("gabier [--json] diagnostics <file>...")
            .example("""
                $ gabier diagnostics src/main/java/demo/Broken.java
                src/main/java/demo/Broken.java:5:16: error: Type mismatch: cannot convert from String to int\
                """)
            .asksTheDaemon(Operations.DIAGNOSTICS);

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
                        Operations.DIAGNOSTICS,
                        Map.of("files", files.stream().map(Path::toString).toList()));
        var diagnostics = ((OperationResult.Diagnosed) result).diagnostics();
        if (invocation.json()) {
            invocation.out().println(diagnostics.toJson());

            return;
        }
        ResultText.lines(arguments.operands(), diagnostics).forEach(invocation.out()::println);
    }
}
