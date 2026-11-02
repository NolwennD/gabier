package eu.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** The wait for a quiet subject, raced against the death of jdtls. A child process stands in for jdtls. */
@ExtendWith(FakeOnlyGuard.class)
class QuietWaitTest {

    private static final Duration LONG = Duration.ofMinutes(5);

    private final List<Process> started = new ArrayList<>();
    private final CompletableFuture<String> failure = new CompletableFuture<>();

    @AfterEach
    void stopTheFakeJdtls() {
        started.forEach(Process::destroyForcibly);
    }

    @Test
    void waitsUntilTheSubjectHasBeenQuietForLongEnough() throws Exception {
        var watch = new JdtlsWatch(running(), failure, LONG);
        var asked = new AtomicInteger();
        // Not quiet the first two times it is asked, then quiet for long enough.
        Supplier<Duration> quietFor = () -> asked.incrementAndGet() <= 2 ? Duration.ZERO : Duration.ofMillis(100);

        QuietWait.await(watch, "the diagnostics", Duration.ofMillis(100), quietFor);

        assertThat(asked).hasValue(3);
    }

    @Test
    void returnsAtOnceWhenTheSubjectIsAlreadyQuiet() throws Exception {
        var watch = new JdtlsWatch(running(), failure, LONG);
        var asked = new AtomicInteger();

        QuietWait.await(watch, "the diagnostics", Duration.ofMillis(100), () -> {
            asked.incrementAndGet();
            return Duration.ofSeconds(1);
        });

        assertThat(asked).hasValue(1);
    }

    @Test
    void aSubjectThatNeverGetsQuietTimesOutNamingWhatWasAwaited() throws Exception {
        var watch = new JdtlsWatch(running(), failure, Duration.ofMillis(400));

        assertThatThrownBy(() -> QuietWait.await(watch, "the diagnostics", Duration.ofSeconds(5), () -> Duration.ZERO))
                .isInstanceOf(JdtlsException.class)
                .hasMessageContaining("Timed out")
                .hasMessageContaining("waiting for the diagnostics to stay quiet for 5.0 seconds");
    }

    @Test
    void waitingForTheQuietEndsAtOnceWhenJdtlsExits() throws Exception {
        var watch = new JdtlsWatch(exitingWith(7), failure, LONG);

        assertThatThrownBy(() -> QuietWait.await(watch, "the diagnostics", LONG, () -> Duration.ZERO))
                .isInstanceOf(JdtlsException.class)
                .hasMessageContaining("jdtls exited with code 7")
                .hasMessageContaining("the diagnostics to stay quiet");
    }

    private Process running() throws IOException {
        return start("sleep", "300");
    }

    private Process exitingWith(int code) throws IOException {
        return start("sh", "-c", "exit " + code);
    }

    private Process start(String... command) throws IOException {
        Process process = new ProcessBuilder(command).start();
        started.add(process);
        return process;
    }
}
