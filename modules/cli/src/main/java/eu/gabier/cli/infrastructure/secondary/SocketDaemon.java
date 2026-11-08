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

    private final Map<String, String> environment;
    private final PrintStream err;
    private final Duration noticeDelay;

    /**
     * The daemon of the project the cli runs in.
     *
     * @param environment the environment of the cli, where the socket of the daemon is resolved
     * @param err where the notice of a request that waits for jdtls is printed
     * @param noticeDelay how long a request waits before that notice
     */
    public SocketDaemon(Map<String, String> environment, PrintStream err, Duration noticeDelay) {
        this.environment = environment;
        this.err = err;
        this.noticeDelay = noticeDelay;
    }

    @Override
    public DaemonReply ask(String method, Map<String, String> params) {
        var launcher = DaemonLauncher.of(environment);
        var socket = socket();

        try (var notice = WaitNotice.during(socket, noticeDelay, err)) {
            return call(() -> launcher.connect(socket), method, params, false);
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

        var result = call(
                () -> DaemonClient.tryConnect(socket).orElseGet(() -> {
                    daemonStarted.set(true);

                    return launcher.connect(socket);
                }),
                method,
                Map.of(),
                false);

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
