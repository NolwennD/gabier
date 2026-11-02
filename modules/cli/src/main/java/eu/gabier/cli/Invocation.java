package eu.gabier.cli;

import eu.gabier.protocol.RpcResponse;
import eu.gabier.protocol.SocketLocation;
import eu.gabier.protocol.UserId;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Supplier;

/**
 * One run of a command: its arguments, whether the output is JSON, where it is printed, and the way to the daemon.
 *
 * @param arguments the words of the command line, the command first
 * @param json whether {@code --json} was given
 * @param out where the result is printed
 * @param environment the environment of the cli, where the socket of the daemon is resolved
 */
record Invocation(Arguments arguments, boolean json, PrintStream out, Map<String, String> environment) {

    /**
     * The result of a request command: the daemon answers, or it is started (the cli never leaves a request unanswered
     * for want of a daemon). A refusal of the daemon is thrown.
     */
    RpcResponse.Success ask(String method, Map<String, String> params) {
        var launcher = DaemonLauncher.of(environment);
        var socket = socket();

        return call(() -> launcher.connect(socket), method, params, false);
    }

    /**
     * The result of {@code status} or {@code stop}: the daemon answers or it is not running, and none is started. It
     * waits for the daemon to hang up when asked.
     */
    RpcResponse.Success askRunning(String method, boolean awaitEnd) {
        var socket = socket();

        return call(() -> DaemonClient.connect(socket), method, Map.of(), awaitEnd);
    }

    private Path socket() {
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
