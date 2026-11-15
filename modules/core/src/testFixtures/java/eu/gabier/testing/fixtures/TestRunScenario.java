package eu.gabier.testing.fixtures;

import eu.gabier.shared.error.domain.Assert;
import eu.gabier.testing.domain.TestReport;
import eu.gabier.testing.domain.TestingErrorKey;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * What a real runner is expected to give for the tests of a small project: the effect of running the classes, which is
 * the report (the counts, the failed tests with their class, method, exception, message and place), or the refusal.
 * It is the contract between the fake of the runner ({@link FakeTestRunner}) and the real one: the fake is scripted
 * with it in {@code check}, and an integration test builds the project with the language server, runs the classes in
 * the real runner and fails if the effect differs.
 *
 * @param name what the scenario shows, in kebab-case
 * @param files the files of the project, as plain text: paths relative to its root
 * @param classes the test classes to run
 * @param timeLimit how long the run may take
 * @param expected the report the run is expected to give; none when it is refused
 * @param refusal the key of the refusal the run is expected to end with; none when it gives a report
 */
public record TestRunScenario(
        String name,
        Map<Path, String> files,
        List<String> classes,
        Duration timeLimit,
        Optional<TestReport> expected,
        Optional<TestingErrorKey> refusal) {

    public TestRunScenario {
        Assert.notBlank("name", name);
        Assert.notNull("files", files);
        Assert.field("classes", classes).notNull().notEmpty().noNullElement();
        Assert.notNull("timeLimit", timeLimit);
        Assert.notNull("expected", expected);
        Assert.notNull("refusal", refusal);

        files = Map.copyOf(files);
        classes = List.copyOf(classes);
    }

    /** A scenario whose run gives a report. */
    public static TestRunScenario reporting(
            String name, Map<Path, String> files, List<String> classes, TestReport report) {
        return new TestRunScenario(name, files, classes, Duration.ofSeconds(60), Optional.of(report), Optional.empty());
    }

    /** A scenario whose run is refused. */
    public static TestRunScenario refused(
            String name, Map<Path, String> files, List<String> classes, Duration timeLimit, TestingErrorKey key) {
        return new TestRunScenario(name, files, classes, timeLimit, Optional.empty(), Optional.of(key));
    }

    /** The report of the scenario; the fake and the replay of a scenario that is refused never ask for it. */
    public TestReport report() {
        return expected.orElseThrow(() -> new AssertionError("The scenario " + name + " is refused, it has no report"));
    }

    /** Writes the files of the project under the directory, creating the folders they need. */
    public void writeProject(Path directory) throws IOException {
        for (Map.Entry<Path, String> file : files.entrySet()) {
            Path path = directory.resolve(file.getKey());
            Files.createDirectories(path.getParent());
            Files.writeString(path, file.getValue());
        }
    }
}
