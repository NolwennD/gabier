package eu.gabier.cli.infrastructure.primary.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/** The race between a notice of progress and the end of the call it tells about, which no client can time. */
class CallsTest {

    @Test
    void tellsNothingOfACallThatEndedWhileTheNoticeWasBeingMade() throws Exception {
        var written = new ByteArrayOutputStream();
        var time = new ManualTime();
        var held = new CountDownLatch(1);
        var calls = new Calls(
                new Output(new PrintStream(written, true, StandardCharsets.UTF_8)),
                time.pacing(),
                task -> Thread.ofPlatform().daemon().start(task));
        calls.start(new Calls.Job(
                "1",
                "n:1",
                "7",
                members -> members,
                () -> {
                    awaitQuietly(held);

                    return "{}";
                },
                () -> {
                    // The client cancels the call at the moment the server asks where the daemon is.
                    calls.cancel("n:1");

                    return Optional.empty();
                },
                () -> "{}"));

        time.tick();
        held.countDown();
        calls.drain();

        assertThat(written.toString(StandardCharsets.UTF_8)).isEmpty();
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("The latch was not released in 10 seconds");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
