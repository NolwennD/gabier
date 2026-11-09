package eu.gabier.cli.infrastructure.primary;

import eu.gabier.cli.domain.Operations;
import java.util.Map;

/**
 * {@code inline [--dry-run] <file> <line>:<column>}: the option may stand anywhere after the command. The position is
 * from 1 and goes to the daemon from 0. It answers as {@code rename} does: the files modified, then the diff.
 */
final class Inline implements Capability {

    static final Command COMMAND = Command.called("inline")
            .usage("gabier [--json] inline [--dry-run] <file> <line>:<column>")
            .example("""
                $ gabier inline --dry-run src/main/java/demo/Prices.java 4:22
                dry run, nothing written
                modified /work/demo/src/main/java/demo/Prices.java
                modified /work/demo/src/main/java/demo/Checkout.java
                ...\
                """)
            .asksTheDaemon(Operations.INLINE);

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
