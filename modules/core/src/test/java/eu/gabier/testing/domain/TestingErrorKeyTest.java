package eu.gabier.testing.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class TestingErrorKeyTest {

    @Test
    void givesTheKeyThatReportsAreMappedFrom() {
        assertThat(Arrays.stream(TestingErrorKey.values()).map(TestingErrorKey::get))
                .containsExactly(
                        "testing-compilation-failed",
                        "testing-file-missing",
                        "testing-invalid-test-filter",
                        "testing-no-such-test-class",
                        "testing-no-test-engine",
                        "testing-no-test-launcher",
                        "testing-not-in-a-source-folder",
                        "testing-read-failed",
                        "testing-run-failed",
                        "testing-test-timeout");
    }
}
