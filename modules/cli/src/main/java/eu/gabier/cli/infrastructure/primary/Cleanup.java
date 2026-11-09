package eu.gabier.cli.infrastructure.primary;

import eu.gabier.cli.domain.Operations;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * {@code cleanup [--dry-run] <file> <cleanup>...}: the first operand is the file, the others are the identifiers of the
 * clean-ups, applied in the order given; the option may stand anywhere after the command. The identifiers go to the
 * daemon one per line, so one with a line break is a usage error. It answers as {@code rename} does, and says {@code
 * nothing to change} (as text) for a file with nothing to clean up.
 */
final class Cleanup implements Capability {

    static final Command COMMAND = Command.called("cleanup")
            .usage("gabier [--json] cleanup [--dry-run] <file> <cleanup>...")
            .example("""
                $ gabier cleanup --dry-run src/main/java/demo/Banner.java stringConcatToTextBlock
                dry run, nothing written
                modified /work/demo/src/main/java/demo/Banner.java

                --- /work/demo/src/main/java/demo/Banner.java
                +++ /work/demo/src/main/java/demo/Banner.java
                @@ ... @@
                ...\
                """)
            .asksTheDaemon(Operations.CLEANUP);

    @Override
    public Command command() {
        return COMMAND;
    }

    @Override
    public void run(Invocation invocation) {
        var arguments = invocation.arguments();
        var operands = arguments.atLeast(2);
        var cleanUps = operands.operands().subList(1, operands.operands().size());
        if (cleanUps.stream().anyMatch(cleanUp -> cleanUp.lines().count() != 1)) {
            throw UsageException.unsupportedArguments(arguments.all());
        }
        var reply = ChangedFilesReply.ask(
                invocation,
                command().commandName(),
                Map.of(
                        "file", operands.file(0).toString(),
                        "cleanUps", cleanUps.stream().collect(Collectors.joining("\n")),
                        "dryRun", String.valueOf(arguments.dryRun())));
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
