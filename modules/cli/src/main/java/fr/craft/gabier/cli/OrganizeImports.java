package fr.craft.gabier.cli;

import java.util.Map;

/**
 * {@code organize-imports [--dry-run] <file>}: the option may stand anywhere after the command. It answers as {@code
 * rename} does, and says {@code nothing to change} (as text) for a file whose imports are already in order.
 */
final class OrganizeImports implements Capability {

    @Override
    public Command command() {
        return Command.ORGANIZE_IMPORTS;
    }

    @Override
    public void run(Invocation invocation) {
        var arguments = invocation.arguments();
        var operands = arguments.operands(1);
        var reply = ChangedFilesReply.ask(
                invocation,
                command().commandName(),
                Map.of("file", operands.file(0).toString(), "dryRun", String.valueOf(arguments.dryRun())));
        if (!invocation.json() && reply.isEmpty()) {
            if (arguments.dryRun()) {
                invocation.out().println("dry run, nothing written");
            }
            invocation.out().println("nothing to change");

            return;
        }
        reply.print();
    }
}
