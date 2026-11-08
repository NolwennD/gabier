package eu.gabier.cli;

import eu.gabier.protocol.CodeActions;
import eu.gabier.protocol.TerminalText;
import java.util.Map;
import java.util.stream.IntStream;

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
            .summary("list the fixes and the rewrites the language server offers on a range")
            .usage("gabier [--json] actions <file> <line>:<column> <line>:<column>")
            .example("""
                $ gabier actions src/main/java/demo/Offers.java 4:5 4:9
                1 quickfix Import 'List' (java.util)
                2 refactor.extract.variable Extract to local variable -> gabier extract-variable
                ...\
                """)
            .asksTheDaemon(
                    "editing-invalid-range: the range ends before it starts",
                    Refusal.FILE_OUTSIDE_PROJECT,
                    Refusal.FILE_MISSING,
                    Refusal.NOT_A_FILE,
                    Refusal.NOT_READY)
            .withNotes("""
                Nothing is written. The range is two positions, each line:column from 1, the end excluded; to ask\
                 about a point, give it twice.
                The actions are the fixes of the problems the range holds (a type that is not imported, a method to\
                 create) and the rewrites of the code it covers (a concatenation to a text block, an extraction).\
                 The actions of a whole file (organize the imports, generate accessors) are not listed: they are not\
                 about a range.
                Each line is the number of the action in the list, its kind (quickfix, refactor.extract.variable,\
                 quickassist, ...) and its title. The kind and the title are the language server's own text, which\
                 gabier does not interpret. A range with nothing offered prints "no actions" and exits with 0.
                An action that a command of gabier also does is followed by "-> gabier <command>", read from its kind\
                 (and, for the kind refactor.inline that "Make Static" shares, from its title, which must start with\
                 "Inline"): extract-variable, extract-field, extract-method and extract-constant (the same file and\
                 range, then the name to give), and inline (the file and one position, on the name). The command\
                 is not always what the action does: extract-variable replaces the selected expression only, so the\
                 action "Extract to local variable (replace all occurrences)" is linked to it but replaces every\
                 occurrence, which only apply-action does. Prefer the command when the name matters: apply-action\
                 takes the name jdtls guesses. With --json the action has a member "command", only when there is one.
                To apply one as the server proposes it, give its title to gabier apply-action with the same range.\
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
                .daemon()
                .ask(
                        command().commandName(),
                        Map.of(
                                "file", operands.file(0).toString(),
                                "startLine", String.valueOf(start.line()),
                                "startColumn", String.valueOf(start.column()),
                                "endLine", String.valueOf(end.line()),
                                "endColumn", String.valueOf(end.column())));
        var offered = CodeActions.of(result.result());
        var out = invocation.out();
        if (invocation.json()) {
            out.println(offered.toJson());

            return;
        }
        if (offered.actions().isEmpty()) {
            out.println("no actions");

            return;
        }
        IntStream.range(0, offered.actions().size())
                .mapToObj(index -> line(index + 1, offered.actions().get(index)))
                .forEach(out::println);
    }

    private static String line(int number, CodeActions.Action action) {
        var reference = action.command().isEmpty() ? "" : " -> gabier " + action.command();

        return TerminalText.neutralise(
                number + " " + (action.kind().isEmpty() ? "-" : action.kind()) + " " + action.title() + reference);
    }
}
