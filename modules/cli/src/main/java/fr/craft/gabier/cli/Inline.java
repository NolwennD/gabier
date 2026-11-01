package fr.craft.gabier.cli;

import java.util.Map;

/**
 * {@code inline [--dry-run] <file> <line>:<column>}: the option may stand anywhere after the command. The position is
 * from 1 and goes to the daemon from 0. It answers as {@code rename} does: the files modified, then the diff.
 */
final class Inline implements Capability {

    static final Command COMMAND = Command.called("inline")
            .summary("replace a local variable, a method or a constant by its value")
            .usage("gabier [--json] inline [--dry-run] <file> <line>:<column>")
            .example("""
                $ gabier inline --dry-run src/main/java/demo/Prices.java 4:22
                dry run, nothing written
                modified /work/demo/src/main/java/demo/Prices.java
                modified /work/demo/src/main/java/demo/Checkout.java
                ...\
                """)
            .asksTheDaemon(
                    "language-server-not-inlinable: nothing the server can inline at the position (not on a variable,"
                            + " a method or a constant; a variable assigned more than once; a recursive or abstract"
                            + " method; a declaration that nothing uses)",
                    Refusal.FILE_OUTSIDE_PROJECT,
                    Refusal.FILE_MISSING,
                    Refusal.NOT_A_FILE,
                    Refusal.STALE_EDIT_OF_FILES,
                    Refusal.NOT_READY)
            .withNotes("""
                The position is on the name of a local variable, a method or a constant.
                A local variable is inlined everywhere, whatever the position.
                A position on the declaration of a method or a constant inlines every use and removes it;
                a position on one use inlines that use only and keeps the declaration.\
                """);

    @Override
    public Command command() {
        return COMMAND;
    }

    @Override
    public void run(Invocation invocation) {
        var arguments = invocation.arguments();
        var operands = arguments.operands(2);
        var position = operands.position(1);
        ChangedFilesReply.ask(
                        invocation,
                        command().commandName(),
                        Map.of(
                                "file", operands.file(0).toString(),
                                "line", String.valueOf(position.line()),
                                "column", String.valueOf(position.column()),
                                "dryRun", String.valueOf(arguments.dryRun())))
                .print();
    }
}
