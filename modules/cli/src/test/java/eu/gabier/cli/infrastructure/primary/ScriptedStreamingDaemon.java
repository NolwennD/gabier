package eu.gabier.cli.infrastructure.primary;

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
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * A daemon of the tests that follows something: it listens on a real Unix socket, reads the request of a client,
 * answers it, then writes the lines the test gives it, when the test gives them ({@link #says}), until the test makes
 * it hang up ({@link #hangsUp}) or the client goes away ({@link #clientGone}).
 */
public final class ScriptedStreamingDaemon implements AutoCloseable {

    private final ServerSocketChannel listener;
    private final String answer;
    private final BlockingQueue<Optional<String>> lines = new LinkedBlockingQueue<>();
    private final List<String> requests = new CopyOnWriteArrayList<>();
    private final CountDownLatch clientGone = new CountDownLatch(1);

    /**
     * Listens on the socket and plays the daemon.
     *
     * @param answer the line to write back to the request of the client
     */
    public ScriptedStreamingDaemon(Path socket, String answer) throws IOException {
        this.answer = answer;
        listener = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
        listener.bind(UnixDomainSocketAddress.of(socket));
        Thread.ofVirtual().start(this::accept);
    }

    /** The requests received so far, one line each. */
    public List<String> requests() {
        return requests;
    }

    /** Writes this line to the client, after the answer and the lines given before it. */
    public void says(String line) {
        lines.add(Optional.of(line));
    }

    /** Closes the connection once what was said is written: what a daemon that stops does. */
    public void hangsUp() {
        lines.add(Optional.empty());
    }

    /** Whether the client closed its end of the connection, within ten seconds. */
    public boolean clientGone() throws InterruptedException {
        return clientGone.await(10, TimeUnit.SECONDS);
    }

    private void accept() {
        try {
            var client = listener.accept();
            serve(client);
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
            Thread.ofVirtual().start(() -> watchForTheEnd(reader));
            write(client, answer);
            for (var next = lines.take(); next.isPresent(); next = lines.take()) {
                write(client, next.get());
            }
        } catch (IOException e) {
            clientGone.countDown();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** The client reads nothing more than it sent: its end of the connection closing is seen as the end of its input. */
    private void watchForTheEnd(BufferedReader reader) {
        try {
            while (reader.readLine() != null) {
                // Nothing is expected after the request.
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            clientGone.countDown();
        }
    }

    private static void write(SocketChannel client, String line) throws IOException {
        client.write(ByteBuffer.wrap((line + "\n").getBytes(StandardCharsets.UTF_8)));
    }

    @Override
    public void close() throws IOException {
        listener.close();
    }
}
