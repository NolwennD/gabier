package eu.gabier.cli.infrastructure.secondary;

import eu.gabier.cli.domain.Daemon;
import eu.gabier.cli.domain.DaemonRefusal;
import eu.gabier.cli.domain.DaemonReply;
import eu.gabier.cli.domain.DaemonStart;
import eu.gabier.protocol.Problem;
import eu.gabier.protocol.RpcResponse;
import eu.gabier.protocol.SocketLocation;
import eu.gabier.protocol.UserId;
import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * The daemon reached on its Unix socket, in JSON-RPC ({@link DaemonClient}), and started when nothing answers
 * ({@link DaemonLauncher}). Nothing is resolved before a request: a command that never asks the daemon (such as {@code
 * help}) never looks for the socket.
 */
public final class SocketDaemon implements Daemon {

    /** How long a request waits before the notice that jdtls is starting. */
    public static final Duration NOTICE_DELAY = WaitNotice.DELAY;

    /** How long a request waits for a daemon that said it is stopping to give its socket back, before it gives up. */
    private static final Duration STOPPING_WAIT = Duration.ofSeconds(5);

    private static final String STOPPING = "daemon-stopping";
    private static final Duration STOPPING_POLL = Duration.ofMillis(25);

    private final Map<String, String> environment;
    private final PrintStream err;
    private final Duration noticeDelay;
    private final Duration stoppingWait;

    /**
     * The daemon of the project the cli runs in.
     *
     * @param environment the environment of the cli, where the socket of the daemon is resolved
     * @param err where the notice of a request that waits for jdtls is printed
     * @param noticeDelay how long a request waits before that notice
     */
    public SocketDaemon(Map<String, String> environment, PrintStream err, Duration noticeDelay) {
        this(environment, err, noticeDelay, STOPPING_WAIT);
    }

    SocketDaemon(Map<String, String> environment, PrintStream err, Duration noticeDelay, Duration stoppingWait) {
        this.environment = environment;
        this.err = err;
        this.noticeDelay = noticeDelay;
        this.stoppingWait = stoppingWait;
    }

    @Override
    public DaemonReply ask(String method, Map<String, String> params) {
        var launcher = DaemonLauncher.of(environment);
        var socket = socket();

        try (var notice = WaitNotice.during(socket, noticeDelay, err)) {
            return callAndAskAgainIfItStopped(socket, () -> launcher.connect(socket), method, params);
        }
    }

    @Override
    public DaemonReply askRunning(String method, boolean awaitEnd) {
        var socket = socket();

        return call(() -> DaemonClient.connect(socket), method, Map.of(), awaitEnd);
    }

    @Override
    public DaemonStart askStarting(String method) {
        var launcher = DaemonLauncher.of(environment);
        var socket = socket();
        var daemonStarted = new AtomicBoolean();

        var result = callAndAskAgainIfItStopped(
                socket,
                () -> DaemonClient.tryConnect(socket).orElseGet(() -> {
                    daemonStarted.set(true);

                    return launcher.connect(socket);
                }),
                method,
                Map.of());

        return new DaemonStart(result, daemonStarted.get(), DaemonLauncher.logOf(socket));
    }

    @Override
    public DaemonReply statusAfter(Duration pause) {
        try {
            Thread.sleep(pause);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();

            throw DaemonException.connectionFailed("the wait for jdtls was interrupted", interrupted);
        }

        return status(socket());
    }

    @Override
    public Notice noticeWhileWaiting() {
        return WaitNotice.during(socket(), noticeDelay, err);
    }

    @Override
    public Path socket() {
        return SocketLocation.resolve(environment, UserId.current(), Path.of("").toAbsolutePath());
    }

    /** The status of the daemon, asked on a connection of its own. */
    private static DaemonReply status(Path socket) {
        var connected = DaemonClient.tryConnect(socket)
                .orElseThrow(() -> DaemonException.connectionFailed(
                        "the daemon stopped answering while jdtls was starting", null));

        return call(() -> connected, "status", Map.of(), false);
    }

    /**
     * Calls the daemon that the supplier connects to. A daemon that stops by itself when it is idle answers a request
     * that reaches it once it has decided with the refusal {@code daemon-stopping}, and has not run it: the call then
     * waits, for {@link #STOPPING_WAIT} at most, until nothing answers on the socket any more, and asks once more,
     * which starts a new daemon. A daemon that is still there after the wait, and a second {@code daemon-stopping}, are
     * {@code daemon-connection-failed}, which says so: no daemon took the call, and the command is not asked a third
     * time. Any other failure, a connection that ends with no answer included (the request may have been run), is the
     * failure it is and is never asked again.
     */
    private DaemonReply callAndAskAgainIfItStopped(
            Path socket, Supplier<DaemonClient> daemon, String method, Map<String, String> params) {
        try {
            return call(daemon, method, params, false);
        } catch (DaemonRefusal refusal) {
            if (!STOPPING.equals(refusal.key())) {
                throw refusal;
            }
            if (!goneAfterWaiting(socket)) {
                throw stillStopping(refusal);
            }
        }
        try {
            return call(daemon, method, params, false);
        } catch (DaemonRefusal again) {
            throw STOPPING.equals(again.key()) ? stillStopping(again) : again;
        }
    }

    private static DaemonException stillStopping(DaemonRefusal refusal) {
        return DaemonException.connectionFailed(
                "the daemon is stopping, did not run the call, and no new daemon took it", refusal);
    }

    /** Whether nothing answers on the socket any more, looked at every {@link #STOPPING_POLL} for the wait at most. */
    private boolean goneAfterWaiting(Path socket) {
        var deadline = System.nanoTime() + stoppingWait.toNanos();
        while (true) {
            var still = DaemonClient.tryConnect(socket);
            if (still.isEmpty()) {
                return true;
            }
            still.get().close();
            if (System.nanoTime() >= deadline) {
                return false;
            }
            // A wait that an interruption does not cut short, and does not clear: it is bounded by the deadline.
            new CompletableFuture<Void>()
                    .completeOnTimeout(null, STOPPING_POLL.toNanos(), TimeUnit.NANOSECONDS)
                    .join();
        }
    }

    private static DaemonReply call(
            Supplier<DaemonClient> daemon, String method, Map<String, String> params, boolean awaitEnd) {
        try (var client = daemon.get()) {
            var response = client.call(method, params);
            if (response instanceof RpcResponse.Failure failure) {
                throw refusal(failure.problem());
            }
            if (awaitEnd) {
                client.awaitEnd();
            }

            return new DaemonReply(((RpcResponse.Success) response).result());
        }
    }

    private static DaemonRefusal refusal(Problem problem) {
        return new DaemonRefusal(
                problem.type(),
                problem.title(),
                problem.detail(),
                problem.key(),
                problem.parameters(),
                problem.category());
    }
}
