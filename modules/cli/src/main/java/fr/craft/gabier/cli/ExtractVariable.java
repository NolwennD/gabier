package fr.craft.gabier.cli;

/**
 * {@code extract-variable [--dry-run] <file> <line>:<column> <line>:<column> <name>}: the option may stand anywhere
 * after the command. The selection is the expression to extract, from its first character to the position just after
 * its last (the end is excluded). It answers as {@code rename} does.
 */
final class ExtractVariable implements Capability {

    static final Command COMMAND = Command.called("extract-variable")
            .summary("turn an expression into a local variable")
            .usage("gabier [--json] extract-variable [--dry-run] <file> <line>:<column> <line>:<column> <name>")
            .example("""
                $ gabier extract-variable --dry-run src/main/java/demo/Greeter.java 7:26 7:48 joined
                dry run, nothing written
                modified /work/demo/src/main/java/demo/Greeter.java
                ...\
                """)
            .asksTheDaemon(
                    "language-server-not-an-expression: the selection is not an expression jdtls can extract",
                    "language-server-invalid-name: the name is not a Java identifier, or is a parameter of the method",
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
