package eu.gabier.cli.infrastructure.primary;

/**
 * {@code help} lists the commands, {@code help <command>} explains one. It never talks to the daemon, and starts none:
 * the text is the one of {@link Command}.
 */
final class Help implements Capability {

    static final Command COMMAND = Command.called("help")
            .usage("""
                gabier [--json] help [<command>]
                gabier [--json] help guide
                gabier [--json] help agents
                gabier [--json] help --for=<task>
                gabier [--json] --help
                gabier [--json] -h\
                """)
            .example("""
                $ gabier help rename
                gabier [--json] rename [--dry-run] <file> <line>:<column> <new-name>
                ...\
                """)
            .standalone("list the commands, or explain one", Refusal.USAGE)
            .withNotes("""
                help lists the commands, one line each; help <command> gives the usage, one example, the notes and the\
                 refusals of a command. --help and -h are help; <command> --help and <command> -h are help <command>, for\
                 every command: the flag can stand anywhere after the command, wins over the other words and starts no\
                 daemon.
                With --json the same content is one object: {"commands":[{"name","summary","usage"}]} for the list,\
                 {"name","summary","usage","example","refusals":[...]} for a command, with a "notes" member before\
                 "refusals" when it has notes.
                help guide prints the guide of the improvements: one section per situation, with the words a person uses\
                 for it, the command or the action that solves it, a before and an after, and the limits. help agents\
                 prints the instructions to paste in AGENTS.md or CLAUDE.md (init-agent writes them into the project).\
                 With --json each is {"name","summary","text"}.
                help --for="<task>" asks the guide a question: the task in English, and the answer is one to three\
                 sections, best first, each with its title, its line "Ask:" or "By hand:" and a confidence from 0 to 1.\
                 A task that no section answers is said plainly ("Nothing in the guide matches ...") with exit code 0.\
                 A section answers when it holds at least two distinctive words of the task in its title or its words,\
                 so a task of one word never has an answer ("rename" has none, "rename a method" has); "this" and\
                 "that" are not words of the task. --for takes its value after =, and an empty task is a usage error.\
                 With --json: {"question","matches":[{"rank","title","kind":"ask" or "by-hand","how","confidence"}]},\
                 matches empty when nothing answers.
                An unknown command is a usage error that lists the known ones and names the one a typo away (did you\
                 mean organize-imports?, the parameter suggestion). A refusal where the language server declined what\
                 was asked ends with "Usage and other ways: gabier help <command>"; an unknown first word ends with\
                 "Not sure which command? Ask for the task: gabier help --for=<task>".
                help never talks to the daemon: it starts none and works with no project.\
                """);

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
        if (name.equals(GuideTopic.FOR) || name.startsWith(GuideTopic.FOR + "=")) {
            var task = name.substring(Math.min(name.length(), GuideTopic.FOR.length() + 1))
                    .strip();
            if (task.isEmpty()) {
                throw UsageException.unsupportedArguments(arguments.all());
            }
            invocation.out().println(HelpText.answer(task, invocation.json()));

            return;
        }
        if (name.equals(GuideTopic.TOPIC)) {
            invocation.out().println(HelpText.guide(invocation.json()));

            return;
        }
        if (name.equals(AgentInstructions.TOPIC)) {
            invocation.out().println(HelpText.agents(invocation.json()));

            return;
        }
        var capability =
                Capabilities.named(name).orElseThrow(() -> UsageException.unknownCommand(name, HelpText.names()));
        invocation.out().println(HelpText.of(capability.command(), invocation.json()));
    }
}
