package eu.gabier.cli.infrastructure.primary;

/**
 * What one {@link Command} does when it is run: it reads its arguments, asks the daemon if it must, and prints. It is
 * sealed: a capability that is not in {@code permits} does not compile, and one that is not in {@link Capabilities}
 * is not a command of gabier. To add one, see {@code .claude/skills/add-daemon-method}, step 5.
 */
sealed interface Capability
        permits Diagnostics,
                Rename,
                OrganizeImports,
                Cleanup,
                ExtractVariable,
                ExtractMethod,
                ExtractConstant,
                ExtractField,
                MoveTypeToFile,
                Move,
                Inline,
                Create,
                References,
                Actions,
                ApplyAction,
                Start,
                Status,
                Stop,
                InitAgent,
                Help {

    /** The command this capability runs: its name and its help text, which the class holds in {@code COMMAND}. */
    Command command();

    /** Runs the command, or throws the usage error or the refusal of the daemon. */
    void run(Invocation invocation);
}
