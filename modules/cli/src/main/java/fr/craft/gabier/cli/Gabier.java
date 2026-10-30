package fr.craft.gabier.cli;

import fr.craft.gabier.protocol.DaemonStatus;
import fr.craft.gabier.protocol.ExitCode;
import fr.craft.gabier.protocol.Problem;
import fr.craft.gabier.protocol.ProblemMapper;
import fr.craft.gabier.protocol.ProtocolVersion;
import fr.craft.gabier.protocol.RpcResponse;
import fr.craft.gabier.protocol.SocketLocation;
import fr.craft.gabier.protocol.UserId;
import fr.craft.gabier.shared.generation.ExcludeFromGeneratedCodeCoverage;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class Gabier {

    private static final String VERSION_COMMAND = "--version";
    private static final String STATUS_COMMAND = "status";
    private static final String STOP_COMMAND = "stop";
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

    private static void execute(
            List<String> arguments, Map<String, String> environment, boolean json, PrintStream out) {
        var command = arguments.size() == 1 ? arguments.getFirst() : "";
        switch (command) {
            case VERSION_COMMAND -> out.println("gabier " + ProtocolVersion.CURRENT);
            case STATUS_COMMAND -> status(socket(environment), json, out);
            case STOP_COMMAND -> stop(socket(environment), json, out);
            default -> throw UsageException.unsupportedArguments(arguments);
        }
    }

    private static Path socket(Map<String, String> environment) {
        return SocketLocation.resolve(environment, UserId.current());
    }

    private static void status(Path socket, boolean json, PrintStream out) {
        var result = ask(socket, STATUS_COMMAND, false);
        var status = DaemonStatus.of(result.result());
        out.println(
                json
                        ? status.toJson()
                        : "gabier-daemon " + status.version() + ", pid " + status.pid() + ", up "
                                + status.uptime().toSeconds() + "s");
    }

    private static void stop(Path socket, boolean json, PrintStream out) {
        ask(socket, STOP_COMMAND, true);
        out.println(json ? "{\"stopped\":true}" : "gabier-daemon stopped");
    }

    /** The result of the method, or the refusal of the daemon thrown. Waits for the daemon to hang up when asked. */
    private static RpcResponse.Success ask(Path socket, String method, boolean awaitEnd) {
        try (var client = DaemonClient.connect(socket)) {
            var response = client.call(method);
            if (response instanceof RpcResponse.Failure failure) {
                throw new DaemonRefusal(failure.problem());
            }
            if (awaitEnd) {
                client.awaitEnd();
            }

            return (RpcResponse.Success) response;
        }
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
