package fr.craft.gabier.cli;

import java.util.Map;

/**
 * {@code rename [--dry-run] <file> <line> <column> <new-name>} or {@code rename [--dry-run] --token <file>
 * <identifier> <new-name>}: the options may stand anywhere after the command. The position is from 1, as {@code
 * diagnostics} shows it, and goes to the daemon from 0. The text lists the files that changed, then the diff, with the
 * same neutralisation as every text that comes from the project; {@code --json} gives the result as the daemon gave it.
 */
final class Rename implements Capability {

    @Override
    public Command command() {
        return Command.RENAME;
    }

    @Override
    public void run(Invocation invocation) {
        var arguments = invocation.arguments();
        var dryRun = String.valueOf(arguments.dryRun());
        ChangedFilesReply reply;
        if (arguments.has(Arguments.TOKEN)) {
            var operands = arguments.operands(3, Arguments.TOKEN);
            reply = ChangedFilesReply.ask(
                    invocation,
                    "rename-token",
                    Map.of(
                            "file", operands.file(0).toString(),
                            "token", operands.operand(1),
                            "newName", operands.operand(2),
                            "dryRun", dryRun));
        } else {
            var operands = arguments.operands(4);
            var position = operands.position(1, 2);
            reply = ChangedFilesReply.ask(
                    invocation,
                    command().commandName(),
                    Map.of(
                            "file", operands.file(0).toString(),
                            "line", String.valueOf(position.line()),
                            "column", String.valueOf(position.column()),
                            "newName", operands.operand(3),
                            "dryRun", dryRun));
        }
        reply.print();
    }
}
