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
                In text, what jdtls is doing while it starts ends the line, in parentheses, and a last line says the\
                 delay of the idle stop and the time left. The message of jdtls is its own text: a control character in\
                 it is written as an escape. It never starts jdtls (start does) and never starts a daemon: with none\
                 running it answers daemon-not-running at once. A command that finds the daemon gone, or that is told by\
                 it that it is stopping (daemon-stopping), starts a new one.\
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
