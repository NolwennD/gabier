package eu.gabier.cli.infrastructure.primary.mcp;

import eu.gabier.protocol.DaemonStatus;
import eu.gabier.protocol.Json;
import eu.gabier.protocol.TerminalText;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * The calls that wait for the daemon, while the server goes on reading what the client writes. A call is run by a thread
 * of its own and answered when it ends, in whatever order they end (the id tells the answers apart, as JSON-RPC
 * means it to). Three things end a call, and the first that comes wins, behind one lock with the writing, so that no
 * message follows the end of a call:
 *
 * <ul>
 *   <li>the thread of the call, which has the daemon's answer, or its refusal;
 *   <li>the bound ({@link Pacing#BOUND}): a call that has waited that long is answered as a failed call, and its thread
 *       is left to end by itself (interrupting it would make the launch of a daemon kill the daemon it is starting);
 *   <li>a cancellation by the client, which is answered by nothing, as the protocol asks.
 * </ul>
 *
 * <p>Every {@link Pacing#INTERVAL} the calls that asked for it (they carry a {@code progressToken}) are told where the
 * daemon is, with a {@code notifications/progress}, whose {@code progress} grows at each notice.
 */
final class Calls {

    /**
     * A call to run.
     *
     * @param id the id of the request, as JSON
     * @param key what tells this id from another
     * @param progressToken the token of the request as JSON, none when the client did not ask to be told how it goes
     * @param answer the message that answers the request, from the members of its result
     * @param work what the thread of the call runs, to the members of its result; it never throws
     * @param status where the daemon is, none when it does not say: asked to tell how the call goes
     * @param timedOut the members of the result of a call that was given up
     */
    record Job(
            String id,
            String key,
            @Nullable String progressToken,
            Function<String, String> answer,
            Supplier<String> work,
            Supplier<Optional<DaemonStatus>> status,
            Supplier<String> timedOut) {}

    private final Output output;
    private final Pacing pacing;
    private final Executor threads;
    private final Map<String, Call> pending = new LinkedHashMap<>();
    private @Nullable Runnable stopTicker;

    /**
     * The calls of one run of the server.
     *
     * @param output where the answers and the notices are written
     * @param pacing the clock and the ticker
     * @param threads where a call runs
     */
    Calls(Output output, Pacing pacing, Executor threads) {
        this.output = output;
        this.pacing = pacing;
        this.threads = threads;
    }

    /**
     * Whether a call with this id is still running: no other request may use the id until the call has been answered.
     * The answer and the end of the call are written behind the lock of the output, so that is where this looks: a
     * client that has read the answer and sends another request with the id never finds the call still running.
     */
    boolean isRunning(String key) {
        synchronized (output) {
            synchronized (this) {
                return pending.containsKey(key);
            }
        }
    }

    /**
     * Runs a call in the background; its answer is written when it ends. The caller has checked that no call with the
     * same id is running ({@link #isRunning}); only the thread that reads the client starts calls, so none starts in
     * between.
     */
    void start(Job job) {
        Call call;
        synchronized (this) {
            call = new Call(job, pacing.nanoClock().getAsLong());
            pending.put(job.key(), call);
            if (stopTicker == null) {
                stopTicker = pacing.ticker().every(pacing.interval(), this::tick);
            }
        }
        threads.execute(() -> call.end(job.work().get()));
    }

    /** Ends the call of this id, if it still runs, without an answer. */
    void cancel(String key) {
        Call call;
        synchronized (this) {
            call = pending.get(key);
        }
        if (call != null) {
            call.cancel();
        }
    }

    /** Waits until no call runs: the client closed its output, and what it asked is answered. */
    void drain() {
        try {
            synchronized (this) {
                while (!pending.isEmpty()) {
                    wait();
                }
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } finally {
            stopTicking();
        }
    }

    private synchronized void stopTicking() {
        if (stopTicker != null) {
            stopTicker.run();
            stopTicker = null;
        }
    }

    private synchronized void release(Call call) {
        pending.remove(call.job.key(), call);
        notifyAll();
    }

    /**
     * What happens every interval: the calls that have waited too long are given up, then the others that asked are told.
     * The bound depends on this thread, so nothing here waits for the daemon for long: the calls are given up first, and
     * a notice waits for the status of the daemon no longer than {@link Pacing#statusLimit()}.
     */
    private void tick() {
        List<Call> running;
        synchronized (this) {
            running = List.copyOf(pending.values());
        }
        var now = pacing.nanoClock().getAsLong();
        var toTell = new ArrayList<Call>();
        for (var call : running) {
            if (Duration.ofNanos(now - call.begun).compareTo(pacing.bound()) >= 0) {
                call.end(call.job.timedOut().get());
            } else if (call.job.progressToken() != null) {
                toTell.add(call);
            }
        }
        toTell.forEach(call -> call.tell(Duration.ofNanos(now - call.begun)));
    }

    /** What a notice says: where jdtls is and for how long the call has waited. */
    private static String message(Optional<DaemonStatus> status, Duration waited) {
        var seconds = waited.toSeconds() + "s so far";
        var text = status.map(now -> {
                    var doing = now.jdtlsProgress()
                            .map(progress -> ": " + progress.text())
                            .orElse("");

                    return "jdtls is " + now.jdtls().text() + doing + " (waiting " + seconds + ")";
                })
                .orElse("waiting for the daemon (" + seconds + ")");

        return TerminalText.neutralise(text);
    }

    private final class Call {

        private final Job job;
        private final long begun;
        private final AtomicBoolean asking = new AtomicBoolean();
        private boolean ended;
        private long progress;

        private Call(Job job, long begun) {
            this.job = job;
            this.begun = begun;
        }

        /**
         * Answers the call, unless it was ended before. The call is released once its answer is written, so that the
         * end of the input, which waits for the calls, never comes before the last answer.
         */
        void end(String members) {
            synchronized (output) {
                if (ended) {
                    return;
                }
                ended = true;
                output.write(job.answer().apply(members));
                release(this);
            }
        }

        void cancel() {
            synchronized (output) {
                ended = true;
                release(this);
            }
        }

        /** A notice of progress, unless the call has ended. */
        void tell(Duration waited) {
            var message = message(status(), waited);
            synchronized (output) {
                if (!ended) {
                    progress++;
                    output.write("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/progress\",\"params\":{"
                            + "\"progressToken\":" + job.progressToken() + ",\"progress\":" + progress
                            + ",\"message\":" + Json.quote(message) + "}}");
                }
            }
        }

        /**
         * Where the daemon is, asked in a thread of its own and waited for {@link Pacing#statusLimit()} at most: the
         * daemon is asked on a connection that has no limit of time, and a daemon that never answers must not hold the
         * ticker. None while the previous question is still waiting (no second thread is spent on a daemon that does
         * not answer), or when the question fails or runs out of time.
         */
        private Optional<DaemonStatus> status() {
            if (!asking.compareAndSet(false, true)) {
                return Optional.empty();
            }

            return CompletableFuture.supplyAsync(
                            () -> {
                                try {
                                    return job.status().get();
                                } finally {
                                    asking.set(false);
                                }
                            },
                            threads)
                    .completeOnTimeout(Optional.empty(), pacing.statusLimit().toNanos(), TimeUnit.NANOSECONDS)
                    .exceptionally(failure -> Optional.empty())
                    .join();
        }
    }
}
