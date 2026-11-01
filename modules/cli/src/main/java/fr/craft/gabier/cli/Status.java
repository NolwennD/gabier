package fr.craft.gabier.cli;

import fr.craft.gabier.protocol.DaemonStatus;
import fr.craft.gabier.protocol.TerminalText;

/**
 * {@code status}: the daemon, if one runs. It takes no argument and starts no daemon. The version, the project and the
 * reason jdtls failed come from the daemon, which has them from jdtls: the line is neutralised ({@link TerminalText}).
 */
final class Status implements Capability {

    static final Command COMMAND = Command.called("status")
            .summary("the project the daemon serves, and where jdtls is")
            .usage("gabier [--json] status")
            .example("""
                $ gabier status
                gabier-daemon 0.1, pid 4242, up 65s, project /work/demo, jdtls not-started\
                """)
            .asksARunningDaemon();

    @Override
    public Command command() {
        return COMMAND;
    }

    @Override
    public void run(Invocation invocation) {
        invocation.arguments().exactly(0);
        var result = invocation.askRunning(command().commandName(), false);
        var status = DaemonStatus.of(result.result());
        invocation.out().println(invocation.json() ? status.toJson() : text(status));
    }

    private static String text(DaemonStatus status) {
        var failure = status.jdtlsFailure().map(reason -> " (" + reason + ")").orElse("");
        return TerminalText.neutralise("gabier-daemon " + status.version() + ", pid " + status.pid() + ", up "
                + status.uptime().toSeconds() + "s, project " + status.project() + ", jdtls "
                + status.jdtls().text() + failure);
    }
}
