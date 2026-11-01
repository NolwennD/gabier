package fr.craft.gabier.cli;

import java.util.Map;

/**
 * {@code inline [--dry-run] <file> <line>:<column>}: the option may stand anywhere after the command. The position is
 * from 1 and goes to the daemon from 0. It answers as {@code rename} does: the files modified, then the diff.
 */
final class Inline implements Capability {

    @Override
    public Command command() {
        return Command.INLINE;
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
