package fr.craft.gabier.cli;

import fr.craft.gabier.protocol.Problem;
import fr.craft.gabier.protocol.ProblemMapper;
import fr.craft.gabier.protocol.ProtocolVersion;
import fr.craft.gabier.shared.generation.ExcludeFromGeneratedCodeCoverage;
import java.io.PrintStream;
import java.util.List;

public final class Gabier {

    private static final String VERSION_OPTION = "--version";
    private static final String JSON_OPTION = "--json";

    private Gabier() {}

    @ExcludeFromGeneratedCodeCoverage(
            reason = "The entry point ends in System.exit, which a unit test cannot cover: "
                    + "all its logic is in run, which is tested")
    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err));
    }

    static int run(String[] args, PrintStream out, PrintStream err) {
        var arguments = List.of(args);
        var json = arguments.contains(JSON_OPTION);
        try {
            execute(
                    arguments.stream()
                            .filter(argument -> !argument.equals(JSON_OPTION))
                            .toList(),
                    out);

            return ExitCode.SUCCESS.value();
        } catch (RuntimeException e) {
            return fail(e, json, err);
        }
    }

    private static void execute(List<String> arguments, PrintStream out) {
        if (!arguments.equals(List.of(VERSION_OPTION))) {
            throw UsageException.unsupportedArguments(arguments);
        }
        out.println("gabier " + ProtocolVersion.CURRENT);
    }

    /** Reports the failure as a problem object on the standard error, as JSON or as text, and gives the exit code. */
    static int fail(RuntimeException failure, boolean json, PrintStream err) {
        Problem problem = ProblemMapper.toProblem(failure);
        err.println(json ? problem.toJson() : problem.toText());

        return ExitCode.of(problem).value();
    }
}
