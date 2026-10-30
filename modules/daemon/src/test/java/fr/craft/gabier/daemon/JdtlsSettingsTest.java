package fr.craft.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
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
}
