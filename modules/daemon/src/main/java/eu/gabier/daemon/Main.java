package eu.gabier.daemon;

import eu.gabier.protocol.ExitCode;
import eu.gabier.protocol.ProblemMapper;
import eu.gabier.protocol.ProtocolVersion;
import eu.gabier.protocol.SocketLocation;
import eu.gabier.protocol.TerminalText;
import eu.gabier.protocol.UserId;
import eu.gabier.shared.generation.ExcludeFromGeneratedCodeCoverage;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
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
        System.exit(run(
                args,
                System.getenv(),
                Path.of("").toAbsolutePath(),
                SelfCheck.ofThisRuntime(),
                System.out,
                System.err));
    }

    /**
     * Without argument, or with the folder of a project, serves that project on the socket until it is stopped; the
     * project is the argument, else {@code GABIER_PROJECT}, else the nearest folder that holds a {@code .git} from the
     * working directory up, else the working directory, and the socket is the one of that project
     * ({@link SocketLocation}). A refusal (the project, the
     * socket path, another daemon, a socket that is not the user's own) is reported as a problem object on the
     * standard error, with the exit code of its category.
     *
     * @param workingDirectory where the daemon was started: the project by default, and what a relative argument is
     *     relative to
     */
    static int run(
            String[] args,
            Map<String, String> environment,
            Path workingDirectory,
            SelfCheck selfCheck,
            PrintStream out,
            PrintStream err) {
        try {
            return execute(List.of(args), environment, workingDirectory, selfCheck, out, err);
        } catch (IOException | RuntimeException failure) {
            var problem = ProblemMapper.toProblem(failure);
            err.println(problem.toText());

            return ExitCode.of(problem).value();
        }
    }

    private static int execute(
            List<String> arguments,
            Map<String, String> environment,
            Path workingDirectory,
            SelfCheck selfCheck,
            PrintStream out,
            PrintStream err)
            throws IOException {
        if (arguments.isEmpty()
                || arguments.size() == 1 && !arguments.getFirst().startsWith("-")) {
            var root = projectRoot(arguments, environment, workingDirectory);

            // What jdtls says is shown as text: a terminal that tails the log must not obey it.
            return serve(
                    socketEnvironment(arguments, environment, root),
                    workingDirectory,
                    out,
                    ProjectServer.jdtls(
                            root, environment, line -> err.println("jdtls: " + TerminalText.neutralise(line))));
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
     * The folder of the project: the argument, else {@code GABIER_PROJECT}, else the nearest folder from where the daemon
     * was started that holds a {@code .git}, else where it was started ({@link SocketLocation#projectRoot}).
     */
    private static Path projectRoot(List<String> arguments, Map<String, String> environment, Path workingDirectory)
            throws IOException {
        var folder = arguments.isEmpty()
                ? SocketLocation.projectRoot(environment, workingDirectory)
                : workingDirectory.resolve(arguments.getFirst());
        if (!Files.isDirectory(folder)) {
            throw UsageException.notAFolder(folder);
        }

        return folder.toRealPath();
    }

    /** The environment the socket is placed by: a project given as the argument is the project of the socket too. */
    private static Map<String, String> socketEnvironment(
            List<String> arguments, Map<String, String> environment, Path root) {
        if (arguments.isEmpty()) {
            return environment;
        }
        var named = new HashMap<>(environment);
        named.put(SocketLocation.PROJECT, root.toString());

        return named;
    }

    /**
     * Serves on the socket, with the language server of this project behind the methods of the catalogue, until the
     * daemon is stopped, and ends that server then. Apart from {@link #execute}, only the distribution that the {@code
     * native} stage of {@code bin/ci} runs calls it (source set {@code fakeServed}, with the fake language server): the
     * way the daemon picks its server is not in its command line nor in its requests.
     */
    static int serve(Map<String, String> environment, Path workingDirectory, PrintStream out, ServedProject project)
            throws IOException {
        var uid = UserId.current();
        var socket = SocketLocation.resolve(environment, uid, workingDirectory);
        var server = DaemonServer.open(
                socket, uid, System::nanoTime, ProcessHandle.current().pid(), project);
        // A signal (SIGTERM) ends the JVM without serve() returning: the hook removes the socket.
        Runtime.getRuntime().addShutdownHook(server.shutdownHook());
        out.println("gabier-daemon " + ProtocolVersion.CURRENT + " listening on " + socket);
        out.println("serving the project " + project.root());
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
