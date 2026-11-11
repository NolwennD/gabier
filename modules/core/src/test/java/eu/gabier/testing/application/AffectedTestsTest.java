package eu.gabier.testing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import eu.gabier.shared.error.domain.AssertionException;
import eu.gabier.shared.error.domain.MissingMandatoryValueException;
import eu.gabier.testing.domain.BuiltProject;
import eu.gabier.testing.domain.ProjectBuild;
import eu.gabier.testing.domain.TestingErrorKey;
import eu.gabier.testing.domain.TestingException;
import eu.gabier.testing.fixtures.CompiledProject;
import eu.gabier.testing.infrastructure.secondary.NioProjectFiles;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AffectedTestsTest {

    @TempDir
    Path root;

    private final AtomicInteger builds = new AtomicInteger();

    private AffectedTests affectedTests(BuiltProject built) {
        ProjectBuild build = () -> {
            builds.incrementAndGet();
            return built;
        };

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
        assertThat(builds).hasValue(1);
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
                .isThrownBy(() -> new AffectedTests(() -> null, null, root))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("files"));
    }
}
