package eu.gabier.testing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import eu.gabier.shared.error.domain.AssertionException;
import eu.gabier.shared.error.domain.MissingMandatoryValueException;
import eu.gabier.testing.domain.BuiltProject;
import eu.gabier.testing.domain.TestingErrorKey;
import eu.gabier.testing.domain.TestingException;
import eu.gabier.testing.fixtures.BuildScenario;
import eu.gabier.testing.fixtures.CompiledProject;
import eu.gabier.testing.fixtures.FakeProjectBuild;
import eu.gabier.testing.infrastructure.secondary.NioProjectFiles;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AffectedTestsTest {

    @TempDir
    Path root;

    private FakeProjectBuild build;

    private AffectedTests affectedTests(BuiltProject built) {
        build = building(built);

        return new AffectedTests(build, new NioProjectFiles(), root);
    }

    private BuiltProject project() throws IOException {
        return CompiledProject.of(
                root,
                Map.of("app/Greeter.java", "package app; public class Greeter {}"),
                Map.of("app/GreeterTest.java", """
                    package app;

                    public class GreeterTest {
                        @org.junit.Test
                        public void greets() {
                            new Greeter();
                        }
                    }
                    """));
    }

    @Test
    void asksTheBuildOnceAndSelectsTheTestsOfTheFilesThatChanged() throws IOException {
        var affected = affectedTests(project());

        assertThat(affected.affectedBy(List.of(root.resolve("src/main/java/app/Greeter.java"))))
                .containsExactly("app.GreeterTest");
        assertThat(build.asked()).isEqualTo(1);
    }

    private FakeProjectBuild building(BuiltProject built) {
        return new FakeProjectBuild(root).scripted(new BuildScenario("compiled-by-javac", Map.of(), built));
    }

    @Test
    void selectsTheTestsOfTheFilesThatChangedSinceTheLastAnswerAndNamesNoFile() throws IOException {
        var build = building(project());
        var affected = new AffectedTests(build, new NioProjectFiles(), root);
        build.edited(Path.of("src/main/java/app/Greeter.java"));

        assertThat(affected.affectedByChanges()).containsExactly("app.GreeterTest");
        assertThat(build.asked()).isEqualTo(1);
    }

    @Test
    void answersNoTestWhenNothingChangedSinceTheLastAnswer() throws IOException {
        var build = building(project());
        var affected = new AffectedTests(build, new NioProjectFiles(), root);

        assertThat(affected.affectedByChanges()).isEmpty();

        build.edited(Path.of("src/test/java/app/GreeterTest.java"));

        assertThat(affected.affectedByChanges()).containsExactly("app.GreeterTest");
        assertThat(affected.affectedByChanges()).isEmpty();
    }

    @Test
    void listsAgainTheChangesOfACallThatWasRefusedOnceTheyCompile() throws IOException {
        BuiltProject compiling = project();
        Path greeter = root.resolve("src/main/java/app/Greeter.java");
        var build = building(new BuiltProject(compiling.folders(), compiling.libraries(), List.of(greeter)));
        var affected = new AffectedTests(build, new NioProjectFiles(), root);
        build.edited(Path.of("src/main/java/app/Greeter.java"));

        assertThatExceptionOfType(TestingException.class)
                .isThrownBy(affected::affectedByChanges)
                .satisfies(exception -> assertThat(exception.key()).isEqualTo(TestingErrorKey.COMPILATION_FAILED));
        build.scripted(new BuildScenario("fixed", Map.of(), compiling));

        assertThat(affected.affectedByChanges()).containsExactly("app.GreeterTest");
    }

    @Test
    void relaysTheRefusalOfTheSelection() throws IOException {
        var affected = affectedTests(project());

        assertThatExceptionOfType(TestingException.class)
                .isThrownBy(() -> affected.affectedBy(List.of(root.resolve("src/main/java/app/Gone.java"))))
                .satisfies(exception -> assertThat(exception.key()).isEqualTo(TestingErrorKey.FILE_MISSING));
    }

    @Test
    void refusesNoFileAtAll() throws IOException {
        var affected = affectedTests(project());

        assertThatExceptionOfType(AssertionException.class).isThrownBy(() -> affected.affectedBy(List.of()));
    }

    @Test
    void rejectsAMissingBuildAndMissingFiles() {
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> new AffectedTests(null, new NioProjectFiles(), root))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("build"));
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> new AffectedTests(new FakeProjectBuild(root), null, root))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("files"));
    }
}
