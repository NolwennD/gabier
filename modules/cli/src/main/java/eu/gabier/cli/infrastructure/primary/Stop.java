package eu.gabier.cli.infrastructure.primary;

import eu.gabier.cli.domain.Operations;

/** {@code stop}: ends the daemon, if one runs, and waits for it to hang up. It takes no argument and starts no daemon. */
final class Stop implements Capability {

    static final Command COMMAND =
            Command.called("stop").usage("gabier [--json] stop").example("""
                $ gabier stop
                gabier-daemon stopped\
                """).asksARunningDaemon(Operations.STOP);

    @Override
    public Command command() {
        return COMMAND;
    }

    @Override
    public void run(Invocation invocation) {
        invocation.arguments().exactly(0);
        invocation.daemon().askRunning(command().commandName(), true);
        invocation.out().println(invocation.json() ? "{\"stopped\":true}" : "gabier-daemon stopped");
    }
}
