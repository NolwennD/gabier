package eu.gabier.cli.infrastructure.primary;

import eu.gabier.cli.domain.Operations;
import java.util.Map;

/**
 * {@code apply-action [--dry-run] <file> <line>:<column> <line>:<column> <title>}: the option may stand anywhere after
 * the command. The range is as {@code actions} takes it (each position from 1, the end excluded, sent to the daemon from
 * 0), and the title is one of those {@code actions} lists: the key of the action, as its number changes from one call to
 * the next. It answers as {@code rename} does.
 */
final class ApplyAction implements Capability {

    static final Command COMMAND = Command.called("apply-action")
            .usage("gabier [--json] apply-action [--dry-run] <file> <line>:<column> <line>:<column> <title>")
            .example("""
                $ gabier apply-action --dry-run src/main/java/demo/Offers.java 4:5 4:9 "Import 'List' (java.util)"
                dry run, nothing written
                modified /work/demo/src/main/java/demo/Offers.java

                --- /work/demo/src/main/java/demo/Offers.java
                +++ /work/demo/src/main/java/demo/Offers.java
                @@ ... @@
                ...\
                """)
            .asksTheDaemon(Operations.APPLY_ACTION);

    @Override
    public Command command() {
        return COMMAND;
    }

    @Override
    public void run(Invocation invocation) {
        var arguments = invocation.arguments();
        var operands = arguments.operands(4);
        var start = operands.position(1);
        var end = operands.position(2);
        ChangedFilesReply.ask(
                        invocation,
                        command().commandName(),
                        Map.of(
                                "file", operands.file(0).toString(),
                                "startLine", String.valueOf(start.line()),
                                "startColumn", String.valueOf(start.column()),
                                "endLine", String.valueOf(end.line()),
                                "endColumn", String.valueOf(end.column()),
                                "title", operands.operand(3),
                                "dryRun", String.valueOf(arguments.dryRun())))
                .print();
    }
}
