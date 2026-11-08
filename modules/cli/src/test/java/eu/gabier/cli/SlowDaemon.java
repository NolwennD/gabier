package eu.gabier.cli;

import eu.gabier.protocol.JsonRpc;
import eu.gabier.protocol.Problem;
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
import java.util.concurrent.CountDownLatch;
import java.util.function.Supplier;

/**
 * A daemon of the tests whose jdtls is still starting: it answers {@code status} at once, with the result it was given,
 * and every other request only once the test lets it, as the real daemon does when the request waits for jdtls.
 */
final class SlowDaemon implements AutoCloseable {

    private final ServerSocketChannel listener;
    private final String status;
    private final String response;
    private final CountDownLatch mayAnswer = new CountDownLatch(1);

    /**
     * Listens on the socket.
     *
     * @param status the result of {@code status}
     * @param answer the result of any other request, written once {@link #answer()} is called
     */
    SlowDaemon(Path socket, String status, String answer) throws IOException {
        this(socket, status, () -> JsonRpc.success("1", answer));
    }

    private SlowDaemon(Path socket, String status, Supplier<String> response) throws IOException {
        this.status = status;
        this.response = response.get();
        listener = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
        listener.bind(UnixDomainSocketAddress.of(socket));
        Thread.ofVirtual().start(this::accept);
    }

    /** A daemon whose other requests are refused with this problem once {@link #answer()} is called. */
    static SlowDaemon refusing(Path socket, String status, Problem problem) throws IOException {
        return new SlowDaemon(socket, status, () -> JsonRpc.failure("1", problem));
    }

    /** Lets the requests that wait get their answer. */
    void answer() {
        mayAnswer.countDown();
    }

    private void accept() {
        try {
            while (true) {
                var client = listener.accept();
                Thread.ofVirtual().start(() -> serve(client));
            }
        } catch (IOException closed) {
            // The test is over.
        }
    }

    private void serve(SocketChannel client) {
        try (client) {
            var reader =
                    new BufferedReader(new InputStreamReader(Channels.newInputStream(client), StandardCharsets.UTF_8));
            var line = reader.readLine();
            if (line == null) {
                return;
            }
            if (!line.contains("\"method\":\"status\"")) {
                mayAnswer.await();
            }
            var written = line.contains("\"method\":\"status\"") ? JsonRpc.success("1", status) : response;
            client.write(ByteBuffer.wrap((written + "\n").getBytes(StandardCharsets.UTF_8)));
        } catch (IOException closed) {
            // The cli went away.
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() throws IOException {
        mayAnswer.countDown();
        listener.close();
    }
}
