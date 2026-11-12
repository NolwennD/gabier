package eu.gabier.testing.fixtures;

import static org.assertj.core.api.Assertions.assertThat;

import eu.gabier.testing.domain.BuiltProject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ChangeScenariosTest {

    private static final Pattern KEBAB_CASE = Pattern.compile("[a-z]+(-[a-z]+)*");
    private static final Path ROOT = Path.of("/project");

    @TempDir
    Path root;

    @Test
    void namesEveryScenarioInKebabCaseOnce() {
        var names = ChangeScenarios.all().stream().map(ChangeScenario::name).toList();

        assertThat(names)
                .hasSize(4)
                .doesNotHaveDuplicates()
                .allMatch(name -> KEBAB_CASE.matcher(name).matches());
    }

    @Test
    void reportsWhatEveryStepSaysWhenTheFakeIsPlayedTheSteps() {
        for (ChangeScenario scenario : ChangeScenarios.all()) {
            var build = new FakeProjectBuild(ROOT)
                    .scripted(new BuildScenario(
                            scenario.name(), Map.of(), new BuiltProject(List.of(), List.of(), List.of())));

            for (ChangeScenario.Step step : scenario.steps()) {
                step.writes().keySet().stream()
                        .filter(file -> file.toString().endsWith(".java"))
                        .forEach(build::edited);
                step.deletes().forEach(build::deleted);

                var changed = build.builtWithChanges();

                assertThat(changed.sources())
                        .as(scenario.name())
                        .containsExactlyElementsOf(
                                step.reported().stream().map(ROOT::resolve).toList());
                if (step.answered()) {
                    changed.answered();
                }
            }
        }
    }

    @Test
    void holdsProjectsThatCompileBeforeAndAfterEveryStep() throws IOException {
        for (ChangeScenario scenario : ChangeScenarios.all()) {
            Map<Path, String> files = new HashMap<>(scenario.files());
            compiles(scenario, files);
            for (ChangeScenario.Step step : scenario.steps()) {
                files.putAll(step.writes());
                step.deletes().forEach(files::remove);

                compiles(scenario, files);
            }
        }
    }

    @Test
    void writesTheFilesOfTheProjectWithTheFoldersTheyNeed() throws IOException {
        ChangeScenarios.AN_EDIT_ON_DISK.writeProject(root);

        assertThat(Files.readString(root.resolve("src/main/java/app/Greeter.java")))
                .contains("public class Greeter");
        assertThat(root.resolve("src/test/java/app/GreeterTest.java")).isRegularFile();
    }

    private void compiles(ChangeScenario scenario, Map<Path, String> files) throws IOException {
        Path folder = Files.createTempDirectory(root, scenario.name());
        Map<String, String> main = sources(files, "src/main/java");
        Map<String, String> tests = sources(files, "src/test/java");
        CompiledSources.compile(main, folder.resolve("main"), List.of());
        CompiledSources.compile(tests, folder.resolve("test"), List.of(folder.resolve("main")));
    }

    private static Map<String, String> sources(Map<Path, String> files, String folder) {
        Map<String, String> sources = new HashMap<>();
        files.forEach((path, text) -> {
            if (path.startsWith(folder)) {
                sources.put(Path.of(folder).relativize(path).toString(), text);
            }
        });

        return sources;
    }
}
