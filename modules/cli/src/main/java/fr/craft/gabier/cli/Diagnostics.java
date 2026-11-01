package fr.craft.gabier.cli;

import fr.craft.gabier.protocol.FileDiagnostics;
import fr.craft.gabier.protocol.TerminalText;
import java.util.Map;

/**
 * {@code diagnostics <file>}: one line per problem, {@code file:line:column: severity: message}, the file as the user
 * typed it and the position from 1 as an editor shows it (the daemon counts from 0); with {@code --json}, the result as
 * the daemon gave it. The daemon gets the absolute path, not cleaned. The line is neutralised as the text of a problem
 * is ({@link TerminalText}): the message quotes source text and the file is typed, and neither may drive the terminal or
 * split the line.
 */
final class Diagnostics implements Capability {

    @Override
    public Command command() {
        return Command.DIAGNOSTICS;
    }

    @Override
    public void run(Invocation invocation) {
        var arguments = invocation.arguments().exactly(1);
        var result = invocation.ask(
                command().commandName(), Map.of("file", arguments.file(0).toString()));
        var diagnostics = FileDiagnostics.of(result.result());
        if (invocation.json()) {
            invocation.out().println(diagnostics.toJson());

            return;
        }
        var file = arguments.operand(0);
        diagnostics
                .diagnostics()
                .forEach(diagnosis -> invocation
                        .out()
                        .println(TerminalText.neutralise(file + ":" + (diagnosis.line() + 1) + ":"
                                + (diagnosis.column() + 1) + ": "
                                + diagnosis.severity().text() + ": "
                                + diagnosis.message())));
    }
}
