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
    private @Nullable RuntimeException failure;

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

    @Override
    public DaemonReply ask(String method, Map<String, String> params) {
        asked.add(new Asked(method, params, false));

        return reply();
    }

    @Override
    public DaemonReply askRunning(String method, boolean awaitEnd) {
        asked.add(new Asked(method, Map.of(), true));

        return reply();
    }

    private DaemonReply reply() {
        observer.run();
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
