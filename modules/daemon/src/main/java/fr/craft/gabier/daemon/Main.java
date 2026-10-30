package fr.craft.gabier.daemon;

import fr.craft.gabier.editing.domain.JavaLanguageServer;
import fr.craft.gabier.protocol.ExitCode;
import fr.craft.gabier.protocol.ProblemMapper;
import fr.craft.gabier.protocol.ProtocolVersion;
import fr.craft.gabier.protocol.SocketLocation;
import fr.craft.gabier.protocol.UserId;
import fr.craft.gabier.shared.generation.ExcludeFromGeneratedCodeCoverage;
import java.io.IOException;
import java.io.PrintStream;
import java.time.Clock;
import java.util.List;
import java.util.Map;

public final class Main {

    private static final String VERSION_OPTION = "--version";
    private static final String SELF_CHECK_OPTION = "--self-check";
    private static final int FAILED = 1;

    private Main() {}

    @ExcludeFromGeneratedCodeCoverage(
            reason = "The entry point ends in System.exit, which a unit test cannot cover: "
                    + "all its logic is in run, which is tested")
    public static void main(String[] args) {
        System.exit(run(args, System.getenv(), SelfCheck.ofThisRuntime(), System.out, System.err));
    }

    /**
     * Without argument, serves on the socket until it is stopped. A refusal (the socket path, another daemon, a socket
     * that is not the user's own) is reported as a problem object on the standard error, with the exit code of its
     * category.
     */
    static int run(
            String[] args, Map<String, String> environment, SelfCheck selfCheck, PrintStream out, PrintStream err) {
        try {
            return execute(List.of(args), environment, selfCheck, out, err);
        } catch (IOException | RuntimeException failure) {
            var problem = ProblemMapper.toProblem(failure);
            err.println(problem.toText());

            return ExitCode.of(problem).value();
        }
    }

    private static int execute(
            List<String> arguments,
            Map<String, String> environment,
            SelfCheck selfCheck,
            PrintStream out,
            PrintStream err)
            throws IOException {
        if (arguments.isEmpty()) {
            return serve(environment, out, new NoProjectServer());
        }
        if (arguments.equals(List.of(VERSION_OPTION))) {
            out.println("gabier-daemon " + ProtocolVersion.CURRENT);

            return ExitCode.SUCCESS.value();
        }
        if (arguments.equals(List.of(SELF_CHECK_OPTION))) {
            return runSelfCheck(selfCheck, out, err);
        }

        throw UsageException.unsupportedArguments(arguments);
    }

    /**
     * Serves on the socket, with this language server behind the methods of the catalogue, until the daemon is stopped.
     * Apart from {@link #execute}, only the distribution that the {@code native} stage of {@code bin/ci} runs calls it
     * (source set {@code fakeServed}, with the fake language server): the way the daemon picks its server is not in
     * its command line nor in its requests.
     */
    static int serve(Map<String, String> environment, PrintStream out, JavaLanguageServer languageServer)
            throws IOException {
        var uid = UserId.current();
        var socket = SocketLocation.resolve(environment, uid);
        var server = DaemonServer.open(
                socket, uid, Clock.systemUTC(), ProcessHandle.current().pid(), languageServer);
        // A signal (SIGTERM) ends the JVM without serve() returning: the hook removes the socket.
        Runtime.getRuntime().addShutdownHook(server.shutdownHook());
        out.println("gabier-daemon " + ProtocolVersion.CURRENT + " listening on " + socket);
        server.serve();

        return ExitCode.SUCCESS.value();
    }

    private static int runSelfCheck(SelfCheck selfCheck, PrintStream out, PrintStream err) {
        var problems = selfCheck.problems();
        if (problems.isEmpty()) {
            out.println("self-check ok: " + selfCheck.summary());
            return ExitCode.SUCCESS.value();
        }
        err.println("self-check failed: " + String.join("; ", problems));
        return FAILED;
    }
}
