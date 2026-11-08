package eu.gabier.cli;

import eu.gabier.protocol.Problem;
import eu.gabier.protocol.ProblemMapper;
import java.util.Optional;
import java.util.Set;

/**
 * The line that ends a refusal and points the agent to {@code gabier help}: the one place that decides when there is
 * one and builds it. It is on the standard error after the problem, and the member {@code help} of the problem object
 * with {@code --json}; the key, the problem and the exit code are not touched.
 */
final class HelpPointer {

    /** For a first word that is no command: the guide knows which command does a task. */
    static final String FOR_A_TASK = "Not sure which command? Ask for the task: gabier help --for=\"<task>\"";

    /**
     * The keys where the language server or the edit declined what was asked, or where {@code init-agent} refused a
     * file, a folder or a name: the help lists the other ways.
     */
    private static final Set<String> DECLINED_OPERATIONS = Set.of(
            "language-server-ambiguous-imports",
            "language-server-empty-package",
            "language-server-invalid-name",
            "language-server-no-such-symbol",
            "language-server-not-applicable",
            "language-server-not-creatable",
            "language-server-not-extractable",
            "language-server-not-inlinable",
            "language-server-not-movable",
            "language-server-not-movable-to-package",
            "language-server-unresolved-imports",
            "init-agent-block-unfinished",
            "init-agent-file-exists",
            "init-agent-no-such-folder",
            "init-agent-outside-folder",
            "init-agent-unknown-command",
            "editing-ambiguous-code-action",
            "editing-no-such-code-action",
            "editing-unknown-clean-up");

    private HelpPointer() {}

    /**
     * The pointer after a refusal, of the daemon or of {@code init-agent}, to the command line whose first word is {@code
     * command}.
     */
    static Optional<String> after(Problem refusal, String command) {
        return DECLINED_OPERATIONS.contains(refusal.key())
                ? Optional.of("Usage and other ways: gabier help " + command)
                : Optional.empty();
    }

    /** The pointer after a failure of the command line itself. */
    static Optional<String> after(RuntimeException failure) {
        if (failure instanceof InitAgentException) {
            return after(ProblemMapper.toProblem(failure), InitAgent.COMMAND.commandName());
        }

        return failure instanceof UsageException usage && usage.namesNoCommand()
                ? Optional.of(FOR_A_TASK)
                : Optional.empty();
    }
}
