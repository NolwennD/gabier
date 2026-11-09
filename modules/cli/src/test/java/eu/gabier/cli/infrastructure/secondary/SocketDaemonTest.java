package eu.gabier.cli.infrastructure.secondary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import eu.gabier.shared.error.domain.ErrorStatus;
import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The daemon of a cli that waits for jdtls: what the pause between two questions does when the wait is cut short. */
class SocketDaemonTest {

    @TempDir
    Path directory;

    @Test
    void aPauseThatIsInterruptedIsAConnectionFailureAndKeepsTheInterruption() {
        var daemon = new SocketDaemon(
                Map.of("GABIER_SOCKET", directory.resolve("gabier.sock").toString()),
                new PrintStream(PrintStream.nullOutputStream()),
                Duration.ofSeconds(1));
        Thread.currentThread().interrupt();

        try {
            // The interruption is there before the pause begins, so the pause ends at once, whatever the machine does.
            var failure = catchThrowableOfType(DaemonException.class, () -> daemon.statusAfter(Duration.ofMinutes(1)));

            assertThat(failure.getMessage())
                    .isEqualTo("The connection to the daemon failed: the wait for jdtls was interrupted");
            assertThat(failure.status()).isEqualTo(ErrorStatus.INTERNAL_SERVER_ERROR);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }
}
