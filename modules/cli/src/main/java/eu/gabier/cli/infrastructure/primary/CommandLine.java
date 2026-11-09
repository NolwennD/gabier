package eu.gabier.cli.infrastructure.primary;

import eu.gabier.cli.Gabier;
import eu.gabier.cli.application.OperationRunner;
import eu.gabier.cli.application.Problems;
import eu.gabier.cli.domain.Daemon;
import eu.gabier.cli.domain.DaemonRefusal;
import eu.gabier.cli.domain.Projects;
import eu.gabier.protocol.ExitCode;
import eu.gabier.protocol.Problem;
import eu.gabier.protocol.ProblemMapper;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.Optional;

/**
 * The command line of gabier as a primary adapter: the words a person typed in, the text or the JSON and the exit code
 * that come out. It knows the daemon only as a port, which the composition root ({@link Gabier}) hands to it.
 */
public final class CommandLine {

    private static final String JSON_OPTION = "--json";

    private CommandLine() {}

    /**
     * Runs the command the arguments name, and gives the exit code.
     *
     * @param args the words of the command line
     * @param in the standard input, which {@code mcp} reads until it is closed
     * @param out where the result is printed
     * @param err where a problem is printed
     * @param daemon the way to the daemon of the project
     * @param projects the way to the daemon of any project, for {@code mcp}, which is not run in one
     */
    public static int run(
            String[] args, InputStream in, PrintStream out, PrintStream err, Daemon daemon, Projects projects) {
        var arguments = List.of(args);
        var json = arguments.contains(JSON_OPTION);
        var words = arguments.stream()
                .filter(argument -> !argument.equals(JSON_OPTION))
                .toList();
        try {
            execute(words, json, in, out, err, daemon, projects);

            return ExitCode.SUCCESS.value();
        } catch (DaemonRefusal refusal) {
            var problem = Problems.of(refusal);

            return report(problem, HelpPointer.after(problem, words.getFirst()), json, err);
        } catch (RuntimeException e) {
            return fail(e, json, err);
        }
    }

    /** Runs the capability of the command the first word names; without one, only {@link Standalone} applies. */
    private static void execute(
            List<String> arguments,
            boolean json,
            InputStream in,
            PrintStream out,
            PrintStream err,
            Daemon daemon,
            Projects projects) {
        var capability = arguments.isEmpty() ? Optional.<Capability>empty() : Capabilities.named(arguments.getFirst());
        if (capability.isEmpty()) {
            Standalone.run(arguments, json, out);

            return;
        }
        var words = Arguments.of(arguments);
        if (words.wantsHelp()) {
            out.println(HelpText.of(capability.get().command(), json));

            return;
        }
        capability.get().run(new Invocation(words, json, out, new OperationRunner(daemon), daemon, projects, in, err));
    }

    /** Reports the failure as a problem object on the standard error, as JSON or as text, and gives the exit code. */
    static int fail(RuntimeException failure, boolean json, PrintStream err) {
        return report(ProblemMapper.toProblem(failure), HelpPointer.after(failure), json, err);
    }

    private static int report(Problem problem, Optional<String> pointer, boolean json, PrintStream err) {
        if (json) {
            err.println(pointer.map(problem::toJson).orElseGet(problem::toJson));
        } else {
            err.println(problem.toText());
            pointer.ifPresent(err::println);
        }

        return ExitCode.of(problem).value();
    }
}
