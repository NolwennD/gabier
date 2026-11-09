package eu.gabier.cli.infrastructure.primary;

import eu.gabier.cli.application.OperationResult;
import eu.gabier.cli.application.ResultText;
import eu.gabier.cli.domain.Operations;
import eu.gabier.protocol.TerminalText;
import java.util.Map;

/**
 * {@code diagnostics <file>}: one line per problem, {@code file:line:column: severity: message}, the file as the user
 * typed it and the position from 1 as an editor shows it (the daemon counts from 0); with {@code --json}, the result as
 * the daemon gave it. The daemon gets the absolute path, not cleaned. The line is neutralised as the text of a problem
 * is ({@link TerminalText}): the message quotes source text and the file is typed, and neither may drive the terminal or
 * split the line.
 */
final class Diagnostics implements Capability {

    static final Command COMMAND = Command.called("diagnostics")
            .usage("gabier [--json] diagnostics <file>")
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
        var arguments = invocation.arguments().exactly(1);
        var result = invocation
                .operations()
                .run(Operations.DIAGNOSTICS, Map.of("file", arguments.file(0).toString()));
        var diagnostics = ((OperationResult.Diagnosed) result).diagnostics();
        if (invocation.json()) {
            invocation.out().println(diagnostics.toJson());

            return;
        }
        ResultText.lines(arguments.operand(0), diagnostics).forEach(invocation.out()::println);
    }
}
