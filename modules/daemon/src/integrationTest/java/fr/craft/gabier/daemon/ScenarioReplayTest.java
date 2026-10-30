package fr.craft.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;

import fr.craft.gabier.editing.domain.ChangeApplier;
import fr.craft.gabier.editing.domain.ChangeSet;
import fr.craft.gabier.editing.domain.FileChange;
import fr.craft.gabier.editing.domain.LanguageServerErrorKey;
import fr.craft.gabier.editing.domain.LanguageServerException;
import fr.craft.gabier.editing.fixtures.Scenario;
import fr.craft.gabier.editing.fixtures.Scenario.Answer;
import fr.craft.gabier.editing.fixtures.Scenario.Request;
import fr.craft.gabier.editing.fixtures.Scenarios;
import fr.craft.gabier.editing.infrastructure.secondary.NioFileStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

/**
 * Replays the contract scenarios of the core ({@link Scenarios}) against a real jdtls through the adapter, and fails
 * on any difference between what jdtls answers and what the scenario says: this is what makes the answers the fake of
 * the port is scripted with a promise about the real server and not a guess.
 *
 * <p>The scenarios are grouped by their project: one jdtls is started on each. Its project is written in a fresh
 * folder, with the two files of an Eclipse project ({@code .project}, {@code .classpath}) that jdtls imports without
 * network, which a build file of Maven or Gradle would not guarantee. A scenario whose answer is "not ready" is replayed
 * before jdtls has imported the project, which is how the adapter detects it: the import takes seconds, so asking at
 * once is deterministic. The other scenarios are replayed once it is ready. Not run by {@code check}: {@code
 * ./gradlew jdtlsProbe}, with {@code GABIER_JDTLS} set to the jdtls launcher.
 *
 * <p>Only the requests the adapter implements are replayed; {@link #replayed} says which, and the test prints the
 * scenarios it leaves out. The answer of a rename is compared to the scenario exactly, and must compile once applied
 * ({@link #compile}); whatever is asked, the sources of the project stay as they were ({@link #assertUntouched}).
 */
class ScenarioReplayTest {

    private static final Duration TIMEOUT = Duration.ofMinutes(5);

    private static final String ECLIPSE_PROJECT = """
        <?xml version="1.0" encoding="UTF-8"?>
        <projectDescription>
          <name>scenario</name>
          <comment></comment>
          <projects></projects>
          <buildSpec>
            <buildCommand><name>org.eclipse.jdt.core.javabuilder</name><arguments></arguments></buildCommand>
          </buildSpec>
          <natures><nature>org.eclipse.jdt.core.javanature</nature></natures>
        </projectDescription>
        """;

    private static final String ECLIPSE_CLASSPATH = """
        <?xml version="1.0" encoding="UTF-8"?>
        <classpath>
          <classpathentry kind="src" path="src/main/java"/>
          <classpathentry kind="con" path="org.eclipse.jdt.launching.JRE_CONTAINER"/>
          <classpathentry kind="output" path="bin"/>
        </classpath>
        """;

    @Test
    void replaysTheScenariosTheAdapterImplements() throws Exception {
        Map<Map<Path, String>, List<Scenario>> projects = new LinkedHashMap<>();
        List<String> left = new ArrayList<>();
        for (Scenario scenario : Scenarios.all()) {
            if (replayed(scenario)) {
                projects.computeIfAbsent(scenario.files(), files -> new ArrayList<>())
                        .add(scenario);
            } else {
                left.add(scenario.name());
            }
        }
        assertThat(projects).as("the scenarios to replay").isNotEmpty();
        System.out.println("scenarios not replayed (the adapter does not implement their request yet): " + left);

        var differences = new SoftAssertions();
        for (List<Scenario> project : projects.values()) {
            replay(project, differences);
        }
        differences.assertAll();
    }

    /** The requests the adapter implements. */
    private static boolean replayed(Scenario scenario) {
        return scenario.request() instanceof Request.Diagnostics || scenario.request() instanceof Request.Rename;
    }

    /** Starts jdtls on the project the scenarios share, and replays them: first those that need it still importing. */
    private static void replay(List<Scenario> scenarios, SoftAssertions differences) throws Exception {
        Path root = Files.createTempDirectory("gabier-scenarios-").toRealPath();
        try {
            scenarios.getFirst().writeProject(root);
            Files.writeString(root.resolve(".project"), ECLIPSE_PROJECT);
            Files.writeString(root.resolve(".classpath"), ECLIPSE_CLASSPATH);
            var settings = JdtlsSettings.of(root, List.of(root), TIMEOUT);
            try (var server = JdtlsLanguageServer.start(
                    RealJdtls.launcher(), settings, JdtlsLanguageServer.QUIET, System.out::println)) {
                for (Scenario scenario : scenarios) {
                    if (needsJdtlsToImport(scenario)) {
                        replay(scenario.inside(root), root, server, differences);
                    }
                }
                server.awaitStarted();
                for (Scenario scenario : scenarios) {
                    if (!needsJdtlsToImport(scenario)) {
                        replay(scenario.inside(root), root, server, differences);
                    }
                }
            }
        } finally {
            delete(root);
        }
    }

    private static boolean needsJdtlsToImport(Scenario scenario) {
        return scenario.answer() instanceof Answer.Refused refused && refused.key() == LanguageServerErrorKey.NOT_READY;
    }

    private static void replay(Scenario scenario, Path root, JdtlsLanguageServer server, SoftAssertions differences)
            throws IOException {
        replayAnswer(scenario, root, server, differences);
        assertUntouched(scenario, root, differences);
    }

    private static void replayAnswer(
            Scenario scenario, Path root, JdtlsLanguageServer server, SoftAssertions differences) throws IOException {
        Request request = scenario.request();
        switch (scenario.answer()) {
            case Answer.Diagnosed diagnosed -> {
                var answer = (List<?>) ask(server, request);
                System.out.println(scenario.name() + ": jdtls answered " + answer);
                differences
                        .assertThat(answer)
                        .as("%s: the diagnostics jdtls answers", scenario.name())
                        .isEqualTo(diagnosed.diagnostics());
            }
            case Answer.Renamed renamed -> {
                var answer = (ChangeSet) ask(server, request);
                System.out.println(scenario.name() + ": jdtls answered " + answer);
                differences
                        .assertThat(answer)
                        .as("%s: the change set jdtls answers", scenario.name())
                        .isEqualTo(renamed.changes());
                compile(scenario, root, answer, differences);
            }
            case Answer.Refused refused -> {
                LanguageServerException expected = request.refusal(refused.key());
                try {
                    var answer = ask(server, request);
                    differences.fail(
                            "%s: jdtls answered %s, the scenario expects %s", scenario.name(), answer, expected);
                } catch (LanguageServerException refusal) {
                    System.out.println(scenario.name() + ": jdtls refused with " + refusal.key());
                    differences
                            .assertThat(refusal.key())
                            .as("%s: the key of the refusal", scenario.name())
                            .isEqualTo(expected.key());
                    differences
                            .assertThat(refusal.parameters())
                            .as("%s: the parameters of the refusal", scenario.name())
                            .isEqualTo(expected.parameters());
                    differences
                            .assertThat(refusal.getMessage())
                            .as("%s: the message of the refusal", scenario.name())
                            .isEqualTo(expected.getMessage());
                }
            }
        }
    }

    /** The diagnostics, or the change set, that jdtls answers to the request. */
    private static Object ask(JdtlsLanguageServer server, Request request) {
        return switch (request) {
            case Request.Diagnostics diagnostics -> server.diagnostics(diagnostics.file());
            case Request.Rename rename -> server.rename(rename.file(), rename.position(), rename.newName());
        };
    }

    /** Asking changes nothing: the files of the project are the ones the scenario wrote, and no other source is there. */
    private static void assertUntouched(Scenario scenario, Path root, SoftAssertions differences) throws IOException {
        Map<Path, String> found = new TreeMap<>();
        try (var files = Files.walk(root.resolve("src"))) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                found.put(file, Files.readString(file));
            }
        }
        differences
                .assertThat(found)
                .as("%s: the sources of %s after the request", scenario.name(), root)
                .isEqualTo(scenario.files());
    }

    /** The project with the change set applied (to a copy of it) is Java that compiles: every reference was changed. */
    private static void compile(Scenario scenario, Path root, ChangeSet answer, SoftAssertions differences)
            throws IOException {
        Path copy = Files.createTempDirectory("gabier-renamed-").toRealPath();
        try {
            copyProject(root, copy);
            new ChangeApplier(new NioFileStore()).apply(rebased(answer, root, copy));
            differences
                    .assertThat(compilationErrors(copy))
                    .as("%s: the errors of the project once the change set of jdtls is applied", scenario.name())
                    .isEmpty();
        } finally {
            delete(copy);
        }
    }

    private static void copyProject(Path from, Path to) throws IOException {
        try (var files = Files.walk(from.resolve("src"))) {
            for (Path file : files.toList()) {
                Path target = to.resolve(from.relativize(file));
                if (Files.isDirectory(file)) {
                    Files.createDirectories(target);
                } else {
                    Files.copy(file, target);
                }
            }
        }
    }

    /** The same changes on the copy of the project. */
    private static ChangeSet rebased(ChangeSet changes, Path root, Path copy) {
        return new ChangeSet(changes.changes().stream()
                .<FileChange>map(change -> switch (change) {
                    case FileChange.EditFile edit ->
                        new FileChange.EditFile(rebased(edit.file(), root, copy), edit.edits());
                    case FileChange.CreateFile create -> new FileChange.CreateFile(rebased(create.file(), root, copy));
                    case FileChange.RenameFile rename ->
                        new FileChange.RenameFile(rebased(rename.from(), root, copy), rebased(rename.to(), root, copy));
                    case FileChange.DeleteFile delete -> new FileChange.DeleteFile(rebased(delete.file(), root, copy));
                })
                .toList());
    }

    private static Path rebased(Path file, Path root, Path copy) {
        return copy.resolve(root.relativize(file));
    }

    /** What javac reports on every source of the project, none when it compiles. */
    private static List<String> compilationErrors(Path project) throws IOException {
        var javac = ToolProvider.getSystemJavaCompiler();
        var errors = new DiagnosticCollector<JavaFileObject>();
        try (var files = Files.walk(project.resolve("src"));
                var manager = javac.getStandardFileManager(errors, null, null)) {
            var sources = manager.getJavaFileObjectsFromPaths(
                    files.filter(file -> file.toString().endsWith(".java")).toList());
            Path classes = Files.createTempDirectory(project, "classes-");
            javac.getTask(null, manager, errors, List.of("-d", classes.toString()), null, sources)
                    .call();
        }
        return errors.getDiagnostics().stream()
                .filter(diagnostic -> diagnostic.getKind() == javax.tools.Diagnostic.Kind.ERROR)
                .map(Object::toString)
                .toList();
    }

    private static void delete(Path folder) throws IOException {
        try (var files = Files.walk(folder)) {
            for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(file);
            }
        }
    }
}
