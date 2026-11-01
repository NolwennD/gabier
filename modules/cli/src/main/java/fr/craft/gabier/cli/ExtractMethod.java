package fr.craft.gabier.cli;

/**
 * {@code extract-method [--dry-run] <file> <line>:<column> <line>:<column> <name>}, as {@code extract-variable}: the
 * selection is the statements, or the expression, to extract into a method of that name.
 */
final class ExtractMethod implements Capability {

    static final Command COMMAND = Command.called("extract-method")
            .summary("turn statements, or an expression, into a method")
            .usage("gabier [--json] extract-method [--dry-run] <file> <line>:<column> <line>:<column> <name>")
            .example("""
                $ gabier extract-method --dry-run src/main/java/demo/Totals.java 6:9 6:47 printCount
                dry run, nothing written
                modified /work/demo/src/main/java/demo/Totals.java
                ...\
                """)
            .asksTheDaemon(
                    "language-server-not-extractable: the selection is not statements or an expression jdtls can"
                            + " extract (half a statement, an if whose branch returns)",
                    "language-server-invalid-name: the name is not a Java identifier, or another method of the class"
                            + " has it with the same parameters",
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
