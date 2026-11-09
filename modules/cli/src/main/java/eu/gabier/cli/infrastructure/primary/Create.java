package eu.gabier.cli.infrastructure.primary;

import eu.gabier.cli.domain.Operations;
import java.util.Map;

/**
 * {@code create [--dry-run] <file> <class|interface|record|enum>}: the option may stand anywhere after the command. The
 * kind goes to the daemon as it is typed, and the daemon refuses a word that is not one of the four. It answers as
 * {@code rename} does: the file created, then the diff.
 */
final class Create implements Capability {

    static final Command COMMAND = Command.called("create")
            .usage("gabier [--json] create [--dry-run] <file> <class|interface|record|enum>")
            .example("""
                $ gabier create --dry-run src/main/java/demo/Widget.java class
                dry run, nothing written
                created /work/demo/src/main/java/demo/Widget.java

                --- /dev/null
                +++ /work/demo/src/main/java/demo/Widget.java
                @@ -0,0 +1,5 @@
                +package demo;
                +
                +public class Widget {
                +
                +}\
                """)
            .asksTheDaemon(Operations.CREATE);

    @Override
    public Command command() {
        return COMMAND;
    }

    @Override
    public void run(Invocation invocation) {
        var arguments = invocation.arguments();
        var operands = arguments.operands(2);
        ChangedFilesReply.ask(
                        invocation,
                        command().commandName(),
                        Map.of(
                                "file", operands.file(0).toString(),
                                "kind", operands.operand(1),
                                "dryRun", String.valueOf(arguments.dryRun())))
                .print();
    }
}
