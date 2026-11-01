package fr.craft.gabier.cli;

/**
 * {@code extract-field [--dry-run] <file> <line>:<column> <line>:<column> <name>}, as {@code extract-variable}: the
 * selection is the expression to extract into a field of that name.
 */
final class ExtractField implements Capability {

    static final Command COMMAND = Command.called("extract-field")
            .summary("turn an expression into a field")
            .usage("gabier [--json] extract-field [--dry-run] <file> <line>:<column> <line>:<column> <name>")
            .example("""
                $ gabier extract-field --dry-run src/main/java/demo/Totals.java 6:28 6:37 label
                dry run, nothing written
                modified /work/demo/src/main/java/demo/Totals.java
                ...\
                """)
            .asksTheDaemon(
                    Refusal.NOT_EXTRACTABLE,
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
