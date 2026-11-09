package eu.gabier.cli.infrastructure.primary;

import eu.gabier.cli.domain.Operations;

/**
 * {@code extract-method [--dry-run] <file> <line>:<column> <line>:<column> <name>}, as {@code extract-variable}: the
 * selection is the statements, or the expression, to extract into a method of that name.
 */
final class ExtractMethod implements Capability {

    static final Command COMMAND = Command.called("extract-method")
            .usage("gabier [--json] extract-method [--dry-run] <file> <line>:<column> <line>:<column> <name>")
            .example("""
                $ gabier extract-method --dry-run src/main/java/demo/Totals.java 6:9 6:47 printCount
                dry run, nothing written
                modified /work/demo/src/main/java/demo/Totals.java
                ...\
                """)
            .asksTheDaemon(Operations.EXTRACT_METHOD);

    @Override
    public Command command() {
        return COMMAND;
    }

    @Override
    public void run(Invocation invocation) {
        new ExtractSelection(Operations.EXTRACT_METHOD).run(invocation);
    }
}
