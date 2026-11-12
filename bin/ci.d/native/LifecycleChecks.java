import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/** The daemon the cli talks to: no daemon, status and stop, signals, a stale socket, start, and a start on demand. */
final class LifecycleChecks extends Checks {

    LifecycleChecks(Session session) {
        super(session);
    }

    private static final Pattern STATUS = Pattern.compile(
            "gabier-daemon " + VERSION + ", pid (\\d+), up \\d+s, project \\S+, jdtls not-started\\nimports:\\n.*",
            Pattern.DOTALL);
    private static final Pattern STATUS_JSON = Pattern.compile(
            "\\{\"version\":\"" + VERSION
                    + "\",\"pid\":(\\d+),\"uptimeMillis\":\\d+,\"project\":\"[^\"]+\",\"jdtls\":\"not-started\",\"imports\":\\{.*\\}\\}");

    /** The status of a daemon the cli started: the project is the folder its script gave. */
    private static final Pattern STARTED_STATUS = Pattern.compile(
            "gabier-daemon " + VERSION + ", pid (\\d+), up \\d+s, project \\S+-project, jdtls \\w+\\nimports:\\n.*",
            Pattern.DOTALL);

    void noDaemon() throws IOException, InterruptedException {
        for (String command : List.of("status", "stop")) {
            Run run = gabier(command);
            expect(
                    run.exit() == 2
                            && run.out().isEmpty()
                            && run.err().startsWith("Daemon not running: ")
                            && run.err().contains("(daemon-not-running; "),
                    "gabier " + command + " with no daemon says so, exit 2, and does not hang");
        }
    }

    void longPath() throws IOException, InterruptedException {
        String tooLong = "/" + "a".repeat(119);
        Run run = runBinary(cli, directory, List.of("GABIER_SOCKET", tooLong), "status");
        System.out.println(
                "gabier status (path of 120 bytes) -> stderr \"" + run.err() + "\" (exit " + run.exit() + ")");
        expect(
                run.exit() == 2 && run.err().contains("(socket-path-too-long; "),
                "a socket path over 107 bytes is refused before any connection, exit 2");
    }

    void statusAndStop() throws IOException, InterruptedException {
        Process process = startDaemon();
        if (process == null) {
            failed();
            return;
        }
        Run status = gabier("status");
        var text = STATUS.matcher(status.out());
        expect(
                status.exit() == 0 && text.matches() && text.group(1).equals(String.valueOf(process.pid())),
                "gabier status reports the pid of the process that was started (the script execs the JVM)");
        Run json = gabier("--json", "status");
        var jsonMatch = STATUS_JSON.matcher(json.out());
        expect(
                json.exit() == 0 && jsonMatch.matches() && jsonMatch.group(1).equals(String.valueOf(process.pid())),
                "gabier --json status gives the result as JSON");
        expect(
                posix(socket).equals("rw-------") && posix(directory).equals("rwx------"),
                "the socket and its folder are private to the user");

        Run stop = gabier("stop");
        expect(
                stop.exit() == 0 && stop.out().equals("gabier-daemon stopped"),
                "gabier stop exits 0 and says the daemon stopped");
        expect(
                ends(process) && process.exitValue() == 0,
                "the daemon process has exited with status 0 once gabier stop returned");
        expect(ProcessHandle.of(process.pid()).isEmpty(), "no process of the daemon is left");
        expect(!Files.exists(socket), "the socket is removed by stop");
        Run after = gabier("status");
        expect(after.exit() == 2 && after.err().contains("(daemon-not-running; "), "no daemon after stop");
    }

    void sigterm() throws IOException, InterruptedException {
        Process process = startDaemon();
        if (process == null) {
            failed();
            return;
        }
        process.destroy(); // SIGTERM, to the process we started and to nothing else
        expect(ends(process), "the daemon ends on SIGTERM");
        expect(!Files.exists(socket), "the socket is removed on SIGTERM");
        expect(ProcessHandle.of(process.pid()).isEmpty(), "no process of the daemon is left after SIGTERM");
    }

    void secondDaemonAndStaleSocket() throws IOException, InterruptedException {
        Process first = startDaemon();
        if (first == null) {
            failed();
            return;
        }
        // A second daemon on the same socket refuses to start, and the first one is not disturbed.
        Path log = Files.createTempFile(directory, "second", ".log");
        ProcessBuilder builder = new ProcessBuilder(daemon.toString()).directory(directory.toFile());
        builder.environment().put("GABIER_SOCKET", socket.toString());
        builder.environment().put("JAVA_HOME", System.getProperty("java.home"));
        Process second =
                builder.redirectErrorStream(true).redirectOutput(log.toFile()).start();
        started.add(second);
        boolean ended = ends(second);
        String output = Files.readString(log);
        System.out.println("second daemon -> " + output.strip() + " (exit " + (ended ? second.exitValue() : -1) + ")");
        expect(
                ended && second.exitValue() == 2 && output.contains("(daemon-already-running; "),
                "a second daemon refuses to start while one answers, exit 2");
        expect(gabier("status").exit() == 0, "the first daemon still answers");

        // A daemon that was killed leaves its socket file: the next one removes it.
        first.destroyForcibly();
        ends(first);
        expect(Files.exists(socket), "a killed daemon leaves its socket file behind");
        Process third = startDaemon();
        if (third == null) {
            failed();
            return;
        }
        Run status = gabier("status");
        expect(
                status.exit() == 0 && status.out().contains("pid " + third.pid()),
                "a daemon removes a stale socket and serves on it");
        gabier("stop");
        expect(ends(third), "the third daemon stops");
    }

    /** {@code gabier start} starts the daemon of the project and answers; the fake-served jdtls is always ready. */
    void startWithTheFakeServer() throws IOException, InterruptedException {
        Path project = directory.resolve("start-project");
        String system = "/usr/bin:/bin";
        Path onPath = directory.resolve("start-on-path");
        installDaemonScript(onPath, "exec '" + fakeDaemon + "' '" + project + "'");
        List<String> withPath = List.of("PATH", onPath + ":" + system, "JAVA_HOME", System.getProperty("java.home"));
        Path log = directory.resolve("gabier.sock.log");
        Long pid = null;
        try {
            Run first = gabierWith(cli, withPath, "start", "--wait");
            expect(
                    first.exit() == 0
                            && first.err().isEmpty()
                            && first.out().startsWith("gabier-daemon started, pid ")
                            && first.out().contains(", jdtls ready; socket " + socket + ", log " + log),
                    "gabier start --wait with no daemon starts it and answers once jdtls is ready, naming the socket"
                            + " and the log");
            var status =
                    STARTED_STATUS.matcher(gabierWith(cli, withPath, "status").out());
            if (status.matches()) {
                pid = Long.parseLong(status.group(1));
            }
            Run again = gabierWith(cli, withPath, "--json", "start");
            expect(
                    again.exit() == 0
                            && again.out().startsWith("{\"daemon\":\"running\",")
                            && again.out().contains("\"jdtls\":\"ready\"")
                            && !again.out().contains("\"log\""),
                    "gabier start with a daemon that runs starts none and answers ready as JSON");
            expect(gabierWith(cli, withPath, "stop").exit() == 0, "gabier stop ends the daemon that start started");
            expect(
                    pid == null
                            || ProcessHandle.of(pid)
                                    .map(LifecycleChecks::awaitExit)
                                    .orElse(true),
                    "no process of the daemon is left after start");
            pid = null;
        } finally {
            if (pid != null) {
                ProcessHandle.of(pid).ifPresent(ProcessHandle::destroyForcibly);
            }
            Files.deleteIfExists(log);
        }
    }

    /**
     * A request command with no daemon running starts {@code gabier-daemon}, found on the {@code PATH} or next to
     * the binary of the cli, waits for its socket and answers; the next one uses the daemon that runs; a daemon that
     * is not found or does not start is a problem object. The daemon is the one served by the fake language server
     * (the script gives it its project folder), and what the cli started is ended with {@code gabier stop}.
     */
    void startsTheDaemonOnDemand() throws IOException, InterruptedException {
        Path project = directory.resolve("started-project");
        String broken = "started-project/src/main/java/demo/Broken.java";
        String expected = broken + ":5:16: error: Type mismatch: cannot convert from String to int";
        String system = "/usr/bin:/bin";
        // The start script of the daemon needs the java of JAVA_HOME and the tools of the system.
        String java = System.getProperty("java.home");
        Path onPath = directory.resolve("on-path");
        installDaemonScript(onPath, "exec '" + fakeDaemon + "' '" + project + "'");
        List<String> withPath = List.of("PATH", onPath + ":" + system, "JAVA_HOME", java);
        Path log = directory.resolve("gabier.sock.log");
        Long pid = null;
        try {
            expect(!Files.exists(socket), "no daemon runs before the first request");
            Run first = gabierWith(cli, withPath, "diagnostics", broken);
            expect(
                    first.exit() == 0 && first.err().isEmpty() && first.out().equals(expected),
                    "gabier diagnostics with no daemon starts gabier-daemon from the PATH and answers");
            Run status = gabierWith(cli, withPath, "status");
            var running = STARTED_STATUS.matcher(status.out());
            expect(
                    status.exit() == 0 && running.matches(),
                    "the daemon the cli started answers status, serving the project of its script");
            if (running.matches()) {
                pid = Long.parseLong(running.group(1));
            }
            expect(
                    Files.exists(log) && Files.readString(log).contains(" listening on " + socket),
                    "the output of the daemon went to the log next to the socket");
            expect(Files.exists(log) && posix(log).equals("rw-------"), "the log is private to the user");
            Run second = gabierWith(cli, withPath, "diagnostics", broken);
            Run again = gabierWith(cli, withPath, "status");
            var sameDaemon = STARTED_STATUS.matcher(again.out());
            expect(
                    second.exit() == 0
                            && second.out().equals(expected)
                            && sameDaemon.matches()
                            && pid != null
                            && sameDaemon.group(1).equals(String.valueOf(pid)),
                    "a second request uses the daemon that runs and starts none");
            Run stop = gabierWith(cli, withPath, "stop");
            expect(stop.exit() == 0, "gabier stop ends the daemon the cli started");
            expect(
                    pid == null
                            || ProcessHandle.of(pid)
                                    .map(LifecycleChecks::awaitExit)
                                    .orElse(true),
                    "no process of the daemon is left");
            pid = null;
        } finally {
            if (pid != null) {
                ProcessHandle.of(pid).ifPresent(ProcessHandle::destroyForcibly);
            }
            Files.deleteIfExists(log);
        }

        // Next to the binary of the cli, with nothing on the PATH.
        Path own = Files.createDirectories(directory.resolve("own"));
        Path ownCli = Files.copy(cli, own.resolve("gabier"));
        Path besideProject = directory.resolve("own-project");
        installDaemonScript(own, "exec '" + fakeDaemon + "' '" + besideProject + "'");
        List<String> noPath = List.of("PATH", system, "JAVA_HOME", java);
        String besideBroken = "own-project/src/main/java/demo/Broken.java";
        Run beside = gabierWith(ownCli, noPath, "diagnostics", besideBroken);
        expect(
                beside.exit() == 0
                        && beside.out()
                                .equals(besideBroken
                                        + ":5:16: error: Type mismatch: cannot convert from String to int"),
                "gabier-daemon next to the binary of the cli is found without the PATH");
        var besidePid =
                STARTED_STATUS.matcher(gabierWith(ownCli, noPath, "status").out());
        gabierWith(ownCli, noPath, "stop");
        expect(
                !besidePid.matches()
                        || ProcessHandle.of(Long.parseLong(besidePid.group(1)))
                                .map(LifecycleChecks::awaitExit)
                                .orElse(true),
                "the daemon started from next to the cli ends with stop");
        Files.deleteIfExists(log);

        // Not found, and found but not coming up: problem objects, exit 1, nothing left running.
        Run missing = gabierWith(cli, noPath, "diagnostics", broken);
        expect(
                missing.exit() == 1
                        && missing.out().isEmpty()
                        && missing.err().startsWith("Daemon start failed: ")
                        && missing.err().contains("gabier-daemon was not found")
                        && missing.err().contains("(daemon-start-failed; socket=")
                        && !Files.exists(socket),
                "a gabier-daemon that is not found is a problem object, exit 1, and starts nothing");
        Path failing = directory.resolve("failing");
        installDaemonScript(failing, "echo 'refusing to start'; exit 5");
        Run exits = gabierWith(cli, List.of("PATH", failing + ":" + system, "JAVA_HOME", java), "diagnostics", broken);
        expect(
                exits.exit() == 1
                        && exits.err().contains("exited with code 5 before it listened")
                        && exits.err().contains("log=" + log)
                        && Files.readString(log).equals("refusing to start\n")
                        && !Files.exists(socket),
                "a gabier-daemon that exits at once is a problem object that says its exit code and where its"
                        + " output went");
        Files.deleteIfExists(log);

        // status and stop never start one, even with a gabier-daemon on the PATH.
        for (String command : List.of("status", "stop")) {
            Run run = gabierWith(cli, withPath, command);
            expect(
                    run.exit() == 2
                            && run.err().contains("(daemon-not-running; ")
                            && !Files.exists(socket)
                            && !Files.exists(log),
                    "gabier " + command + " with no daemon still says so at once, whatever is on the PATH");
        }
    }

    /** The daemon served by the fake writes the scenarios in its folder: it refuses one that holds anything. */
    void fakeServedRefusesAFolderThatIsNotEmpty() throws IOException, InterruptedException {
        Path occupied = Files.createDirectory(directory.resolve("occupied"));
        Path mine = Files.writeString(occupied.resolve("Main.java"), "class Main {}");
        Path log = Files.createTempFile(directory, "occupied", ".log");
        ProcessBuilder builder =
                new ProcessBuilder(fakeDaemon.toString(), occupied.toString()).directory(directory.toFile());
        builder.environment().put("GABIER_SOCKET", socket.toString());
        builder.environment().put("JAVA_HOME", System.getProperty("java.home"));
        Process process =
                builder.redirectErrorStream(true).redirectOutput(log.toFile()).start();
        started.add(process);
        boolean ended = ends(process);
        String output = Files.readString(log);
        System.out.println("daemon served by the fake on a folder that is not empty -> " + output.strip() + " (exit "
                + (ended ? process.exitValue() : -1) + ")");
        expect(
                ended
                        && process.exitValue() == 2
                        && output.contains("is not empty")
                        && Files.readString(mine).equals("class Main {}")
                        && !Files.exists(occupied.resolve("src")),
                "the daemon served by the fake refuses a folder that is not empty, exit 2, and leaves its files alone");
    }

    /** A {@code gabier-daemon} in the folder: a script with this body, which the cli finds and launches. */
    static Path installDaemonScript(Path folder, String body) throws IOException {
        Path script = Files.writeString(
                Files.createDirectories(folder).resolve("gabier-daemon"), "#!/bin/sh\n" + body + "\n");
        script.toFile().setExecutable(true);
        return script;
    }

    /** True once the process has ended, in a minute at most. */
    static boolean awaitExit(ProcessHandle handle) {
        try {
            handle.onExit().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static String posix(Path path) throws IOException {
        return java.nio.file.attribute.PosixFilePermissions.toString(Files.getPosixFilePermissions(path));
    }
}
