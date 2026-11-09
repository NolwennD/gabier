package eu.gabier.cli.infrastructure.primary;

import eu.gabier.cli.domain.Operations;

/**
 * {@code extract-variable [--dry-run] <file> <line>:<column> <line>:<column> <name>}: the option may stand anywhere
 * after the command. The selection is the expression to extract, from its first character to the position just after
 * its last (the end is excluded). It answers as {@code rename} does.
 */
final class ExtractVariable implements Capability {

    static final Command COMMAND = Command.called("extract-variable")
            .usage("gabier [--json] extract-variable [--dry-run] <file> <line>:<column> <line>:<column> <name>")
            .example("""
                $ gabier extract-variable --dry-run src/main/java/demo/Greeter.java 7:26 7:48 joined
                dry run, nothing written
                modified /work/demo/src/main/java/demo/Greeter.java
                ...\
                """)
            .asksTheDaemon(Operations.EXTRACT_VARIABLE);

    @Override
    public Command command() {
        return COMMAND;
    }

    @Override
    public void run(Invocation invocation) {
        new ExtractSelection(command()).run(invocation);
    }
}
