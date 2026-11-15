package eu.gabier.testing.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import eu.gabier.shared.error.domain.AssertionException;
import eu.gabier.shared.error.domain.MissingMandatoryValueException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TestRunRecordsTest {

    private static final TestFailure FAILURE = new TestFailure(
            "app.GreeterTest", Optional.of("greets"), "java.lang.AssertionError", "no", Optional.empty());

    @Test
    void countsWhatRanAndWhatFailed() {
        var report = new TestReport(3, 1, List.of(FAILURE, FAILURE));

        assertThat(report.failed()).isEqualTo(2);
        assertThat(report.ran()).isEqualTo(5);
        assertThat(report.skipped()).isEqualTo(1);
    }

    @Test
    void ranNothing() {
        assertThat(TestReport.nothingRan().ran()).isZero();
        assertThat(TestReport.nothingRan().failures()).isEmpty();
    }

    @Test
    void refusesNegativeCounts() {
        assertThatExceptionOfType(AssertionException.class).isThrownBy(() -> new TestReport(-1, 0, List.of()));
        assertThatExceptionOfType(AssertionException.class).isThrownBy(() -> new TestReport(0, -1, List.of()));
    }

    @Test
    void refusesAFailureWithoutItsClassOrItsException() {
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> new TestFailure(" ", Optional.empty(), "E", "", Optional.empty()));
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> new TestFailure("a.B", Optional.empty(), "", "", Optional.empty()));
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> new TestFailure("a.B", Optional.empty(), "E", null, Optional.empty()));
    }

    @Test
    void asksForTestClassesAndATimeLimit() {
        var run = new TestRun(
                List.of("app.GreeterTest"), List.of(Path.of("/bin")), Path.of("/project"), Duration.ofSeconds(1));

        assertThat(run.classes()).containsExactly("app.GreeterTest");
        assertThatExceptionOfType(AssertionException.class)
                .isThrownBy(() -> new TestRun(List.of(), List.of(), Path.of("/project"), Duration.ofSeconds(1)));
        assertThatExceptionOfType(AssertionException.class)
                .isThrownBy(() -> new TestRun(List.of("a.B"), List.of(), Path.of("/project"), Duration.ZERO));
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> new TestRun(List.of("a.B"), List.of(), null, Duration.ofSeconds(1)));
    }
}
