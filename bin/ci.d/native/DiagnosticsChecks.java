import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/** {@code diagnostics} with no jdtls and with the fake language server, and {@code diagnostics --watch}. */
final class DiagnosticsChecks extends Checks {

    DiagnosticsChecks(Session session) {
        super(session);
    }

    /**
     * The daemon starts jdtls at the first request that needs it, and says in a problem object why it cannot when
     * the launcher is not there; {@code status} then shows the failure and the project, and still starts nothing.
     */
    void diagnosticsWithNoJdtls() throws IOException, InterruptedException {
        Process process = startDaemon();
        if (process == null) {
            failed();
            return;
        }
        Run run = gabier("diagnostics", "Main.java");
        expect(
                run.exit() == 1
                        && run.out().isEmpty()
                        && run.err().startsWith("Jdtls start failed: ")
                        && run.err().contains("no-jdtls")
                        && run.err().contains("(jdtls-start-failed; project="),
                "gabier diagnostics against the installed daemon, whose jdtls cannot start, is a problem object"
                        + " that says why, exit 1");
        Run status = gabier("status");
        expect(
                status.exit() == 0
                        && status.out().contains(", jdtls failed (jdtls did not start: ")
                        && status.out().contains("no-jdtls"),
                "gabier status shows the failed start and why");
        Run again = gabier("diagnostics", "Main.java");
        expect(again.exit() == 1 && again.err().contains("(jdtls-start-failed; "), "the next request tries again");
        gabier("stop");
        expect(ends(process), "the daemon stops");
    }

    /**
     * The commands of the catalogue against the installed daemon served by the fake language server, which answers
     * what the contract scenarios say jdtls answers: no jdtls in this stage. The file is given relative to the
     * directory the cli runs in, as a user types it.
     */
    void diagnosticsWithTheFakeServer() throws IOException, InterruptedException {
        Path project = directory.resolve("project");
        Process process = startDaemon(fakeDaemon, project.toString());
        if (process == null) {
            failed();
            return;
        }
        String broken = "project/src/main/java/demo/Broken.java";
        String message = "Type mismatch: cannot convert from String to int";
        Run error = gabier("diagnostics", broken);
        expect(
                error.exit() == 0 && error.err().isEmpty() && error.out().equals(broken + ":5:16: error: " + message),
                "gabier diagnostics of a file with an error prints it, positions from 1, and exits 0");
        Run warning = gabier("diagnostics", "project/src/main/java/demo/Sloppy.java");
        expect(
                warning.exit() == 0
                        && warning.out()
                                .equals("project/src/main/java/demo/Sloppy.java:5:13: warning:"
                                        + " The value of the local variable unused is not used"),
                "gabier diagnostics of a file with a warning prints it and exits 0");
        Run clean = gabier("diagnostics", "project/src/main/java/demo/Main.java");
        expect(clean.exit() == 0 && clean.out().isEmpty() && clean.err().isEmpty(), "a clean file prints nothing");
        Run json = gabier("--json", "diagnostics", broken);
        expect(
                json.exit() == 0
                        && json.out()
                                .equals("{\"files\":[{\"file\":\"" + directory.resolve(broken)
                                        + "\",\"diagnostics\":[{\"severity\":\"error\",\"line\":4,\"column\":15,"
                                        + "\"message\":\"" + message + "\"}]}]}"),
                "gabier --json diagnostics gives the result with the positions from 0");
        String sloppy = "project/src/main/java/demo/Sloppy.java";
        Run several = gabier("diagnostics", sloppy, "project/src/main/java/demo/Main.java", broken);
        expect(
                several.exit() == 0
                        && several.err().isEmpty()
                        && several.out()
                                .equals(sloppy + ":5:13: warning: The value of the local variable unused is not used\n"
                                        + broken + ":5:16: error: " + message),
                "gabier diagnostics of several files prints the problems of each in the order given, in one call");
        Run all = gabier("diagnostics", "--all");
        String demo = project + "/src/main/java/demo/";
        expect(
                all.exit() == 0
                        && all.err().isEmpty()
                        && all.out()
                                .equals(String.join(
                                        "\n",
                                        demo + "Broken.java:5:16: error: ",
                                        demo + "shop/Sloppy.java:5:13: warning: ",
                                        project + "/src/test/java/demo/BrokenTest.java:5:16: error:")),
                "gabier diagnostics --all prints the problems of every file of the project by absolute path");
        Run allJson = gabier("--json", "diagnostics", "--all");
        expect(
                allJson.exit() == 0
                        && allJson.out().startsWith("{\"files\":[{\"file\":\"" + demo + "Broken.java\"")
                        && !allJson.out().contains("Clean.java"),
                "gabier --json diagnostics --all gives the files that have a problem, and not the clean one");
        Run outside = gabier("diagnostics", "outside/Elsewhere.java");
        expect(
                outside.exit() == 2
                        && outside.out().isEmpty()
                        && outside.err().contains("(language-server-file-outside-project; "),
                "a file outside the project is refused as a problem object, exit 2");
        gabier("stop");
        expect(ends(process), "the daemon served by the fake stops");
    }

    /**
     * {@code gabier diagnostics --watch}, the native binary, against the daemon served by the fake language server: it
     * prints the problems as they are, then, once the file {@code .gabier-fake-edit} appears in the project (the fake's
     * way to edit the files on disk), the files whose problems changed, each line as it comes; SIGTERM ends it with
     * nothing on the standard error; and it ends with the problem of a lost connection when the daemon stops.
     */
    void watchingWithTheFakeServer() throws IOException, InterruptedException {
        Path project = directory.resolve("watch-project");
        Process process = startDaemon(fakeDaemon, project.toString());
        if (process == null) {
            failed();
            return;
        }
        String demo = project + "/src/main/java/demo/";
        Path out = Files.createTempFile(directory, "watch-out", ".txt");
        Path err = Files.createTempFile(directory, "watch-err", ".txt");
        Process watching = startWatch(out, err);
        expect(
                awaitText(out, demo + "Broken.java:5:16: error: "),
                "gabier diagnostics --watch prints the problems as they are, with the command still running");
        expect(watching.isAlive(), "gabier diagnostics --watch keeps running after it printed them");
        Files.writeString(project.resolve(".gabier-fake-edit"), "");
        expect(
                awaitText(out, demo + "Broken.java: no problems") && awaitText(out, demo + "Clean.java:5:16: error: "),
                "gabier diagnostics --watch prints a file that became clean and a file that broke after an edit on"
                        + " disk");
        watching.destroy(); // SIGTERM, to the process we started and to nothing else
        expect(
                watching.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        && watching.exitValue() == 143
                        && Files.readString(err).isEmpty(),
                "gabier diagnostics --watch ends on SIGTERM with the status of the signal and nothing on the standard"
                        + " error");
        Files.delete(project.resolve(".gabier-fake-edit"));
        Path goneOut = Files.createTempFile(directory, "watch-gone-out", ".txt");
        Path goneErr = Files.createTempFile(directory, "watch-gone-err", ".txt");
        Process gone = startWatch(goneOut, goneErr);
        expect(awaitText(goneOut, demo + "Broken.java:5:16: error: "), "a second watch starts on the same daemon");
        gabier("stop");
        expect(
                gone.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        && gone.exitValue() == 1
                        && Files.readString(goneErr).contains("daemon-connection-failed")
                        && !Files.readString(goneErr).contains("\tat "),
                "gabier diagnostics --watch ends with a daemon-connection-failed problem, no stack trace, when the"
                        + " daemon stops");
        expect(ends(process), "the daemon served by the fake stops");
    }

    /** Starts {@code gabier diagnostics --watch} in the background, its streams in these files. */
    Process startWatch(Path out, Path err) throws IOException {
        ProcessBuilder builder =
                new ProcessBuilder(cli.toString(), "diagnostics", "--watch").directory(directory.toFile());
        builder.environment().clear();
        builder.environment().put("GABIER_SOCKET", socket.toString());
        builder.redirectOutput(out.toFile()).redirectError(err.toFile());
        Process process = builder.start();
        started.add(process);

        return process;
    }

    /** Whether the file holds the text within the time-out: what a command that keeps running printed. */
    static boolean awaitText(Path file, String text) throws IOException, InterruptedException {
        for (int attempt = 0; attempt < TIMEOUT_SECONDS * 20; attempt++) {
            if (Files.readString(file).contains(text)) {
                return true;
            }
            Thread.sleep(50);
        }
        System.out.println("not printed in time: " + text + " (printed: " + Files.readString(file) + ")");

        return false;
    }
}
