package eu.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;

import eu.gabier.protocol.JdtlsProgress;
import eu.gabier.protocol.JdtlsProgress.Phase;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class StartProgressTest {

    private final AtomicLong nanos = new AtomicLong();
    private final StartProgress progress = new StartProgress(nanos::get);

    private void after(Duration time) {
        nanos.addAndGet(time.toNanos());
    }

    private JdtlsProgress current() {
        return progress.current();
    }

    @Test
    void jdtlsIsLaunchingFromTheMomentItIsStartedAndSaysNothingYet() {
        after(Duration.ofSeconds(2));

        assertThat(current()).isEqualTo(new JdtlsProgress(Phase.LAUNCHING, "", Duration.ofSeconds(2)));
    }

    @Test
    void theStartingStatusesBeforeTheImportAreTheMessageOfTheLaunch() {
        after(Duration.ofSeconds(6));
        progress.status("Starting", "Init...");
        after(Duration.ofSeconds(1));
        progress.status("Starting", "45% Starting Java Language Server");

        assertThat(current())
                .isEqualTo(
                        new JdtlsProgress(Phase.LAUNCHING, "45% Starting Java Language Server", Duration.ofSeconds(7)));
    }

    @Test
    void aStartingStatusWithATaskAfterTheDashIsTheImportAndTheTaskIsItsMessage() {
        after(Duration.ofSeconds(6));
        progress.status("Starting", "70% Starting Java Language Server - Configure project :core");

        assertThat(current()).isEqualTo(new JdtlsProgress(Phase.IMPORTING, "Configure project :core", Duration.ZERO));
    }

    @Test
    void theTimeInTheImportCountsFromTheFirstTaskNotFromTheLatest() {
        progress.status("Starting", "70% Starting Java Language Server - Build");
        after(Duration.ofSeconds(30));
        progress.status("Starting", "70% Starting Java Language Server - Run build - fetch the model");
        after(Duration.ofSeconds(5));

        assertThat(current())
                .isEqualTo(new JdtlsProgress(Phase.IMPORTING, "Run build - fetch the model", Duration.ofSeconds(35)));
    }

    @Test
    void theNumbersJdtlsPutsInFrontOfTheMessageAreNotReadAndTheImportDoesNotGoBack() {
        progress.status("Starting", "1800% Starting Java Language Server - Build model 'java.util.Map'");
        progress.status("Starting", "100% Starting Java Language Server");

        assertThat(current().phase()).isEqualTo(Phase.IMPORTING);
        assertThat(current().message()).isEqualTo("Build model 'java.util.Map'");
    }

    @Test
    void theOtherStatusesLeaveTheProgressAsItIs() {
        progress.status("Starting", "70% Starting Java Language Server - Build");
        for (var type : new String[] {"ProjectStatus", "Started", "ServiceReady", "Error"}) {
            progress.status(type, "whatever - it says");
        }

        assertThat(current()).isEqualTo(new JdtlsProgress(Phase.IMPORTING, "Build", Duration.ZERO));
    }

    @Test
    void theSystemClockIsTheUsualOne() {
        assertThat(new StartProgress().current().phase()).isEqualTo(Phase.LAUNCHING);
    }
}
