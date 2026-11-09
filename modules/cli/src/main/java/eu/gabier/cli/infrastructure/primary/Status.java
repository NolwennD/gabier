package eu.gabier.cli.infrastructure.primary;

import eu.gabier.cli.domain.Operations;
import eu.gabier.protocol.DaemonStatus;
import eu.gabier.protocol.TerminalText;
import java.util.ArrayList;
import java.util.stream.Collectors;

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
            .asksARunningDaemon(Operations.STATUS);

    @Override
    public Command command() {
        return COMMAND;
    }

    @Override
    public void run(Invocation invocation) {
        invocation.arguments().exactly(0);
        var result = invocation.daemon().askRunning(command().commandName(), false);
        var status = DaemonStatus.of(result.result());
        invocation.out().println(invocation.json() ? status.toJson() : text(status));
    }

    /** The line of the daemon, then the settings of the imports it follows for the project, each with its source. */
    private static String text(DaemonStatus status) {
        var failure = status.jdtlsFailure().map(reason -> " (" + reason + ")").orElse("");
        var progress =
                status.jdtlsProgress().map(doing -> " (" + doing.text() + ")").orElse("");
        var lines = new ArrayList<String>();
        lines.add("gabier-daemon " + status.version() + ", pid " + status.pid() + ", up "
                + status.uptime().toSeconds() + "s, project " + status.project() + ", jdtls "
                + status.jdtls().text() + failure + progress);
        lines.add("imports:");
        status.imports().lines().forEach(line -> lines.add("  " + line));

        return lines.stream().map(TerminalText::neutralise).collect(Collectors.joining(System.lineSeparator()));
    }
}
