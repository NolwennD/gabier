package eu.gabier.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;

/**
 * A daemon of the tests: it listens on a real Unix socket, reads one line per client and writes the line it was given
 * (or nothing, or hangs up), then waits for the test to let it close the connection. It stands for the real daemon
 * that the end-to-end stage of {@code bin/ci} starts.
 */
final class ScriptedDaemon implements AutoCloseable {

    private final ServerSocketChannel listener;
    private final String answer;
    private final CountDownLatch mayHangUp;
    private final List<String> requests = new CopyOnWriteArrayList<>();

    /**
     * Listens on the socket and plays the daemon with a scripted answer.
     *
     * @param answer the line to write back to each request, or null to hang up without answering
     * @param mayHangUp the connection is kept open until the latch opens, then closed: what a stopping daemon does
     */
    ScriptedDaemon(Path socket, String answer, CountDownLatch mayHangUp) throws IOException {
        this.answer = answer;
        this.mayHangUp = mayHangUp;
        listener = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
        listener.bind(UnixDomainSocketAddress.of(socket));
        Thread.ofVirtual().start(this::accept);
    }

    ScriptedDaemon(Path socket, String answer) throws IOException {
        this(socket, answer, new CountDownLatch(0));
    }

    /** The requests received so far, one line each. */
    List<String> requests() {
        return requests;
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
            if (line != null) {
                requests.add(line);
            }
            if (answer != null) {
                client.write(ByteBuffer.wrap((answer + "\n").getBytes(StandardCharsets.UTF_8)));
            }
            mayHangUp.await();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() throws IOException {
        listener.close();
    }
}
