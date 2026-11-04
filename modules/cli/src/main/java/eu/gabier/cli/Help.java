package eu.gabier.cli;

/**
 * {@code help} lists the commands, {@code help <command>} explains one. It never talks to the daemon, and starts none:
 * the text is the one of {@link Command}.
 */
final class Help implements Capability {

    static final Command COMMAND = Command.called("help")
            .summary("list the commands, or explain one")
            .usage("""
                gabier [--json] help [<command>]
                gabier [--json] help guide
                gabier [--json] --help
                gabier [--json] -h\
                """)
            .example("""
                $ gabier help rename
                gabier [--json] rename [--dry-run] <file> <line> <column> <new-name>
                ...\
                """)
            .standalone(Refusal.USAGE);

    @Override
    public Command command() {
        return COMMAND;
    }

    @Override
    public void run(Invocation invocation) {
        var arguments = invocation.arguments();
        if (arguments.operands().isEmpty()) {
            invocation.out().println(HelpText.index(invocation.json()));

            return;
        }
        var name = arguments.exactly(1).operand(0);
        if (name.equals(Guide.TOPIC)) {
            invocation.out().println(HelpText.guide(invocation.json()));

            return;
        }
        var capability =
                Capabilities.named(name).orElseThrow(() -> UsageException.unknownCommand(name, HelpText.names()));
        invocation.out().println(HelpText.of(capability.command(), invocation.json()));
    }
}
