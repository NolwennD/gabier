package eu.gabier.testing.fixtures;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.gabier.testing.domain.ClassFolder;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class FakeProjectBuildTest {

    private static final Path ROOT = Path.of("/project");

    private final FakeProjectBuild build = new FakeProjectBuild(ROOT);

    @Test
    void reportsTheBuildOfTheScriptedScenarioUnderTheRoot() {
        build.scripted(BuildScenarios.BUILD_OF_A_PROJECT_THAT_COMPILES);

        assertThat(build.built().folders())
                .containsExactly(
                        new ClassFolder(ROOT.resolve("src/main/java"), ROOT.resolve("bin")),
                        new ClassFolder(ROOT.resolve("src/test/java"), ROOT.resolve("test-bin")));
    }

    @Test
    void reportsTheLastScenarioScriptedAndCountsTheAsks() {
        build.scripted(BuildScenarios.BUILD_OF_A_PROJECT_THAT_COMPILES)
                .scripted(BuildScenarios.BUILD_OF_A_PROJECT_WITH_ERRORS);

        assertThat(build.built().filesWithErrors()).hasSize(2);
        assertThat(build.asked()).isEqualTo(1);
    }

    @Test
    void reportsTheFilesEditedSinceTheChangesWereAnsweredForUnderTheRootInTheOrderOfThePaths() {
        build.scripted(BuildScenarios.BUILD_OF_A_PROJECT_THAT_COMPILES);
        build.edited(Path.of("src/test/java/app/GreeterTest.java"));
        build.edited(Path.of("src/main/java/app/Greeter.java"), Path.of("src/main/java/app/Greeter.java"));

        var changed = build.builtWithChanges();

        assertThat(changed.sources())
                .containsExactly(
                        ROOT.resolve("src/main/java/app/Greeter.java"),
                        ROOT.resolve("src/test/java/app/GreeterTest.java"));
        assertThat(changed.built()).isEqualTo(build.built());
        assertThat(build.asked()).isEqualTo(2);
    }

    @Test
    void forgetsTheChangesOnlyOnceTheyAreAnsweredAndOnlyThoseOfTheCall() {
        build.scripted(BuildScenarios.BUILD_OF_A_PROJECT_THAT_COMPILES);
        build.edited(Path.of("src/main/java/app/Greeter.java"), Path.of("src/test/java/app/GreeterTest.java"));
        var first = build.builtWithChanges();

        assertThat(build.builtWithChanges().sources()).hasSize(2);

        build.edited(Path.of("src/main/java/app/Greeter.java"));
        first.answered();

        assertThat(build.builtWithChanges().sources()).containsExactly(ROOT.resolve("src/main/java/app/Greeter.java"));
    }

    @Test
    void doesNotReportAFileDeletedAfterItWasEdited() {
        build.scripted(BuildScenarios.BUILD_OF_A_PROJECT_THAT_COMPILES);
        build.edited(Path.of("src/main/java/app/Greeter.java"), Path.of("src/test/java/app/GreeterTest.java"));

        build.deleted(Path.of("src/main/java/app/Greeter.java"));

        assertThat(build.builtWithChanges().sources())
                .containsExactly(ROOT.resolve("src/test/java/app/GreeterTest.java"));
    }

    @Test
    void failsWhenNoScenarioWasScripted() {
        assertThatThrownBy(build::built).isInstanceOf(AssertionError.class).hasMessageContaining("not scripted");
    }
}
