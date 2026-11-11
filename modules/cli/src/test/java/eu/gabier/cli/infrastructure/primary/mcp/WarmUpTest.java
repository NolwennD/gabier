package eu.gabier.cli.infrastructure.primary.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** The warm-up starts the daemon once, behind the handshake, and what it meets goes to the standard error. */
class WarmUpTest {

    private final ByteArrayOutputStream log = new ByteArrayOutputStream();
    private final PrintStream err = new PrintStream(log, true, StandardCharsets.UTF_8);

    @Test
    void startsOnceWhateverTheNumberOfTimesItIsBegun() {
        var tasks = new ArrayList<Runnable>();
        var started = new AtomicInteger();
        var warmUp = new WarmUp(started::incrementAndGet, err, tasks::add);

        warmUp.begin();
        warmUp.begin();
        warmUp.begin();

        assertThat(tasks).hasSize(1);
        assertThat(started).hasValue(0);
        tasks.getFirst().run();
        assertThat(started).hasValue(1);
    }

    @Test
    void startsNothingUntilItIsBegun() {
        var tasks = new ArrayList<Runnable>();

        new WarmUp(() -> {}, err, tasks::add).awaitEnd();

        assertThat(tasks).isEmpty();
    }

    @Test
    void saysOnTheStandardErrorThatTheDaemonCouldNotBeStartedAndDoesNotFail() {
        var warmUp = new WarmUp(
                () -> {
                    throw new IllegalStateException("gabier-daemon was not found");
                },
                err,
                Runnable::run);

        warmUp.begin();
        warmUp.awaitEnd();

        assertThat(log.toString(StandardCharsets.UTF_8))
                .isEqualTo("gabier mcp: the daemon could not be started: gabier-daemon was not found"
                        + System.lineSeparator());
    }

    @Test
    void takesAnErrorOfTheProcessForAStartThatFailedToo() {
        var warmUp = new WarmUp(
                () -> {
                    throw new UnsatisfiedLinkError("no spawn in the image");
                },
                err,
                Runnable::run);

        warmUp.begin();

        assertThatCode(warmUp::awaitEnd).doesNotThrowAnyException();
        assertThat(log.toString(StandardCharsets.UTF_8))
                .isEqualTo(
                        "gabier mcp: the daemon could not be started: no spawn in the image" + System.lineSeparator());
    }

    @Test
    void isRunningFromItsBeginUntilTheStartEnds() throws InterruptedException {
        var go = new CountDownLatch(1);
        var warmUp = WarmUp.inBackground(() -> awaitQuietly(go), err);

        assertThat(warmUp.isRunning()).isFalse();
        warmUp.begin();
        assertThat(warmUp.isRunning()).isTrue();
        go.countDown();
        warmUp.awaitEnd();
        assertThat(warmUp.isRunning()).isFalse();
    }

    @Test
    void saysNothingWhenTheDaemonStarts() {
        var warmUp = new WarmUp(() -> {}, err, Runnable::run);

        warmUp.begin();
        warmUp.awaitEnd();

        assertThat(log.toByteArray()).isEmpty();
    }

    @Test
    void runsInAThreadOfItsOwnThatDoesNotKeepTheProgramAlive() throws InterruptedException {
        var inDaemonThread = new AtomicBoolean();
        var thread = new ArrayList<Thread>();
        var ran = new CountDownLatch(1);
        var warmUp = WarmUp.inBackground(
                () -> {
                    thread.add(Thread.currentThread());
                    inDaemonThread.set(Thread.currentThread().isDaemon());
                    ran.countDown();
                },
                err);

        warmUp.begin();

        assertThat(ran.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(thread.getFirst()).isNotSameAs(Thread.currentThread());
        assertThat(inDaemonThread).isTrue();
    }

    @Test
    void makesTheOneThatAwaitsItsEndWaitForIt() throws InterruptedException {
        var go = new CountDownLatch(1);
        var events = new ArrayList<String>();
        var warmUp = WarmUp.inBackground(
                () -> {
                    awaitQuietly(go);
                    synchronized (events) {
                        events.add("started");
                    }
                },
                err);
        warmUp.begin();
        var waiter = Thread.ofPlatform().start(() -> {
            warmUp.awaitEnd();
            synchronized (events) {
                events.add("awaited");
            }
        });

        waiter.join(200);
        assertThat(waiter.isAlive()).isTrue();
        go.countDown();
        waiter.join(10_000);

        synchronized (events) {
            assertThat((List<String>) events).containsExactly("started", "awaited");
        }
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
