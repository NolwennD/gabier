package fr.craft.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JdtlsSettingsTest {

    private static final Path ROOT = Path.of("/work/project");

    @Test
    void readsTheProcessTreeEveryQuarterOfASecondAndLetsJdtlsTenSecondsToShutDownByDefault() {
        var settings = JdtlsSettings.of(ROOT, List.of(), Duration.ofMinutes(10));

        assertThat(settings.projectRoot()).isEqualTo(ROOT);
        assertThat(settings.timeout()).isEqualTo(Duration.ofMinutes(10));
        assertThat(settings.treeInterval()).isEqualTo(Duration.ofMillis(250));
        assertThat(settings.shutdownTimeout()).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    void expectedProjectsAreTheAbsoluteNormalizedFoldersToCompareWithWhatJdtlsListed() {
        var settings =
                JdtlsSettings.of(ROOT, List.of(Path.of("/work/project/modules/../modules/a")), Duration.ofSeconds(1));

        assertThat(settings.expectedProjects()).containsExactly(Path.of("/work/project/modules/a"));
    }

    @Test
    void theListOfExpectedProjectsIsACopy() {
        var expected = new ArrayList<>(List.of(Path.of("/work/project/modules/a")));
        var settings = JdtlsSettings.of(ROOT, expected, Duration.ofSeconds(1));

        expected.add(Path.of("/work/project/modules/b"));

        assertThat(settings.expectedProjects()).containsExactly(Path.of("/work/project/modules/a"));
    }

    @Test
    void holdsThePreferencesJdtlsIsStartedWith() {
        // The Checkstyle of the project bans star imports; its formatter keeps the static imports first, then one
        // block of all the others, in ASCII order. "#" is the group of the static imports, "" the one of everything
        // else. jdtls writes /** Name */ above the type of a file it creates with its default type comment, and an
        // empty
        // setting means "the default": a type comment of white space only is judged empty when the template is
        // evaluated, so the type has none. (java.templates.fileHeader is empty by default: no header.)
        assertThat(JdtlsSettings.javaPreferences())
                .isEqualTo(Map.of(
                        "java",
                        Map.of(
                                "sources",
                                Map.of(
                                        "organizeImports",
                                        Map.of(
                                                "starThreshold",
                                                Integer.MAX_VALUE,
                                                "staticStarThreshold",
                                                Integer.MAX_VALUE)),
                                "completion",
                                Map.of("importOrder", List.of("#", "")),
                                "templates",
                                Map.of("typeComment", List.of(" ")))));
    }
}
