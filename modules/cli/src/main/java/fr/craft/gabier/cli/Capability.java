package fr.craft.gabier.cli;

/**
 * What one {@link Command} does when it is run: it reads its arguments, asks the daemon if it must, and prints. To add
 * one, see {@code .claude/skills/add-daemon-method}, step 5.
 */
interface Capability {

    /** The command this capability runs. */
    Command command();

    /** Runs the command, or throws the usage error or the refusal of the daemon. */
    void run(Invocation invocation);
}
