package eu.gabier.testing.application;

import static org.assertj.core.api.Assertions.assertThat;

import eu.gabier.testing.domain.BuiltProject;
import eu.gabier.testing.fixtures.AffectedTestsCase;
import eu.gabier.testing.fixtures.AffectedTestsCases;
import eu.gabier.testing.fixtures.BuildScenario;
import eu.gabier.testing.fixtures.CompiledProject;
import eu.gabier.testing.fixtures.FakeProjectBuild;
import eu.gabier.testing.infrastructure.secondary.NioProjectFiles;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

/** The contract scenarios of the selection of tests, played on classes that javac compiled in place of the server. */
class AffectedTestsCasesTest {

    private static final Pattern KEBAB_CASE = Pattern.compile("[a-z]+(-[a-z]+)*");
    private static final Path MAIN = Path.of("src/main/java");
    private static final Path TESTS = Path.of("src/test/java");

    @TempDir
    Path root;

    @Test
    void namesEveryCaseInKebabCaseOnce() {
        var names =
                AffectedTestsCases.all().stream().map(AffectedTestsCase::name).toList();

        assertThat(names)
                .doesNotHaveDuplicates()
                .allMatch(name -> KEBAB_CASE.matcher(name).matches());
    }

    @Test
    void asksOnlyAboutFilesOfTheProjectAndEditsOnlyFilesOfTheProject() {
        var files = projectFiles();

        for (AffectedTestsCase scenario : AffectedTestsCases.all()) {
            assertThat(files)
                    .containsAll(scenario.changed())
                    .containsAll(scenario.edits().keySet());
        }
    }

    @Test
    void listsTheTestsOfEveryCaseInTheOrderOfTheirNames() {
        for (AffectedTestsCase scenario : AffectedTestsCases.all()) {
            assertThat(scenario.affected()).isSorted();
        }
    }

    @TestFactory
    Stream<DynamicTest> selectsTheTestsOfEveryCase() {
        return AffectedTestsCases.all().stream()
                .map(scenario -> DynamicTest.dynamicTest(scenario.name(), () -> play(scenario)));
    }

    private void play(AffectedTestsCase scenario) throws IOException {
        Path project = Files.createDirectories(root.resolve(scenario.name()));
        for (Map.Entry<Path, String> other : AffectedTestsCases.otherFiles().entrySet()) {
            Files.writeString(project.resolve(other.getKey()), other.getValue());
        }
        Map<String, String> main = new HashMap<>(AffectedTestsCases.mainSources());
        Map<String, String> tests = new HashMap<>(AffectedTestsCases.testSources());
        scenario.edits().forEach((file, text) -> {
            if (file.startsWith(TESTS)) {
                tests.put(TESTS.relativize(file).toString(), text);
            } else {
                main.put(MAIN.relativize(file).toString(), text);
            }
        });
        BuiltProject built = CompiledProject.of(project, main, tests);
        FakeProjectBuild build =
                new FakeProjectBuild(project).scripted(new BuildScenario(scenario.name(), Map.of(), built));
        AffectedTests affectedTests = new AffectedTests(build, new NioProjectFiles(), project);

        List<String> affected = affectedTests.affectedBy(
                scenario.changed().stream().map(project::resolve).toList());
        build.edited(scenario.changed().toArray(Path[]::new));
        List<String> affectedWithNoFileGiven = affectedTests.affectedByChanges();

        assertThat(affected).containsExactlyElementsOf(scenario.affected());
        assertThat(affectedWithNoFileGiven).containsExactlyElementsOf(scenario.affected());
    }

    private static List<Path> projectFiles() {
        Stream<Path> main = AffectedTestsCases.mainSources().keySet().stream().map(MAIN::resolve);
        Stream<Path> tests = AffectedTestsCases.testSources().keySet().stream().map(TESTS::resolve);

        return Stream.of(main, tests, AffectedTestsCases.otherFiles().keySet().stream())
                .flatMap(files -> files)
                .toList();
    }
}
