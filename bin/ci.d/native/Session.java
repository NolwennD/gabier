import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** What the checks of the daemon share: the paths of the stage, the processes they started and the failures. */
final class Session extends Support {

    // The state of the checks of the daemon: the paths of the stage, the processes started and the failures counted.

    final Path cli;
    final Path daemon;
    final Path fakeDaemon;
    final Path directory;
    final Path socket;
    final List<Process> started = new ArrayList<>();
    int failures;

    Session(Path cli, Path daemon, Path fakeDaemon, Path directory) {
        this.cli = cli;
        this.daemon = daemon;
        this.fakeDaemon = fakeDaemon;
        this.directory = directory;
        // The directory is private to the user (a temporary directory is created with mode 700) and short: the
        // path of a socket is limited to 107 bytes.
        this.socket = directory.resolve("gabier.sock");
    }

    void expect(boolean condition, String what) {
        System.out.println((condition ? "ok: " : "FAILED: ") + what);
        if (!condition) {
            failures++;
        }
    }

    Run gabier(String... args) throws IOException, InterruptedException {
        Run run = runBinary(cli, directory, List.of("GABIER_SOCKET", socket.toString()), args);
        System.out.println("gabier " + String.join(" ", args) + " -> stdout \"" + run.out() + "\", stderr \""
                + run.err() + "\" (exit " + run.exit() + ")");
        return run;
    }

    /** The cli with these variables only (pairs of name and value) and the socket of the stage. */
    Run gabierWith(Path binary, List<String> environment, String... args) throws IOException, InterruptedException {
        var all = new ArrayList<>(List.of("GABIER_SOCKET", socket.toString()));
        all.addAll(environment);
        Run run = runBinary(binary, directory, all, args);
        System.out.println("gabier " + String.join(" ", args) + " -> stdout \"" + run.out() + "\", stderr \""
                + run.err() + "\" (exit " + run.exit() + ")");
        return run;
    }

    /**
     * Starts the installed daemon, and waits until it says it is listening (a stale socket file may already be
     * there, so the file does not tell). Null when it did not start.
     */
    Process startDaemon() throws IOException, InterruptedException {
        return startDaemon(daemon);
    }

    /** Starts a daemon of the build (the installed one or the one served by the fake), as above. */
    Process startDaemon(Path executable, String... args) throws IOException, InterruptedException {
        var command = new ArrayList<String>();
        command.add(executable.toString());
        command.addAll(List.of(args));
        ProcessBuilder builder = new ProcessBuilder(command).directory(directory.toFile());
        builder.environment().put("GABIER_SOCKET", socket.toString());
        // The start script runs the java of JAVA_HOME: the JDK that runs this stage.
        builder.environment().put("JAVA_HOME", System.getProperty("java.home"));
        // No jdtls in this stage, whatever the environment of the CI run holds: the launcher is a file that is not
        // there.
        builder.environment().put("GABIER_JDTLS", directory.resolve("no-jdtls").toString());
        Path log = Files.createTempFile(directory, "daemon", ".log");
        builder.redirectErrorStream(true).redirectOutput(log.toFile());
        Process process = builder.start();
        started.add(process);
        for (int attempt = 0; attempt < TIMEOUT_SECONDS * 20; attempt++) {
            if (Files.readString(log).contains(" listening on ")) {
                return process;
            }
            if (!process.isAlive()) {
                break;
            }
            Thread.sleep(50);
        }
        System.out.println("the daemon did not start: " + Files.readString(log));
        process.destroyForcibly();
        return null;
    }

    /** The daemon ends by itself, in a minute at most. */
    boolean ends(Process process) throws InterruptedException {
        return process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    /** The number of checks that failed. */
    int check() throws IOException, InterruptedException {
        var lifecycle = new LifecycleChecks(this);
        var diagnostics = new DiagnosticsChecks(this);
        var rewrites = new RewriteChecks(this);
        var extracts = new ExtractChecks(this);
        var moves = new MoveChecks(this);
        var queries = new QueryChecks(this);
        try {
            lifecycle.noDaemon();
            lifecycle.longPath();
            lifecycle.statusAndStop();
            lifecycle.sigterm();
            lifecycle.secondDaemonAndStaleSocket();
            diagnostics.diagnosticsWithNoJdtls();
            lifecycle.startsTheDaemonOnDemand();
            lifecycle.startWithTheFakeServer();
            diagnostics.diagnosticsWithTheFakeServer();
            diagnostics.watchingWithTheFakeServer();
            diagnostics.watchingTheTestsWithTheFakeServer();
            rewrites.renameWithTheFakeServer();
            rewrites.organizeImportsWithTheFakeServer();
            rewrites.cleanupWithTheFakeServer();
            extracts.extractVariableWithTheFakeServer();
            extracts.extractMethodWithTheFakeServer();
            extracts.extractConstantWithTheFakeServer();
            extracts.extractFieldWithTheFakeServer();
            moves.moveTypeToFileWithTheFakeServer();
            moves.moveToAPackageWithTheFakeServer();
            moves.inlineWithTheFakeServer();
            moves.createWithTheFakeServer();
            queries.referencesWithTheFakeServer();
            queries.actionsWithTheFakeServer();
            queries.affectedTestsWithTheFakeServer();
            lifecycle.fakeServedRefusesAFolderThatIsNotEmpty();
        } finally {
            for (Process process : started) {
                if (process.isAlive()) {
                    process.destroyForcibly();
                }
            }
        }
        return failures;
    }
}
