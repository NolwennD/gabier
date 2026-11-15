package eu.gabier.daemon;

import eu.gabier.shared.generation.ExcludeFromGeneratedCodeCoverage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The program the process of the tests runs: it runs test classes with the JUnit Platform of the class path it is
 * started with (the class path of the project, then the jar of the daemon, which holds this class), and writes what
 * happened in a file ({@link RunnerReport}). It uses no library: the JUnit Platform is reached by reflection
 * ({@link JUnitPlatform}), so it is the project's own, at its own version.
 *
 * <p>Arguments: the file of the report, the class folders of the project ({@link File#pathSeparator} between them, the
 * place of a failure is the first frame of its stack that is in one of them), then the binary names of the test
 * classes. The exit code is 0 when the report is written, whatever it says.
 */
public final class TestRunnerMain {

    private static final Pattern PATH_SEPARATOR = Pattern.compile(Pattern.quote(File.pathSeparator));

    private TestRunnerMain() {}

    @ExcludeFromGeneratedCodeCoverage(reason = "The entry point of the process: it ends the JVM, which a test cannot")
    public static void main(String[] arguments) throws IOException {
        System.exit(execute(arguments));
    }

    /**
     * Runs what the arguments say and writes the report.
     *
     * @return the exit code of the process
     */
    static int execute(String[] arguments) throws IOException {
        Path file = Path.of(arguments[0]);
        List<Path> folders = Stream.of(PATH_SEPARATOR.split(arguments[1]))
                .filter(folder -> !folder.isEmpty())
                .map(Path::of)
                .toList();
        List<String> classes = List.of(arguments).subList(2, arguments.length);
        Files.write(file, run(classes, folders).lines(), StandardCharsets.UTF_8);

        return 0;
    }

    /** Runs the classes and tells what happened, as the report says. */
    static RunnerReport run(List<String> classes, List<Path> projectFolders) {
        if (!JUnitPlatform.hasLauncher()) {
            return RunnerReport.withoutLauncher();
        }
        var discovered = JUnitPlatform.discover(classes);
        if (discovered.isEmpty()) {
            return RunnerReport.withUnrunnable(classes);
        }
        var plan = discovered.get();
        List<String> unrunnable =
                classes.stream().filter(name -> !plan.recognises(name)).toList();
        if (!unrunnable.isEmpty()) {
            return RunnerReport.withUnrunnable(unrunnable);
        }
        var tally = new Tally(plan, projectFolders);
        plan.run(tally::finished, (identifier, reason) -> tally.skipped += plan.testsIn(identifier));

        return RunnerReport.of(tally.passed, tally.skipped, tally.failures);
    }

    /** What the tests that finished add up to. */
    private static final class Tally {

        private final JUnitPlatform.Discovered plan;
        private final List<Path> projectFolders;
        private final List<RunnerReport.Failure> failures = new ArrayList<>();
        private int passed;
        private int skipped;

        private Tally(JUnitPlatform.Discovered plan, List<Path> projectFolders) {
            this.plan = plan;
            this.projectFolders = projectFolders;
        }

        private void finished(JUnitPlatform.Finished finished) {
            if (finished.failed()) {
                failures.add(failure(finished));
            } else if (finished.isTest() && finished.aborted()) {
                skipped++;
            } else if (finished.isTest()) {
                passed++;
            }
        }

        private RunnerReport.Failure failure(JUnitPlatform.Finished finished) {
            Throwable thrown = finished.thrown();
            Optional<Object> located = locatedFrom(finished.identifier());

            return new RunnerReport.Failure(
                    located.flatMap(JUnitPlatform::sourceClassOf)
                            .orElse(JUnitPlatform.displayNameOf(finished.identifier())),
                    located.flatMap(JUnitPlatform::sourceMethodOf).orElse(""),
                    thrown.getClass().getName(),
                    Optional.ofNullable(thrown.getMessage()).orElse(""),
                    where(thrown).orElse(""));
        }

        /** The identifier, or the nearest one above it that knows its class: a dynamic test has no source of its own. */
        private Optional<Object> locatedFrom(Object identifier) {
            if (JUnitPlatform.sourceClassOf(identifier).isPresent()) {
                return Optional.of(identifier);
            }

            return plan.parentOf(identifier).flatMap(this::locatedFrom);
        }

        /** The first frame of the stack that is in the project; else the first frame; none when the stack is empty. */
        private Optional<String> where(Throwable thrown) {
            StackTraceElement[] stack = thrown.getStackTrace();
            return Stream.of(stack)
                    .filter(this::inTheProject)
                    .findFirst()
                    .or(() -> Stream.of(stack).findFirst())
                    .map(TestRunnerMain::describe);
        }

        private boolean inTheProject(StackTraceElement frame) {
            Path classFile = Path.of(frame.getClassName().replace('.', '/') + ".class");

            return projectFolders.stream().anyMatch(folder -> Files.isRegularFile(folder.resolve(classFile)));
        }
    }

    /** {@code app.Ledger.withdraw(Ledger.java:17)}: the frame without the loader and the module the JDK puts in front. */
    private static String describe(StackTraceElement frame) {
        String file = frame.getFileName() == null ? "Unknown Source" : frame.getFileName();
        String line = frame.getLineNumber() >= 0 ? ":" + frame.getLineNumber() : "";

        return frame.getClassName() + "." + frame.getMethodName() + "(" + file + line + ")";
    }
}
