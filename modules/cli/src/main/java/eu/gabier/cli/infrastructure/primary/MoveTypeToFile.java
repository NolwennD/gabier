package eu.gabier.cli.infrastructure.primary;

import eu.gabier.cli.domain.Operations;
import java.util.Map;

/**
 * {@code move-type-to-file [--dry-run] <file> <line>:<column>}: the option may stand anywhere after the command. The
 * position is from 1 and goes to the daemon from 0. It answers as {@code rename} does: the files created and modified,
 * then the diff.
 */
final class MoveTypeToFile implements Capability {

    static final Command COMMAND = Command.called("move-type-to-file")
            .usage("gabier [--json] move-type-to-file [--dry-run] <file> <line>:<column>")
            .example("""
                $ gabier move-type-to-file --dry-run src/main/java/demo/Shapes.java 6:25
                dry run, nothing written
                modified /work/demo/src/main/java/demo/app/Drawing.java
                modified /work/demo/src/main/java/demo/Shapes.java
                created /work/demo/src/main/java/demo/Circle.java
                ...\
                """)
            .asksTheDaemon(Operations.MOVE_TYPE_TO_FILE);

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
                        Operations.MOVE_TYPE_TO_FILE,
                        Map.of(
                                "file", operands.file(0).toString(),
                                "line", position.line(),
                                "column", position.column(),
                                "dryRun", arguments.dryRun()))
                .print();
    }
}
