package eu.gabier.cli.infrastructure.primary.mcp;

import eu.gabier.cli.domain.Daemon;
import eu.gabier.cli.domain.DaemonReply;
import eu.gabier.cli.domain.DaemonStart;
import eu.gabier.protocol.Json;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;

/**
 * The daemon of the tests of the server: it keeps what it is asked and answers the result, or throws the failure, that
 * the test gave it; with neither, asking it is a mistake of the test.
 */
final class StubDaemon implements Daemon {

    /** A request the daemon received. */
    record Asked(String method, Map<String, String> params, boolean running) {}

    private final List<Asked> asked = new CopyOnWriteArrayList<>();
    private Runnable observer = () -> {};
    private @Nullable String answer;
    private @Nullable String statusAnswer;
    private @Nullable CountDownLatch gate;
    private @Nullable CountDownLatch statusGate;
    private @Nullable RuntimeException failure;
    private @Nullable Error crash;
    private final CountDownLatch operationAsked = new CountDownLatch(1);

    /** Answers every request with this result, as JSON. */
    StubDaemon answering(String result) {
        answer = result;

        return this;
    }

    /** Fails every request with this failure. */
    StubDaemon failing(RuntimeException failure) {
        this.failure = failure;

        return this;
    }

    /** Runs this when the daemon is asked, before it answers, so that a test sees what happened by then. */
    StubDaemon whenAsked(Runnable observation) {
        observer = observation;

        return this;
    }

    List<Asked> asked() {
        return asked;
    }

    /** Makes every request that is not a question of {@code status} wait for this latch before it answers. */
    StubDaemon holding(CountDownLatch release) {
        gate = release;

        return this;
    }

    /** Makes every question of {@code status} wait for this latch, as a daemon that accepts and never answers does. */
    StubDaemon holdingStatus(CountDownLatch release) {
        statusGate = release;

        return this;
    }

    /** Answers {@code status} at once, whatever is held, with this result, as the daemon does while jdtls starts. */
    StubDaemon reportingStatus(String result) {
        statusAnswer = result;

        return this;
    }

    /** Fails every request with this error of the process, which a call of a tool must not let end the server. */
    StubDaemon crashing(Error crash) {
        this.crash = crash;

        return this;
    }

    /** Waits until an operation is asked, which a call in a thread of its own does after the test sent it. */
    void awaitAsked() throws InterruptedException {
        if (!operationAsked.await(10, TimeUnit.SECONDS)) {
            throw new AssertionError("The daemon was not asked in 10 seconds");
        }
    }

    @Override
    public DaemonReply ask(String method, Map<String, String> params) {
        asked.add(new Asked(method, params, false));
        operationAsked.countDown();
        awaitGate();

        return reply();
    }

    @Override
    public Following follow(String method, Map<String, String> params) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DaemonReply askRunning(String method, boolean awaitEnd) {
        asked.add(new Asked(method, Map.of(), true));
        if (method.equals("status")) {
            await(statusGate);
        }
        if (method.equals("status") && statusAnswer != null) {
            return new DaemonReply(members(Json.parse(statusAnswer)));
        }

        return reply();
    }

    private void awaitGate() {
        await(gate);
    }

    private static void await(@Nullable CountDownLatch latch) {
        if (latch == null) {
            return;
        }
        try {
            latch.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError("The daemon of the test was interrupted while it was held", interrupted);
        }
    }

    private DaemonReply reply() {
        observer.run();
        if (crash != null) {
            throw crash;
        }
        if (failure != null) {
            throw failure;
        }
        if (answer == null) {
            throw new AssertionError("The daemon was asked, and the test gave it no answer");
        }

        return new DaemonReply(members(Json.parse(answer)));
    }

    @SuppressWarnings("unchecked") // The answers of the tests are the JSON objects that the protocol types write.
    private static Map<String, @Nullable Object> members(@Nullable Object json) {
        return (Map<String, @Nullable Object>) json;
    }

    @Override
    public DaemonStart askStarting(String method) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DaemonReply statusAfter(Duration pause) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Notice noticeWhileWaiting() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Path socket() {
        throw new UnsupportedOperationException();
    }
}
