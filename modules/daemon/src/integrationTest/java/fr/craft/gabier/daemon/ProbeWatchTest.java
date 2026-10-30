package fr.craft.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** What the probe waits for, raced against the death of jdtls. A child process stands in for jdtls: none is started. */
class ProbeWatchTest {

    private static final Duration LONG = Duration.ofMinutes(5);

    private final List<Process> started = new ArrayList<>();
    private final CompletableFuture<String> failure = new CompletableFuture<>();

    @AfterEach
    void stopTheFakeJdtls() {
        started.forEach(Process::destroyForcibly);
    }

    @Test
    void returnsWhatWasAwaitedWhenItArrives() throws Exception {
        var watch = new ProbeWatch(running(), failure, LONG);

        String answer = watch.await("the answer", () -> CompletableFuture.completedFuture("ready"));

        assertThat(answer).isEqualTo("ready");
    }

    @Test
    void failsAtOnceWithTheExitCodeWhenJdtlsExits() throws Exception {
        var watch = new ProbeWatch(exitingWith(3), failure, LONG);
        long begin = System.nanoTime();

        assertThatThrownBy(() -> watch.await("the answer to initialize", CompletableFuture<String>::new))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("jdtls exited with code 3")
                .hasMessageContaining("the answer to initialize");

        assertThat(Duration.ofNanos(System.nanoTime() - begin)).isLessThan(Duration.ofSeconds(30));
    }

    @Test
    void failsAtOnceWithTheMessageOfAnErrorStatus() throws Exception {
        var watch = new ProbeWatch(running(), failure, LONG);
        failure.complete("jdtls sent the status Error: boom");

        assertThatThrownBy(() -> watch.await("ServiceReady", CompletableFuture<String>::new))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("jdtls sent the status Error: boom")
                .hasMessageContaining("ServiceReady");
    }

    @Test
    void aTimeoutSaysWhatWasAwaitedAndHowLongTheProbeHadWaited() throws Exception {
        var watch = new ProbeWatch(running(), failure, Duration.ofMillis(300));

        assertThatThrownBy(() -> watch.await("the project list", CompletableFuture<String>::new))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Timed out")
                .hasMessageContaining("waiting for the project list");
    }

    @Test
    void theWaitsShareOneDeadline() throws Exception {
        var watch = new ProbeWatch(running(), failure, Duration.ofSeconds(2));
        watch.await(
                "the first answer", () -> CompletableFuture.supplyAsync(() -> "late", delayed(Duration.ofSeconds(1))));
        long begin = System.nanoTime();

        assertThatThrownBy(() -> watch.await("the second answer", CompletableFuture<String>::new))
                .isInstanceOf(AssertionError.class);

        assertThat(Duration.ofNanos(System.nanoTime() - begin)).isLessThan(Duration.ofMillis(1700));
    }

    @Test
    void aRequestThatCannotBeSentBecauseJdtlsDiedReportsTheExitCode() throws Exception {
        var watch = new ProbeWatch(exitingWith(4), failure, LONG);

        assertThatThrownBy(() -> watch.await("the project list", () -> {
                    throw new IllegalStateException("Stream closed");
                }))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("jdtls exited with code 4")
                .hasMessageContaining("the project list");
    }

    @Test
    void anAnswerThatFailedBecauseJdtlsDiedReportsTheExitCodeNotTheFailure() throws Exception {
        var watch = new ProbeWatch(exitingWith(5), failure, LONG);

        assertThatThrownBy(() -> watch.await(
                        "the answer to initialize",
                        () -> CompletableFuture.failedFuture(new IllegalStateException("Stream closed"))))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("jdtls exited with code 5");
    }

    @Test
    void anAnswerThatFailedWhileJdtlsRunsReportsItsCause() throws Exception {
        var watch = new ProbeWatch(running(), failure, LONG);

        assertThatThrownBy(() -> watch.await(
                        "the answer to initialize",
                        () -> CompletableFuture.failedFuture(new IllegalStateException("bad request"))))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("the answer to initialize")
                .hasMessageContaining("bad request")
                .hasRootCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    void aPauseLastsItsIntervalWhenAllIsWell() throws Exception {
        var watch = new ProbeWatch(running(), failure, LONG);
        long begin = System.nanoTime();

        watch.pause("the next poll", Duration.ofMillis(300));

        assertThat(Duration.ofNanos(System.nanoTime() - begin)).isGreaterThanOrEqualTo(Duration.ofMillis(300));
    }

    @Test
    void aPauseEndsAtOnceWithTheExitCodeWhenJdtlsExits() throws Exception {
        var watch = new ProbeWatch(exitingWith(6), failure, LONG);

        assertThatThrownBy(() -> watch.pause("the next poll", LONG))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("jdtls exited with code 6")
                .hasMessageContaining("the next poll");
    }

    @Test
    void aPauseEndsAtOnceWithTheMessageOfAnErrorStatus() throws Exception {
        var watch = new ProbeWatch(running(), failure, LONG);
        failure.complete("jdtls sent the status Error: boom");

        assertThatThrownBy(() -> watch.pause("the next poll", LONG))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("jdtls sent the status Error: boom");
    }

    @Test
    void aPauseNeverGoesPastTheDeadline() throws Exception {
        var watch = new ProbeWatch(running(), failure, Duration.ofMillis(200));

        assertThatThrownBy(() -> watch.pause("the next poll", LONG))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Timed out")
                .hasMessageContaining("the next poll");
    }

    @Test
    void waitsUntilTheSubjectHasBeenQuietForLongEnough() throws Exception {
        var watch = new ProbeWatch(running(), failure, LONG);
        var asked = new AtomicInteger();
        // Not quiet the first two times it is asked, then quiet for long enough.
        Supplier<Duration> quietFor = () -> asked.incrementAndGet() <= 2 ? Duration.ZERO : Duration.ofMillis(100);

        watch.awaitQuiet("the diagnostics", Duration.ofMillis(100), quietFor);

        assertThat(asked).hasValue(3);
    }

    @Test
    void returnsAtOnceWhenTheSubjectIsAlreadyQuiet() throws Exception {
        var watch = new ProbeWatch(running(), failure, LONG);
        var asked = new AtomicInteger();

        watch.awaitQuiet("the diagnostics", Duration.ofMillis(100), () -> {
            asked.incrementAndGet();
            return Duration.ofSeconds(1);
        });

        assertThat(asked).hasValue(1);
    }

    @Test
    void aSubjectThatNeverGetsQuietTimesOutNamingWhatWasAwaited() throws Exception {
        var watch = new ProbeWatch(running(), failure, Duration.ofMillis(400));

        assertThatThrownBy(() -> watch.awaitQuiet("the diagnostics", Duration.ofSeconds(5), () -> Duration.ZERO))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Timed out")
                .hasMessageContaining("waiting for the diagnostics to stay quiet for 5.0 seconds");
    }

    @Test
    void waitingForTheQuietEndsAtOnceWhenJdtlsExits() throws Exception {
        var watch = new ProbeWatch(exitingWith(7), failure, LONG);

        assertThatThrownBy(() -> watch.awaitQuiet("the diagnostics", LONG, () -> Duration.ZERO))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("jdtls exited with code 7")
                .hasMessageContaining("the diagnostics to stay quiet");
    }

    private static Executor delayed(Duration delay) {
        return CompletableFuture.delayedExecutor(delay.toMillis(), TimeUnit.MILLISECONDS);
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
