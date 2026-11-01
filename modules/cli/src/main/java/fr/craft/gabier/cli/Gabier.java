package fr.craft.gabier.cli;

import fr.craft.gabier.protocol.ExitCode;
import fr.craft.gabier.protocol.Problem;
import fr.craft.gabier.protocol.ProblemMapper;
import fr.craft.gabier.shared.generation.ExcludeFromGeneratedCodeCoverage;
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
        try {
            execute(
                    arguments.stream()
                            .filter(argument -> !argument.equals(JSON_OPTION))
                            .toList(),
                    environment,
                    json,
                    out);

            return ExitCode.SUCCESS.value();
        } catch (DaemonRefusal refusal) {
            return report(refusal.problem(), json, err);
        } catch (RuntimeException e) {
            return fail(e, json, err);
        }
    }

    /** Runs the capability of the command the first word names; without one, only {@link Standalone} applies. */
    private static void execute(
            List<String> arguments, Map<String, String> environment, boolean json, PrintStream out) {
        var command = arguments.isEmpty() ? Optional.<Command>empty() : Command.named(arguments.getFirst());
        if (command.isEmpty()) {
            Standalone.run(arguments, json, out);

            return;
        }
        Capabilities.of(command.get()).run(new Invocation(Arguments.of(arguments), json, out, environment));
    }

    /** Reports the failure as a problem object on the standard error, as JSON or as text, and gives the exit code. */
    static int fail(RuntimeException failure, boolean json, PrintStream err) {
        return report(ProblemMapper.toProblem(failure), json, err);
    }

    private static int report(Problem problem, boolean json, PrintStream err) {
        err.println(json ? problem.toJson() : problem.toText());

        return ExitCode.of(problem).value();
    }
}
