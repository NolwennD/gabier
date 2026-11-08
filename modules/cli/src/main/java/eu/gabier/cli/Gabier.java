package eu.gabier.cli;

import eu.gabier.protocol.ExitCode;
import eu.gabier.protocol.Problem;
import eu.gabier.protocol.ProblemMapper;
import eu.gabier.shared.generation.ExcludeFromGeneratedCodeCoverage;
import java.io.PrintStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class Gabier {

    private static final String JSON_OPTION = "--json";

    private Gabier() {}

    @ExcludeFromGeneratedCodeCoverage(
            reason = "The entry point ends in System.exit, which a unit test cannot cover: "
                    + "all its logic is in run, which is tested")
    public static void main(String[] args) {
        System.exit(run(args, System.getenv(), System.out, System.err));
    }

    static int run(String[] args, Map<String, String> environment, PrintStream out, PrintStream err) {
        var arguments = List.of(args);
        var json = arguments.contains(JSON_OPTION);
        var words = arguments.stream()
                .filter(argument -> !argument.equals(JSON_OPTION))
                .toList();
        try {
            execute(words, environment, json, out);

            return ExitCode.SUCCESS.value();
        } catch (DaemonRefusal refusal) {
            return report(refusal.problem(), HelpPointer.after(refusal.problem(), words.getFirst()), json, err);
        } catch (RuntimeException e) {
            return fail(e, json, err);
        }
    }

    /** Runs the capability of the command the first word names; without one, only {@link Standalone} applies. */
    private static void execute(
            List<String> arguments, Map<String, String> environment, boolean json, PrintStream out) {
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
        capability.get().run(new Invocation(words, json, out, environment));
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
