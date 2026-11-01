package fr.craft.gabier.cli;

import java.util.Map;

/**
 * What the commands that extract a selection under a name share: {@code <command> [--dry-run] <file>
 * <line>:<column> <line>:<column> <name>}, each position from 1 (the end excluded), sent to the daemon from 0, and the
 * answer of the daemon as {@code rename} gives it.
 *
 * @param command the command that extracts
 */
record ExtractSelection(Command command) {

    void run(Invocation invocation) {
        var arguments = invocation.arguments();
        var operands = arguments.operands(4);
        var start = operands.position(1);
        var end = operands.position(2);
        ChangedFilesReply.ask(
                        invocation,
                        command.commandName(),
                        Map.of(
                                "file", operands.file(0).toString(),
                                "startLine", String.valueOf(start.line()),
                                "startColumn", String.valueOf(start.column()),
                                "endLine", String.valueOf(end.line()),
                                "endColumn", String.valueOf(end.column()),
                                "name", operands.operand(3),
                                "dryRun", String.valueOf(arguments.dryRun())))
                .print();
    }
}
