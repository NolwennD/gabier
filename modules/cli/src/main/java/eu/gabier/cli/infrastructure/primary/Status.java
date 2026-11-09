package eu.gabier.cli.infrastructure.primary;

import eu.gabier.cli.application.OperationResult;
import eu.gabier.cli.application.ResultText;
import eu.gabier.cli.domain.Operations;
import eu.gabier.protocol.TerminalText;
import java.util.Map;

/**
 * {@code status}: the daemon, if one runs. It takes no argument and starts no daemon. The version, the project, the
 * reason jdtls failed and what it is doing while it starts come from the daemon, which has them from jdtls: the line is
 * neutralised ({@link TerminalText}).
 */
final class Status implements Capability {

    static final Command COMMAND = Command.called("status")
            .usage("gabier [--json] status")
            .example("""
                $ gabier status
                gabier-daemon 0.1.0, pid 4242, up 65s, project /work/demo, jdtls not-started\
                """)
            .asksARunningDaemon(Operations.STATUS)
            .withNotes("""
                In text, what jdtls is doing while it starts ends the line, in parentheses. With --json, it is the member\
                 jdtlsProgress of the result. A daemon stops by itself after a delay with no client (60 minutes, \
                GABIER_IDLE_TIMEOUT in minutes, 0 for never): a last line says the delay and the time left before this\
                 call, which restarts it; with --json it is the member idle (timeoutMillis, leftMillis), absent when the\
                 daemon never stops by itself.\
                """);

    @Override
    public Command command() {
        return COMMAND;
    }

    @Override
    public void run(Invocation invocation) {
        invocation.arguments().exactly(0);
        var result = (OperationResult.Reported) invocation.operations().run(Operations.STATUS, Map.of());
        var status = result.status();
        if (invocation.json()) {
            invocation.out().println(status.toJson());

            return;
        }
        ResultText.lines(status).forEach(invocation.out()::println);
    }
}
