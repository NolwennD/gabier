package eu.gabier.daemon;

import eu.gabier.protocol.JsonRpc;
import eu.gabier.protocol.Problem;
import eu.gabier.shared.error.domain.GeneratorException;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * What is written to a client: one writer thread per connection writes every line, the answers to the requests and the
 * notifications of a subscription, so that two lines never mix and no other thread waits for a socket. An answer is
 * written as soon as it is queued, before any notification; the caller waits until it is written. A subscription holds
 * at most one state not written yet, which {@link Peer.Listener#offer} replaces, so a client that does not read costs
 * the daemon one thread (its writer, blocked in the write) and one state, and holds up no one.
 */
final class Outbox implements Peer, AutoCloseable {

    /** The name of the writer thread of a connection. */
    static final String WRITER = "gabier-writer";

    /** An answer to write, and what completes once it is written. */
    private record Answer(String line, CompletableFuture<Void> written) {}

    private final OutputStream out;
    private final Runnable onFailure;
    private final Thread writer;
    private final Deque<Answer> answers = new ArrayDeque<>();
    private @Nullable Subscription<?> subscription;
    private boolean closed;

    /**
     * Starts the writer.
     *
     * @param onFailure runs when a line cannot be written (the client is gone): the connection then ends
     */
    Outbox(OutputStream out, Runnable onFailure) {
        this.out = out;
        this.onFailure = onFailure;
        this.writer = Thread.ofVirtual().name(WRITER).start(this::write);
    }

    /**
     * Writes an answer, and returns once it is written.
     *
     * @throws IOException when the line could not be written, or the connection ended
     */
    void answer(String line) throws IOException {
        var answer = new Answer(line, new CompletableFuture<>());
        synchronized (this) {
            if (closed) {
                throw new IOException("The connection ended");
            }
            answers.add(answer);
            notifyAll();
        }
        try {
            answer.written().get();
        } catch (ExecutionException failure) {
            throw new IOException("The line could not be written", failure.getCause());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();

            throw new IOException("Interrupted while writing", interrupted);
        }
    }

    /** Lets the subscription of the connection, if there is one, write: called once the answer to its request is. */
    synchronized void startNotifying() {
        if (subscription != null) {
            subscription.armed = true;
            notifyAll();
        }
    }

    @Override
    public synchronized <S> Listener<S> listen(
            Function<S, List<String>> notifications, BinaryOperator<S> merge, Consumer<Listener<S>> whenGone) {
        if (subscription != null) {
            throw GeneratorException.badRequest(DaemonErrorKey.ALREADY_WATCHING)
                    .message("This connection already follows something: ask on another connection")
                    .build();
        }
        var listener = new Subscription<>(notifications, merge, whenGone);
        subscription = listener;

        return listener;
    }

    /** Ends the connection: the subscription is told, the stream is closed, which ends a write that is blocked. */
    @Override
    public void close() throws IOException {
        Subscription<?> ending;
        synchronized (this) {
            closed = true;
            ending = subscription;
            notifyAll();
        }
        try {
            out.close();
        } finally {
            if (ending != null) {
                ending.gone();
            }
            joinWriter();
        }
    }

    private void joinWriter() {
        try {
            writer.join();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    /** The loop of the writer: an answer first, then the state a subscription is waiting to tell. */
    private void write() {
        try {
            while (true) {
                var work = next();
                if (work == null) {
                    return;
                }
                try {
                    for (var line : work.lines().get()) {
                        out.write((line + "\n").getBytes(StandardCharsets.UTF_8));
                    }
                    out.flush();
                } catch (IOException | RuntimeException failure) {
                    work.written().completeExceptionally(failure);

                    throw failure;
                }
                work.written().complete(null);
            }
        } catch (IOException | RuntimeException failure) {
            fail(failure);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            fail(interrupted);
        }
    }

    /** What to write next, and what to do once it is written; null when the connection ended. */
    private @Nullable Work next() throws InterruptedException {
        synchronized (this) {
            Work work;
            while ((work = take()) == null) {
                if (closed) {
                    return null;
                }
                wait();
            }

            return work;
        }
    }

    /** Takes the next thing to write, holding the lock: an answer first, then what the subscription has to tell. */
    private @Nullable Work take() {
        var answer = answers.poll();
        if (answer != null) {
            return new Work(() -> List.of(answer.line()), answer.written());
        }
        if (subscription == null || !subscription.armed || closed) {
            return null;
        }
        var notice = subscription.notices.poll();
        if (notice != null) {
            return new Work(() -> List.of(JsonRpc.notice(notice)), new CompletableFuture<>());
        }
        var told = subscription.tellState();
        if (told != null) {
            return new Work(told, new CompletableFuture<>());
        }
        var reason = subscription.reason;
        if (reason != null && !subscription.finished) {
            subscription.finished = true;

            return new Work(() -> List.of(JsonRpc.ended(reason)), new CompletableFuture<>());
        }

        return null;
    }

    private void fail(Exception failure) {
        synchronized (this) {
            closed = true;
            for (var answer : answers) {
                answer.written().completeExceptionally(failure);
            }
            answers.clear();
        }
        onFailure.run();
    }

    /** Lines to write, worked out by the writer once the lock is released, and what completes once they are written, or fails. */
    private record Work(Supplier<List<String>> lines, CompletableFuture<Void> written) {}

    /** What a connection that listens has to tell: the state not written, and why it ended, if it did. */
    private final class Subscription<S> implements Listener<S> {

        private final Function<S, List<String>> notifications;
        private final BinaryOperator<S> merge;
        private final Consumer<Listener<S>> whenGone;
        private final Deque<String> notices = new ArrayDeque<>();
        private boolean armed;
        private boolean finished;
        private @Nullable S state;
        private @Nullable Problem reason;

        Subscription(Function<S, List<String>> notifications, BinaryOperator<S> merge, Consumer<Listener<S>> whenGone) {
            this.notifications = notifications;
            this.merge = merge;
            this.whenGone = whenGone;
        }

        /** The lines of the state waiting, which is then taken; none when there is none. Holding the lock. */
        @Nullable
        Supplier<List<String>> tellState() {
            var waiting = state;
            if (waiting == null) {
                return null;
            }
            state = null;

            return () -> notifications.apply(waiting);
        }

        void gone() {
            whenGone.accept(this);
        }

        @Override
        public void offer(S state) {
            synchronized (Outbox.this) {
                if (closed || reason != null) {
                    return;
                }
                var waiting = this.state;
                this.state = waiting == null ? state : merge.apply(waiting, state);
                Outbox.this.notifyAll();
            }
        }

        @Override
        public void say(String notice) {
            synchronized (Outbox.this) {
                if (closed || reason != null) {
                    return;
                }
                notices.add(notice);
                Outbox.this.notifyAll();
            }
        }

        @Override
        public void end(Problem reason) {
            synchronized (Outbox.this) {
                if (closed || this.reason != null) {
                    return;
                }
                this.reason = reason;
                Outbox.this.notifyAll();
            }
        }
    }
}
