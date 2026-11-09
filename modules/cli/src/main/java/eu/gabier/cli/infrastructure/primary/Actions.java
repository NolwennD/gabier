package eu.gabier.cli.infrastructure.primary;

import eu.gabier.cli.application.OperationResult;
import eu.gabier.cli.application.ResultText;
import eu.gabier.cli.domain.Operations;
import eu.gabier.protocol.TerminalText;
import java.util.Map;

/**
 * {@code actions <file> <line>:<column> <line>:<column>}: what the language server offers on a range, one line per
 * action, {@code <number> <kind> <title>}, in the order the server gives, followed by {@code -> gabier <command>} when
 * the daemon names the command of gabier that does the same (an extraction, an inlining). Each position is from 1 and
 * goes to the daemon from 0, the end excluded; a point is the same position twice. An action the server gives no kind
 * is listed with {@code -} in its place. A range with nothing offered says {@code no actions} and exits with 0. With
 * {@code --json}, the result as the daemon gave it ({@code {"actions":[{"kind":...,"title":...,"command":...}]}}, the
 * member {@code command} only on an action that has one); the number is the place in that list, from 1.
 *
 * <p>The kinds and the titles are the server's text, hostile as any: in text, every line is neutralised as the text of
 * a problem is ({@link TerminalText}). {@code --dry-run} is not an option of this command, it is a usage error.
 */
final class Actions implements Capability {

    static final Command COMMAND = Command.called("actions")
            .usage("gabier [--json] actions <file> <line>:<column> <line>:<column>")
            .example("""
                $ gabier actions src/main/java/demo/Offers.java 4:5 4:9
                1 quickfix Import 'List' (java.util)
                2 refactor.extract.variable Extract to local variable -> gabier extract-variable
                ...\
                """)
            .asksTheDaemon(Operations.ACTIONS)
            .withNotes("""
                Each position is typed line:column. A range with nothing offered prints "no actions" and exits with 0.
                An action that a command of gabier also does is followed by "-> gabier <command>"; with --json it has a\
                 member "command", only when there is one. To apply one, give its title to gabier apply-action with the\
                 same range.\
                """);

    @Override
    public Command command() {
        return COMMAND;
    }

    @Override
    public void run(Invocation invocation) {
        var operands = invocation.arguments().exactly(3);
        var start = operands.position(1);
        var end = operands.position(2);
        var result = invocation
                .operations()
                .run(
                        Operations.ACTIONS,
                        Map.of(
                                "file", operands.file(0).toString(),
                                "startLine", start.line(),
                                "startColumn", start.column(),
                                "endLine", end.line(),
                                "endColumn", end.column()));
        var offered = ((OperationResult.Offered) result).offered();
        if (invocation.json()) {
            invocation.out().println(offered.toJson());

            return;
        }
        ResultText.lines(offered).forEach(invocation.out()::println);
    }
}
