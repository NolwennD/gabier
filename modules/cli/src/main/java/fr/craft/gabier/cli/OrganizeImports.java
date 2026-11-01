package fr.craft.gabier.cli;

import java.util.Map;

/**
 * {@code organize-imports [--dry-run] <file>}: the option may stand anywhere after the command. It answers as {@code
 * rename} does, and says {@code nothing to change} (as text) for a file whose imports are already in order.
 */
final class OrganizeImports implements Capability {

    static final Command COMMAND = Command.called("organize-imports")
            .summary("add the missing imports, remove the unused ones and order the rest, never with a star import")
            .usage("gabier [--json] organize-imports [--dry-run] <file>")
            .example("""
                $ gabier organize-imports src/main/java/demo/Tidy.java
                nothing to change\
                """)
            .asksTheDaemon(
                    "language-server-ambiguous-imports: several packages declare a type the file uses without"
                            + " importing it; the candidates are in the problem",
                    "language-server-unresolved-imports: jdtls would delete imports it reports an error on",
                    Refusal.FILE_OUTSIDE_PROJECT,
                    Refusal.FILE_MISSING,
                    Refusal.NOT_A_FILE,
                    Refusal.STALE_EDIT_OF_A_FILE,
                    Refusal.NOT_READY);

    @Override
    public Command command() {
        return COMMAND;
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
