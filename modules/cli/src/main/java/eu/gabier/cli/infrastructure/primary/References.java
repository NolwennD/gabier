package eu.gabier.cli.infrastructure.primary;

import eu.gabier.cli.application.OperationResult;
import eu.gabier.cli.application.ResultText;
import eu.gabier.cli.domain.Operations;
import eu.gabier.protocol.TerminalText;
import java.util.Map;

/**
 * {@code references [--include-declaration] <file> <line>:<column>}: where the symbol at the position is used, one line
 * per place, {@code file:line:column: source}, the position from 1 as an editor shows it (the daemon counts from 0) and
 * the source line without its indentation. The files are written as the daemon gives them, absolute. The declaration is
 * listed apart, under {@code declaration:}, only when it is asked for; a symbol nothing uses is said ({@code no
 * references}) with the exit code 0. With {@code --json}, the result as the daemon gave it, the positions from 0 and the
 * source lines exact. In text, every line is neutralised as the text of a problem is ({@link TerminalText}): a source
 * line and a file name are hostile text. {@code --dry-run} is not an option of this command, it is a usage error.
 */
final class References implements Capability {

    static final String INCLUDE_DECLARATION = "--include-declaration";

    static final Command COMMAND = Command.called("references")
            .usage("gabier [--json] references [--include-declaration] <file> <line>:<column>")
            .example("""
                $ gabier references src/main/java/demo/Ledger.java 11:17
                /work/demo/src/main/java/demo/app/Bank.java:8:16: ledger.deposit(10);
                /work/demo/src/test/java/demo/LedgerTest.java:6:16: ledger.deposit(20);\
                """)
            .asksTheDaemon(Operations.REFERENCES)
            .withNotes("""
                One line per place: file:line:column: source, the file absolute, the source line without its\
                 indentation. With --json the positions are counted from 0 and the source lines are exact.
                A symbol nothing uses prints "no references" and exits with 0; with --json, two empty lists.\
                """);

    @Override
    public Command command() {
        return COMMAND;
    }

    @Override
    public void run(Invocation invocation) {
        var arguments = invocation.arguments();
        var operands = arguments.without(INCLUDE_DECLARATION).exactly(2);
        var position = operands.position(1);
        var result = invocation
                .operations()
                .run(
                        Operations.REFERENCES,
                        Map.of(
                                "file", operands.file(0).toString(),
                                "line", position.line(),
                                "column", position.column(),
                                "includeDeclaration", arguments.has(INCLUDE_DECLARATION)));
        var found = ((OperationResult.Referenced) result).found();
        if (invocation.json()) {
            invocation.out().println(found.toJson());

            return;
        }
        ResultText.lines(found).forEach(invocation.out()::println);
    }
}
