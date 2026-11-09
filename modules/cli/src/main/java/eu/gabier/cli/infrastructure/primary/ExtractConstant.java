package eu.gabier.cli.infrastructure.primary;

import eu.gabier.cli.domain.Operations;

/**
 * {@code extract-constant [--dry-run] <file> <line>:<column> <line>:<column> <name>}, as {@code extract-variable}: the
 * selection is the expression to extract into a {@code private static final} field of that name.
 */
final class ExtractConstant implements Capability {

    static final Command COMMAND = Command.called("extract-constant")
            .usage("gabier [--json] extract-constant [--dry-run] <file> <line>:<column> <line>:<column> <name>")
            .example("""
                $ gabier extract-constant --dry-run src/main/java/demo/Totals.java 6:28 6:37 COUNT_LABEL
                dry run, nothing written
                modified /work/demo/src/main/java/demo/Totals.java
                ...\
                """)
            .asksTheDaemon(Operations.EXTRACT_CONSTANT);

    @Override
    public Command command() {
        return COMMAND;
    }

    @Override
    public void run(Invocation invocation) {
        new ExtractSelection(Operations.EXTRACT_CONSTANT).run(invocation);
    }
}
