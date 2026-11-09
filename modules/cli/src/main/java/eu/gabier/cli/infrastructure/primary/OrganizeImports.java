package eu.gabier.cli.infrastructure.primary;

import eu.gabier.cli.domain.Operations;
import java.util.Map;

/**
 * {@code organize-imports [--dry-run] <file>}: the option may stand anywhere after the command. It answers as {@code
 * rename} does, and says {@code nothing to change} (as text) for a file whose imports are already in order.
 */
final class OrganizeImports implements Capability {

    static final Command COMMAND = Command.called("organize-imports")
            .usage("gabier [--json] organize-imports [--dry-run] <file>")
            .example("""
                $ gabier organize-imports src/main/java/demo/Tidy.java
                nothing to change\
                """)
            .asksTheDaemon(Operations.ORGANIZE_IMPORTS);

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
                Operations.ORGANIZE_IMPORTS,
                Map.of("file", operands.file(0).toString(), "dryRun", arguments.dryRun()));
        if (!invocation.json() && reply.isEmpty()) {
            if (arguments.dryRun()) {
                invocation.out().println("dry run, nothing written");
            }
            reply.printWarnings();
            invocation.out().println("nothing to change");

            return;
        }
        reply.print();
    }
}
