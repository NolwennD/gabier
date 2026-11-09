package eu.gabier.cli.infrastructure.primary;

import java.util.Map;

/**
 * {@code apply-action [--dry-run] <file> <line>:<column> <line>:<column> <title>}: the option may stand anywhere after
 * the command. The range is as {@code actions} takes it (each position from 1, the end excluded, sent to the daemon from
 * 0), and the title is one of those {@code actions} lists: the key of the action, as its number changes from one call to
 * the next. It answers as {@code rename} does.
 */
final class ApplyAction implements Capability {

    static final Command COMMAND = Command.called("apply-action")
            .summary("apply the fix or the rewrite the language server offers on a range, chosen by its title")
            .usage("gabier [--json] apply-action [--dry-run] <file> <line>:<column> <line>:<column> <title>")
            .example("""
                $ gabier apply-action --dry-run src/main/java/demo/Offers.java 4:5 4:9 "Import 'List' (java.util)"
                dry run, nothing written
                modified /work/demo/src/main/java/demo/Offers.java

                --- /work/demo/src/main/java/demo/Offers.java
                +++ /work/demo/src/main/java/demo/Offers.java
                @@ ... @@
                ...\
                """)
            .asksTheDaemon(
                    "editing-no-such-code-action: the server offers no action with this title on the range; the problem"
                            + " lists the ones it offers",
                    "editing-ambiguous-code-action: several actions have this title on the range; the problem lists"
                            + " them, and a narrower range may leave one",
                    "language-server-not-applicable: the action gives no edit (it is a command for an editor, such as a"
                            + " dialog to fill in, or it changes nothing)",
                    "editing-invalid-range: the range ends before it starts",
                    Refusal.FILE_OUTSIDE_PROJECT,
                    Refusal.FILE_MISSING,
                    Refusal.NOT_A_FILE,
                    Refusal.STALE_EDIT_OF_FILES,
                    Refusal.NOT_READY)
            .withNotes("""
                The title is the one gabier actions lists for the same file and range, exactly as written: the number\
                 of an action changes from one call to the next, the title does not.
                A title that no action has is refused with the titles the range offers; one that several have, with\
                 those actions. Nothing is written.
                Some actions of the language server are commands for an editor: they ask a question (a dialog) or a\
                 place before they change anything, and give no edit. They are listed by gabier actions and refused\
                 here as language-server-not-applicable.\
                """);

    @Override
    public Command command() {
        return COMMAND;
    }

    @Override
    public void run(Invocation invocation) {
        var arguments = invocation.arguments();
        var operands = arguments.operands(4);
        var start = operands.position(1);
        var end = operands.position(2);
        ChangedFilesReply.ask(
                        invocation,
                        command().commandName(),
                        Map.of(
                                "file", operands.file(0).toString(),
                                "startLine", String.valueOf(start.line()),
                                "startColumn", String.valueOf(start.column()),
                                "endLine", String.valueOf(end.line()),
                                "endColumn", String.valueOf(end.column()),
                                "title", operands.operand(3),
                                "dryRun", String.valueOf(arguments.dryRun())))
                .print();
    }
}
