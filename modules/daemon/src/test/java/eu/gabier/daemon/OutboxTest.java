package eu.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.gabier.editing.domain.DiagnosedFile;
import eu.gabier.protocol.JsonRpc;
import eu.gabier.protocol.Problem;
import eu.gabier.shared.error.domain.ErrorStatus;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The one writer of a connection: what it does when the caller or the writer itself is interrupted, when a write fails
 * with answers waiting behind it, and when a subscription that ended is offered more. The ordinary use is in {@link
 * ConnectionFollowingTest}.
 */
class OutboxTest {

    private static final Problem BROKEN = new Problem(
            "urn:gabier:error:jdtls-down", "Jdtls is down", "It died", "jdtls-down", Map.of(), ErrorStatus.BAD_REQUEST);

    private final Output output = new Output();
    private final AtomicInteger failures = new AtomicInteger();
    private final Outbox outbox = new Outbox(output, failures::incrementAndGet);

    /** What the client reads: the bytes it was written, which can be held back and then fail. */
    private static final class Output extends OutputStream {

        private final StringBuilder written = new StringBuilder();
        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch released = new CountDownLatch(1);
        private volatile boolean held;
        private volatile boolean failsOnceReleased;

        void hold() {
            held = true;
        }

        void release(boolean fail) {
            failsOnceReleased = fail;
            held = false;
            released.countDown();
        }

        @Override
        public void write(int b) {
            throw new UnsupportedOperationException("The outbox writes arrays");
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            if (held) {
                started.countDown();
                try {
                    released.await();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();

                    throw new IOException("Interrupted", interrupted);
                }
            }
            if (failsOnceReleased) {
                throw new IOException("Broken pipe");
            }
            synchronized (written) {
                written.append(new String(bytes, offset, length, StandardCharsets.UTF_8));
            }
        }

        List<String> lines() {
            synchronized (written) {
                return written.toString().lines().toList();
            }
        }
    }

    /** A thread that writes an answer, and what became of it. */
    private final class Answering {

        private final AtomicReference<IOException> failure = new AtomicReference<>();
        private final AtomicReference<Boolean> interrupted = new AtomicReference<>();
        private final Thread thread;

        Answering(String line) {
            thread = Thread.ofPlatform().start(() -> {
                try {
                    outbox.answer(line);
                } catch (IOException failed) {
                    failure.set(failed);
                }
                interrupted.set(Thread.currentThread().isInterrupted());
            });
        }

        void join() throws InterruptedException {
            thread.join(Duration.ofSeconds(10));
        }
    }

    @AfterEach
    void endTheConnection() throws IOException {
        output.release(false);
        outbox.close();
    }

    private static void eventually(BooleanSupplier condition, String what) throws InterruptedException {
        var deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("Waited 10 s for " + what);
            }
            Thread.sleep(5);
        }
    }

    @Test
    void refusesAnAnswerOnceTheConnectionEnded() throws IOException {
        outbox.close();

        assertThatThrownBy(() -> outbox.answer("{}"))
                .isInstanceOf(IOException.class)
                .hasMessage("The connection ended");
    }

    @Test
    void failsTheCallerThatIsInterruptedWhileItsAnswerIsWrittenAndKeepsTheInterrupt() throws InterruptedException {
        output.hold();
        var answering = new Answering("{}");
        assertThat(output.started.await(10, TimeUnit.SECONDS)).isTrue();

        answering.thread.interrupt();
        answering.join();

        assertThat(answering.failure.get()).hasMessage("Interrupted while writing");
        assertThat(answering.interrupted.get()).isTrue();
    }

    @Test
    void keepsTheInterruptOfTheThreadThatClosesItWhileItsWriterIsStillWriting() throws Exception {
        output.hold();
        new Answering("{}");
        assertThat(output.started.await(10, TimeUnit.SECONDS)).isTrue();
        Thread.currentThread().interrupt();

        outbox.close();

        assertThat(Thread.interrupted()).isTrue();
    }

    @Test
    void failsTheAnswersThatWaitBehindAWriteThatFailed() throws InterruptedException {
        output.hold();
        var first = new Answering("first");
        assertThat(output.started.await(10, TimeUnit.SECONDS)).isTrue();
        var second = new Answering("second");
        eventually(() -> second.thread.getState() == Thread.State.WAITING, "the second answer to wait");

        output.release(true);
        first.join();
        second.join();

        assertThat(first.failure.get()).isNotNull();
        assertThat(second.failure.get()).isNotNull();
        eventually(() -> failures.get() == 1, "the connection to end");
    }

    @Test
    void endsTheConnectionWhenItsWriterIsInterrupted() throws InterruptedException {
        var listener = outbox.listen(
                state -> {
                    Thread.currentThread().interrupt();

                    return List.of();
                },
                ended -> {});
        outbox.startNotifying();

        listener.offer(List.of(new DiagnosedFile(Path.of("/project/A.java"), List.of())));

        eventually(() -> failures.get() == 1, "the connection to end");
    }

    @Test
    void ignoresWhatIsOfferedSaidOrEndedOnceTheSubscriptionEndedOrTheConnectionDid()
            throws IOException, InterruptedException {
        var listener = outbox.listen(state -> List.of("state"), ended -> {});
        outbox.answer("answer");
        outbox.startNotifying();
        listener.end(BROKEN);
        eventually(() -> output.lines().size() == 2, "the end to be written");

        listener.offer(List.of());
        listener.say("late notice");
        listener.end(BROKEN);
        outbox.close();
        listener.offer(List.of());
        listener.say("later notice");
        listener.end(BROKEN);

        assertThat(output.lines()).containsExactly("answer", JsonRpc.ended(BROKEN));
    }
}
