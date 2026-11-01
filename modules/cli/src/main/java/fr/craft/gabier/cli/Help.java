package fr.craft.gabier.cli;

/**
 * {@code help} lists the commands, {@code help <command>} explains one. It never talks to the daemon, and starts none:
 * the text is the one of {@link Command}.
 */
final class Help implements Capability {

    @Override
    public Command command() {
        return Command.HELP;
    }

    @Override
    public void run(Invocation invocation) {
        var arguments = invocation.arguments();
        if (arguments.operands().isEmpty()) {
            invocation.out().println(HelpText.index(invocation.json()));

            return;
        }
        var name = arguments.exactly(1).operand(0);
        var command = Command.named(name).orElseThrow(() -> UsageException.unknownCommand(name, HelpText.names()));
        invocation.out().println(HelpText.of(command, invocation.json()));
    }
}
