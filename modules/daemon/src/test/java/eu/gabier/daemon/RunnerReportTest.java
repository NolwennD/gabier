package eu.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import org.junit.jupiter.api.Test;

class RunnerReportTest {

    @Test
    void writesAndReadsTheCountsAndTheFailuresWhateverTheyHold() {
        var hostile = new RunnerReport.Failure(
                "app.GreeterTest$Inner",
                "greets",
                "java.lang.AssertionError",
                "line one\nline two\ttabbed \\ back\r\nslash",
                "app.Greeter.greet(Greeter.java:7)");
        var bare = new RunnerReport.Failure("app.OtherTest", "", "java.lang.Error", "", "");
        var report = RunnerReport.of(4, 2, List.of(hostile, bare));

        var read = RunnerReport.parse(report.lines());

        assertThat(report.lines()).allSatisfy(line -> assertThat(line).doesNotContain("\n", "\r"));
        assertThat(read.passed()).isEqualTo(4);
        assertThat(read.skipped()).isEqualTo(2);
        assertThat(read.failures()).containsExactly(hostile, bare);
        assertThat(read.launcherMissing()).isFalse();
        assertThat(read.unrunnable()).isEmpty();
    }

    @Test
    void writesAndReadsAMissingLauncher() {
        var read = RunnerReport.parse(RunnerReport.withoutLauncher().lines());

        assertThat(read.launcherMissing()).isTrue();
        assertThat(read.passed()).isZero();
    }

    @Test
    void writesAndReadsTheClassesNoEngineRecognises() {
        var read = RunnerReport.parse(
                RunnerReport.withUnrunnable(List.of("app.A", "app.B")).lines());

        assertThat(read.unrunnable()).containsExactly("app.A", "app.B");
    }

    @Test
    void readsABackslashThatEndsAFieldAsItIs() {
        var read = RunnerReport.parse(List.of("unrunnable\tapp.A\\"));

        assertThat(read.unrunnable()).containsExactly("app.A\\");
    }

    @Test
    void refusesALineTheWriterDoesNotWrite() {
        assertThatIllegalArgumentException().isThrownBy(() -> RunnerReport.parse(List.of("surprise\t1")));
    }
}
