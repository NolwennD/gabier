package fr.craft.gabier.cli;

import java.util.Map;

/**
 * {@code move-type-to-file [--dry-run] <file> <line>:<column>}: the option may stand anywhere after the command. The
 * position is from 1 and goes to the daemon from 0. It answers as {@code rename} does: the files created and modified,
 * then the diff.
 */
final class MoveTypeToFile implements Capability {

    @Override
    public Command command() {
        return Command.MOVE_TYPE_TO_FILE;
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
