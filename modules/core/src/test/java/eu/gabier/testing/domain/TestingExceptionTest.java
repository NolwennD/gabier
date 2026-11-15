package eu.gabier.testing.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class TestingExceptionTest {

    @Test
    void namesTheClassesNoEngineRecognises() {
        var refusal = TestingException.noTestEngine(List.of("app.A", "app.B"));

        assertThat(refusal.key()).isEqualTo(TestingErrorKey.NO_TEST_ENGINE);
        assertThat(refusal.parameters()).containsEntry("classes", "app.A, app.B");
    }

    @Test
    void saysWhichLibraryIsMissingFromTheClassPath() {
        var refusal = TestingException.noTestLauncher();

        assertThat(refusal.key()).isEqualTo(TestingErrorKey.NO_TEST_LAUNCHER);
        assertThat(refusal.getMessage()).contains("org.junit.platform:junit-platform-launcher");
    }

    @Test
    void saysHowLongTheTestsWereGiven() {
        var refusal = TestingException.timedOut(Duration.ofSeconds(90));

        assertThat(refusal.key()).isEqualTo(TestingErrorKey.TEST_TIMEOUT);
        assertThat(refusal.parameters()).containsEntry("seconds", "90");
    }

    @Test
    void givesTheEndOfTheOutputOfAProcessThatEndedWithNoReport() {
        var refusal = TestingException.endedWithoutReport("OutOfMemoryError");

        assertThat(refusal.key()).isEqualTo(TestingErrorKey.RUN_FAILED);
        assertThat(refusal.getMessage()).contains("OutOfMemoryError");
    }

    @Test
    void keepsTheCauseOfAProcessThatCouldNotStart() {
        var cause = new IOException("no java");
        var refusal = TestingException.notStarted(cause);

        assertThat(refusal.key()).isEqualTo(TestingErrorKey.RUN_FAILED);
        assertThat(refusal.getCause()).isSameAs(cause);
    }
}
