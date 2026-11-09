package eu.gabier.cli.infrastructure.primary;

import eu.gabier.cli.domain.Operations;

/**
 * {@code extract-field [--dry-run] <file> <line>:<column> <line>:<column> <name>}, as {@code extract-variable}: the
 * selection is the expression to extract into a field of that name.
 */
final class ExtractField implements Capability {

    static final Command COMMAND = Command.called("extract-field")
            .usage("gabier [--json] extract-field [--dry-run] <file> <line>:<column> <line>:<column> <name>")
            .example("""
                $ gabier extract-field --dry-run src/main/java/demo/Totals.java 6:28 6:37 label
                dry run, nothing written
                modified /work/demo/src/main/java/demo/Totals.java
                ...\
                """)
            .asksTheDaemon(Operations.EXTRACT_FIELD);

    @Override
    public Command command() {
        return COMMAND;
    }

    @Override
    public void run(Invocation invocation) {
        new ExtractSelection(Operations.EXTRACT_FIELD).run(invocation);
    }
}
