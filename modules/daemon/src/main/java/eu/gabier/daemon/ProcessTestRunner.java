package eu.gabier.daemon;

import eu.gabier.testing.domain.TestFailure;
import eu.gabier.testing.domain.TestReport;
import eu.gabier.testing.domain.TestRun;
import eu.gabier.testing.domain.TestRunner;
import eu.gabier.testing.domain.TestingException;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Runs the tests in a JVM of its own, started with the {@code java} of the daemon: the class path of the project, then the
 * jar that holds {@link TestRunnerMain}, which uses the JUnit Platform the project brings ({@link JUnitPlatform}). The
 * daemon never loads a test, a test that never ends or stops the JVM costs only its process, and the project's JUnit does not
 * meet the libraries of the daemon. Everything the tests print is written to a file and kept only to say why a JVM ended with
 * no report.
 */
final class ProcessTestRunner implements TestRunner {

    private static final int LAST_LINES = 20;

    private final Path java;
    private final Path runnerEntry;

    /**
     * Creates a runner.
     *
     * @param java the {@code java} launcher the tests run with
     * @param runnerEntry the jar or the folder that holds {@link TestRunnerMain}, which goes last on the class path
     */
    ProcessTestRunner(Path java, Path runnerEntry) {
        this.java = java;
        this.runnerEntry = runnerEntry;
    }

    /** The runner of this daemon: its own {@code java}, and the jar it was started from. */
    static ProcessTestRunner ofThisDaemon() {
        Path java = Path.of(System.getProperty("java.home"), "bin", "java");
        var source = Objects.requireNonNull(
                TestRunnerMain.class.getProtectionDomain().getCodeSource(), "the daemon has a code source");

        return new ProcessTestRunner(
                java, Path.of(URI.create(source.getLocation().toString())));
    }

    @Override
    public TestReport run(TestRun run) {
        try {
            return runWithFiles(run);
        } catch (IOException failure) {
            throw TestingException.notStarted(failure);
        }
    }

    private TestReport runWithFiles(TestRun run) throws IOException {
        Path report = Files.createTempFile("gabier-tests-", ".report");
        Files.delete(report);
        Path output = Files.createTempFile("gabier-tests-", ".log");
        try {
            Process process = start(run, report, output);
            if (!finishedIn(process, run)) {
                throw TestingException.timedOut(run.timeLimit());
            }

            return read(report, output);
        } finally {
            // Temporary files: one that stays is the system's to clean.
            report.toFile().delete();
            output.toFile().delete();
        }
    }

    private Process start(TestRun run, Path report, Path output) throws IOException {
        List<Path> classpath = new ArrayList<>(run.classpath());
        classpath.add(runnerEntry);
        String folders = run.classpath().stream()
                .filter(Files::isDirectory)
                .map(Path::toString)
                .collect(Collectors.joining(File.pathSeparator));
        List<String> command = new ArrayList<>();
        command.add(java.toString());
        command.addAll(jvmArguments(classpath));
        command.addAll(List.of(TestRunnerMain.class.getName(), report.toString(), folders));
        command.addAll(run.classes());

        return new ProcessBuilder(command)
                .directory(run.workingDirectory().toFile())
                .redirectErrorStream(true)
                .redirectOutput(output.toFile())
                .start();
    }

    /**
     * The arguments of the JVM of the tests, before the main class: the one place they are built, so that an option of
     * the JVM (a cache of classes, a limit of memory) is added here and nowhere else. Only the class path for now.
     */
    private static List<String> jvmArguments(List<Path> classpath) {
        return List.of("-cp", classpath.stream().map(Path::toString).collect(Collectors.joining(File.pathSeparator)));
    }

    /** Waits for the process to end; one that does not within the time limit is killed, with what it started. */
    private static boolean finishedIn(Process process, TestRun run) {
        try {
            if (process.waitFor(run.timeLimit().toMillis(), TimeUnit.MILLISECONDS)) {
                return true;
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            kill(process);
            throw TestingException.endedWithoutReport("the daemon was interrupted while the tests ran");
        }
        kill(process);

        return false;
    }

    private static void kill(Process process) {
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
    }

    private static TestReport read(Path file, Path output) throws IOException {
        if (!Files.exists(file)) {
            throw TestingException.endedWithoutReport(lastLinesOf(output));
        }
        RunnerReport report = RunnerReport.parse(Files.readAllLines(file, StandardCharsets.UTF_8));
        if (report.launcherMissing()) {
            throw TestingException.noTestLauncher();
        }
        if (!report.unrunnable().isEmpty()) {
            throw TestingException.noTestEngine(report.unrunnable());
        }

        return new TestReport(
                report.passed(),
                report.skipped(),
                report.failures().stream()
                        .map(failure -> new TestFailure(
                                failure.testClass(),
                                nonEmpty(failure.method()),
                                failure.exception(),
                                failure.message(),
                                nonEmpty(failure.where())))
                        .toList());
    }

    private static Optional<String> nonEmpty(String text) {
        return text.isEmpty() ? Optional.empty() : Optional.of(text);
    }

    private static String lastLinesOf(Path output) throws IOException {
        List<String> lines = new String(Files.readAllBytes(output), StandardCharsets.UTF_8)
                .lines()
                .toList();

        return String.join("\n", lines.subList(Math.max(0, lines.size() - LAST_LINES), lines.size()));
    }
}
