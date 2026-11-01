package fr.craft.gabier.cli;

/** {@code stop}: ends the daemon, if one runs, and waits for it to hang up. It takes no argument and starts no daemon. */
final class Stop implements Capability {

    static final Command COMMAND = Command.called("stop")
            .summary("stop the daemon")
            .usage("gabier [--json] stop")
            .example("""
                $ gabier stop
                gabier-daemon stopped\
                """)
            .asksARunningDaemon();

    @Override
    public Command command() {
        return COMMAND;
    }

    @Override
    public void run(Invocation invocation) {
        invocation.arguments().exactly(0);
        invocation.askRunning(command().commandName(), true);
        invocation.out().println(invocation.json() ? "{\"stopped\":true}" : "gabier-daemon stopped");
    }
}
