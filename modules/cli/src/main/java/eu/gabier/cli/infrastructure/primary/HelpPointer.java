package eu.gabier.cli.infrastructure.primary;

import eu.gabier.cli.application.Problems;
import eu.gabier.protocol.Problem;
import eu.gabier.protocol.ProblemMapper;
import java.util.Optional;
import java.util.Set;

/**
 * The line that ends a refusal and points the agent to {@code gabier help}: the one place of the command line that
 * decides when there is one. It is on the standard error after the problem, and the member {@code help} of the problem
 * object with {@code --json}; the key, the problem and the exit code are not touched. The refusals of the daemon are
 * pointed by {@link Problems}, which any front end uses; the command line adds those of {@code init-agent} and of a
 * first word that names no command.
 */
final class HelpPointer {

    /** For a first word that is no command: the guide knows which command does a task. */
    static final String FOR_A_TASK = "Not sure which command? Ask for the task: gabier help --for=\"<task>\"";

    /** The keys where {@code init-agent} refused a file, a folder or a name: the help lists the other ways. */
    private static final Set<String> DECLINED_AGENT_FILES = Set.of(
            "init-agent-block-repeated",
            "init-agent-block-unfinished",
            "init-agent-fence-unclosed",
            "init-agent-file-exists",
            "init-agent-no-such-folder",
            "init-agent-outside-folder",
            "init-agent-unknown-command");

    private HelpPointer() {}

    /** The pointer after a refusal of the daemon to the command line whose first word is {@code command}. */
    static Optional<String> after(Problem refusal, String command) {
        return Problems.pointer(refusal, command);
    }

    /** The pointer after a failure of the command line itself. */
    static Optional<String> after(RuntimeException failure) {
        if (failure instanceof InitAgentException) {
            var refusal = ProblemMapper.toProblem(failure);

            return DECLINED_AGENT_FILES.contains(refusal.key())
                    ? Optional.of(Problems.pointerTo(InitAgent.COMMAND.commandName()))
                    : Optional.empty();
        }

        return failure instanceof UsageException usage && usage.namesNoCommand()
                ? Optional.of(FOR_A_TASK)
                : Optional.empty();
    }
}
