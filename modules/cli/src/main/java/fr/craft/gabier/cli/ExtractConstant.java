package fr.craft.gabier.cli;

/**
 * {@code extract-constant [--dry-run] <file> <line>:<column> <line>:<column> <name>}, as {@code extract-variable}: the
 * selection is the expression to extract into a {@code private static final} field of that name.
 */
final class ExtractConstant implements Capability {

    static final Command COMMAND = Command.called("extract-constant")
            .summary("turn an expression into a constant")
            .usage("gabier [--json] extract-constant [--dry-run] <file> <line>:<column> <line>:<column> <name>")
            .example("""
                $ gabier extract-constant --dry-run src/main/java/demo/Totals.java 6:28 6:37 COUNT_LABEL
                dry run, nothing written
                modified /work/demo/src/main/java/demo/Totals.java
                ...\
                """)
            .asksTheDaemon(
                    "language-server-not-extractable: the selection is not an expression that can be a constant (it"
                            + " reads a local variable or an instance member, or it is not an expression)",
                    "language-server-invalid-name: the name is not a Java identifier",
                    Refusal.FILE_OUTSIDE_PROJECT,
                    Refusal.FILE_MISSING,
                    Refusal.NOT_A_FILE,
                    Refusal.STALE_EDIT_OF_A_FILE,
                    Refusal.NOT_READY);

    @Override
    public Command command() {
        return COMMAND;
    }

    @Override
    public void run(Invocation invocation) {
        new ExtractSelection(command()).run(invocation);
    }
}
