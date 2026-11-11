package eu.gabier.testing.fixtures;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.gabier.testing.domain.ClassFolder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BuildScenariosTest {

    private static final Pattern KEBAB_CASE = Pattern.compile("[a-z]+(-[a-z]+)*");

    @TempDir
    Path root;

    @Test
    void namesEveryScenarioInKebabCaseOnceAndHoldsTwoOfThem() {
        var names = BuildScenarios.all().stream().map(BuildScenario::name).toList();

        assertThat(names)
                .hasSize(2)
                .doesNotHaveDuplicates()
                .allMatch(name -> KEBAB_CASE.matcher(name).matches());
    }

    @Test
    void holdsAProjectThatCompilesAndNamesNoFileWithAnError() throws IOException {
        var scenario = BuildScenarios.BUILD_OF_A_PROJECT_THAT_COMPILES;

        CompiledSources.compile(sources(scenario, "src/main/java"), root.resolve("main"), List.of());
        CompiledSources.compile(
                sources(scenario, "src/test/java"), root.resolve("test"), List.of(root.resolve("main")));
        assertThat(scenario.built().filesWithErrors()).isEmpty();
    }

    @Test
    void holdsAProjectWhoseErrorsAreInTheTwoFilesItNames() throws IOException {
        var scenario = BuildScenarios.BUILD_OF_A_PROJECT_WITH_ERRORS;

        assertThatThrownBy(() -> CompiledSources.compile(sources(scenario, "src/main/java"), root, List.of()))
                .hasMessageContaining("Broken.java")
                .hasMessageNotContaining("Clean.java");
        assertThatThrownBy(() -> CompiledSources.compile(sources(scenario, "src/test/java"), root, List.of()))
                .hasMessageContaining("BrokenTest.java");
        assertThat(scenario.built().filesWithErrors())
                .containsExactly(
                        Path.of("src/main/java/app/Broken.java"), Path.of("src/test/java/app/BrokenTest.java"));
    }

    @Test
    void putsEveryPathUnderTheRootWhenPlayedInsideIt() {
        var inside = BuildScenarios.BUILD_OF_A_PROJECT_WITH_ERRORS.inside(Path.of("/project"));

        assertThat(inside.built().folders())
                .containsExactly(
                        new ClassFolder(Path.of("/project/src/main/java"), Path.of("/project/bin")),
                        new ClassFolder(Path.of("/project/src/test/java"), Path.of("/project/test-bin")));
        assertThat(inside.built().filesWithErrors())
                .containsExactly(
                        Path.of("/project/src/main/java/app/Broken.java"),
                        Path.of("/project/src/test/java/app/BrokenTest.java"));
        assertThat(inside.files()).containsKey(Path.of("/project/src/main/java/app/Clean.java"));
    }

    @Test
    void putsTheLibrariesUnderTheRootToo() {
        var scenario = new BuildScenario(
                "with-a-library",
                Map.of(),
                new eu.gabier.testing.domain.BuiltProject(List.of(), List.of(Path.of("lib/a.jar")), List.of()));

        assertThat(scenario.inside(Path.of("/project")).built().libraries())
                .containsExactly(Path.of("/project/lib/a.jar"));
    }

    @Test
    void writesTheFilesOfTheProjectWithTheFoldersTheyNeed() throws IOException {
        BuildScenarios.BUILD_OF_A_PROJECT_THAT_COMPILES.writeProject(root);

        assertThat(Files.readString(root.resolve("src/main/java/app/Greeter.java")))
                .contains("public class Greeter");
        assertThat(root.resolve("src/test/java/app/GreeterTest.java")).isRegularFile();
    }

    private static Map<String, String> sources(BuildScenario scenario, String folder) {
        Map<String, String> sources = new HashMap<>();
        scenario.files().forEach((path, text) -> {
            if (path.startsWith(folder)) {
                sources.put(Path.of(folder).relativize(path).toString(), text);
            }
        });

        return sources;
    }
}
