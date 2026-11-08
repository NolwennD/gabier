package eu.gabier.cli;

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
 * One run of a command: its arguments, whether the output is JSON, where it is printed, and the way to the daemon.
 *
 * @param arguments the words of the command line, the command first
 * @param json whether {@code --json} was given
 * @param out where the result is printed
 * @param err where what is not a result is printed: the notice of a request that waits for jdtls
 * @param environment the environment of the cli, where the socket of the daemon is resolved
 * @param noticeDelay how long a request waits before the notice that jdtls is starting ({@link WaitNotice})
 */
record Invocation(
        Arguments arguments,
        boolean json,
        PrintStream out,
        PrintStream err,
        Map<String, String> environment,
        Duration noticeDelay) {

    /**
     * The result of a request command: the daemon answers, or it is started (the cli never leaves a request unanswered
     * for want of a daemon). A refusal of the daemon is thrown. While the request waits for a jdtls that is still starting,
     * a line on the standard error says so ({@link WaitNotice}).
     */
    RpcResponse.Success ask(String method, Map<String, String> params) {
        var launcher = DaemonLauncher.of(environment);
        var socket = socket();

        try (var notice = WaitNotice.during(socket, noticeDelay, err)) {
            return call(() -> launcher.connect(socket), method, params, false);
        }
    }

    /**
     * The result of {@code status} or {@code stop}: the daemon answers or it is not running, and none is started. It
     * waits for the daemon to hang up when asked.
     */
    RpcResponse.Success askRunning(String method, boolean awaitEnd) {
        var socket = socket();

        return call(() -> DaemonClient.connect(socket), method, Map.of(), awaitEnd);
    }

    /**
     * As {@link #ask}, and says whether this call had to start the daemon. No notice: the answer is at once, whatever
     * the state of jdtls.
     */
    Started askStarting(String method) {
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

        return new Started(result, daemonStarted.get());
    }

    /**
     * The result of a request that may have started the daemon.
     *
     * @param result what the daemon answered
     * @param daemonStarted whether the daemon was not running and this call started it
     */
    record Started(RpcResponse.Success result, boolean daemonStarted) {}

    /** The socket of the daemon of the project this run is in. */
    Path socket() {
        return SocketLocation.resolve(environment, UserId.current(), Path.of("").toAbsolutePath());
    }

    private static RpcResponse.Success call(
            Supplier<DaemonClient> daemon, String method, Map<String, String> params, boolean awaitEnd) {
        try (var client = daemon.get()) {
            var response = client.call(method, params);
            if (response instanceof RpcResponse.Failure failure) {
                throw new DaemonRefusal(failure.problem());
            }
            if (awaitEnd) {
                client.awaitEnd();
            }

            return (RpcResponse.Success) response;
        }
    }
}
