package eu.gabier.cli.infrastructure.primary;

import eu.gabier.protocol.JsonRpc;
import eu.gabier.protocol.Problem;
import eu.gabier.shared.error.domain.ErrorStatus;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

/**
 * The program behind the {@code gabier-daemon} script of the tests: a daemon of its own process, listening on {@code
 * GABIER_SOCKET} and answering a request with an empty list of diagnostics, a rename with nothing changed, and a start with the status of a daemon whose jdtls is starting. It says on its standard output (the
 * log the cli keeps) where it runs, which project it was told of and its pid, then serves for a minute at most, so that
 * a test that forgets to end it leaves nothing behind.
 */
public final class FakeDaemonMain {

    private static final long LIFETIME_MILLIS = 60_000;
    private static final String NO_DIAGNOSTICS = "{\"diagnostics\":[]}";
    private static final String NOTHING_CHANGED =
            "{\"created\":[],\"renamed\":[],\"deleted\":[],\"modified\":[],\"diff\":\"\"}";

    private static final String STARTING = """
        {"version":"0.1","pid":4242,"uptimeMillis":15,"project":"/work/demo","jdtls":"starting","imports":%s}\
        """.formatted(CliFixtures.IMPORTS);

    private FakeDaemonMain() {}

    /** The refusal, as a line of the protocol, with this key: the answer to every request of a refusing daemon. */
    private static String refusalWith(String key) {
        return JsonRpc.failure(
                "1",
                new Problem(
                        "urn:gabier:error:" + key,
                        "Refused",
                        "The daemon refuses",
                        key,
                        Map.of(),
                        ErrorStatus.BAD_REQUEST));
    }

    /**
     * Starts the daemon.
     *
     * @param args none for a daemon that answers; else the key of the refusal that it answers to every request
     */
    public static void main(String[] args) throws IOException, InterruptedException {
        var refusal = Arrays.stream(args).findFirst().map(FakeDaemonMain::refusalWith);
        var listener = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
        listener.bind(UnixDomainSocketAddress.of(Path.of(System.getenv("GABIER_SOCKET"))));
        System.out.println(
                "listening cwd=" + System.getProperty("user.dir") + " project=" + System.getenv("GABIER_PROJECT")
                        + " pid=" + ProcessHandle.current().pid());
        System.out.flush();
        Thread.ofVirtual().start(() -> accept(listener, refusal));
        Thread.sleep(LIFETIME_MILLIS);
        System.exit(0);
    }

    private static void accept(ServerSocketChannel listener, Optional<String> refusal) {
        try {
            while (true) {
                var client = listener.accept();
                Thread.ofVirtual().start(() -> serve(client, refusal));
            }
        } catch (IOException closed) {
            // The process is ending.
        }
    }

    private static void serve(SocketChannel client, Optional<String> refusal) {
        try (client) {
            var reader =
                    new BufferedReader(new InputStreamReader(Channels.newInputStream(client), StandardCharsets.UTF_8));
            var line = reader.readLine();
            if (line != null) {
                var result = line.contains("\"method\":\"start")
                        ? STARTING
                        : line.contains("\"method\":\"rename") ? NOTHING_CHANGED : NO_DIAGNOSTICS;
                var answer = refusal.orElseGet(() -> JsonRpc.success("1", result)) + "\n";
                client.write(ByteBuffer.wrap(answer.getBytes(StandardCharsets.UTF_8)));
            }
        } catch (IOException disconnected) {
            // A client that leaves is not the daemon's concern.
        }
    }
}
