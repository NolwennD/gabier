package eu.gabier.cli.infrastructure.secondary;

import eu.gabier.cli.domain.Daemon;
import eu.gabier.protocol.DaemonStatus;
import eu.gabier.protocol.JdtlsState;
import eu.gabier.protocol.RpcResponse;
import eu.gabier.protocol.TerminalText;
import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Tells the agent, on the standard error, why a request is slow: while it waits for the answer, a thread looks at the
 * {@code status} of the daemon (on a connection of its own, so the request is not disturbed) and, when jdtls is still
 * starting, writes one line. The first line comes once the request has waited for {@link #DELAY}, so a quick answer
 * gets none; the next ones follow every {@link #REPEAT} for as long as jdtls is starting. Nothing is written once
 * {@link #close} has returned, so the line never comes after the answer. The line is for the person or the agent who
 * waits: the standard output stays the answer alone.
 */
final class WaitNotice implements Daemon.Notice {

    /** How long a request waits before the first line. */
    static final Duration DELAY = Duration.ofSeconds(3);

    /** How long after a line the next one comes. */
    static final Duration REPEAT = Duration.ofSeconds(15);

    private final Thread watcher;
    private final Object writing = new Object();
    private boolean closed;

    private WaitNotice(Duration delay, Duration repeat, Supplier<Optional<DaemonStatus>> status, PrintStream err) {
        var begun = System.nanoTime();
        watcher = Thread.ofPlatform().daemon().name("gabier-wait-notice").unstarted(() -> {
            try {
                Thread.sleep(delay);
                while (true) {
                    var waited = Duration.ofNanos(System.nanoTime() - begun);
                    status.get().filter(WaitNotice::starting).ifPresent(now -> write(err, line(now, waited)));
                    Thread.sleep(repeat);
                }
            } catch (InterruptedException closing) {
                // The answer came: there is nothing more to tell.
            }
        });
    }

    /** Starts watching the request that is about to be sent to the daemon of this socket. */
    static WaitNotice during(Path socket, Duration delay, PrintStream err) {
        return during(delay, REPEAT, () -> statusOf(socket), err);
    }

    /** The notice for a daemon whose status is given, with the delays given. */
    static WaitNotice during(
            Duration delay, Duration repeat, Supplier<Optional<DaemonStatus>> status, PrintStream err) {
        var notice = new WaitNotice(delay, repeat, status, err);
        notice.watcher.start();

        return notice;
    }

    private static boolean starting(DaemonStatus status) {
        return status.jdtls() == JdtlsState.STARTING;
    }

    /** The status of the daemon, or nothing when it does not answer: the notice is a courtesy, never a failure. */
    private static Optional<DaemonStatus> statusOf(Path socket) {
        var connected = DaemonClient.tryConnect(socket);
        if (connected.isEmpty()) {
            return Optional.empty();
        }
        var client = connected.get();
        try {
            if (client.call("status") instanceof RpcResponse.Success success) {
                return Optional.of(DaemonStatus.of(success.result()));
            }
        } catch (RuntimeException unreadable) {
            // A daemon that cannot say where jdtls is: nothing to tell.
        } finally {
            client.close();
        }

        return Optional.empty();
    }

    /**
     * The line for a daemon whose jdtls is starting, after the request has waited this long. It says what jdtls is
     * doing when the daemon knows; the text comes from jdtls, so the line is neutralised ({@link TerminalText}).
     */
    static String line(DaemonStatus status, Duration waited) {
        var doing =
                status.jdtlsProgress().map(progress -> "; " + progress.text()).orElse("");

        return TerminalText.neutralise(
                "gabier: waiting for jdtls (" + status.jdtls().text() + ", " + waited.toSeconds()
                        + "s so far" + doing + "); the first command of a session waits for it to start and import the"
                        + " project, the next ones are quick");
    }

    private void write(PrintStream err, String line) {
        synchronized (writing) {
            if (!closed) {
                err.println(line);
            }
        }
    }

    /** Stops the notice: no line is written after this returns. */
    @Override
    public void close() {
        synchronized (writing) {
            closed = true;
        }
        watcher.interrupt();
    }
}
