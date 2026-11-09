package eu.gabier.cli.infrastructure.primary.mcp;

import eu.gabier.protocol.Json;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;

/**
 * A client of the server that talks to it as a host does: it writes a line, reads the next line the server wrote, and
 * goes on, instead of writing the whole input and reading the whole output. It is what shows what the server answers
 * while a call is still waiting. Whatever waits waits for a latch, never for a given time, and a read that gets nothing
 * in ten seconds fails the test.
 */
final class McpSession implements AutoCloseable {

    private static final byte[] END = new byte[0];
    private static final long PATIENCE_SECONDS = 10;

    private final BlockingQueue<byte[]> toServer = new LinkedBlockingQueue<>();
    private final BlockingQueue<String> fromServer = new LinkedBlockingQueue<>();
    private final Thread serving;

    /** A session with this server, which runs in a thread of its own until the session is {@link #finish() finished}. */
    McpSession(McpServer server) {
        var out = new PrintStream(new Lines(fromServer), true, StandardCharsets.UTF_8);
        serving = Thread.ofPlatform().daemon().name("mcp-session").start(() -> server.serve(new Queued(toServer), out));
    }

    /** Writes a line to the server. */
    void send(String line) {
        toServer.add((line + "\n").getBytes(StandardCharsets.UTF_8));
    }

    /** The next line the server writes, as JSON; the test fails when none comes. */
    @Nullable
    Object next() throws InterruptedException {
        return Json.parse(nextRaw());
    }

    /** The next line the server writes, as the server wrote it. */
    String nextRaw() throws InterruptedException {
        var line = fromServer.poll(PATIENCE_SECONDS, TimeUnit.SECONDS);
        if (line == null) {
            throw new AssertionError("The server wrote nothing in " + PATIENCE_SECONDS + " seconds");
        }

        return line;
    }

    /** Whether the server has written a line that nobody has read. */
    boolean hasLine() {
        return !fromServer.isEmpty();
    }

    /** Closes the input of the server, as a host does when it is done. */
    void endInput() {
        toServer.add(END);
    }

    /** Whether the server is still running, which it is while a call it was asked has not ended. */
    boolean isServing() {
        return serving.isAlive();
    }

    /** Waits for the server to end, which it does once its input is closed and its calls have ended. */
    void awaitEnd() throws InterruptedException {
        serving.join(TimeUnit.SECONDS.toMillis(PATIENCE_SECONDS));
        if (serving.isAlive()) {
            throw new AssertionError("The server did not end in " + PATIENCE_SECONDS + " seconds");
        }
    }

    /** Closes the input of the server, as a host does, and waits for the server to end. */
    void finish() throws InterruptedException {
        endInput();
        awaitEnd();
    }

    @Override
    public void close() throws InterruptedException {
        finish();
    }

    /** What the server reads: the lines the test wrote, then the end of the input. */
    private static final class Queued extends InputStream {

        private final BlockingQueue<byte[]> chunks;
        private byte[] current = new byte[0];
        private int position;
        private boolean ended;

        private Queued(BlockingQueue<byte[]> chunks) {
            this.chunks = chunks;
        }

        @Override
        public int read() throws IOException {
            var one = new byte[1];

            return read(one, 0, 1) < 0 ? -1 : one[0] & 0xFF;
        }

        /** As a pipe does: the bytes that are there, at least one, and not a wait for the buffer to be full. */
        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            while (position == current.length) {
                if (ended) {
                    return -1;
                }
                try {
                    current = chunks.take();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException(interrupted);
                }
                position = 0;
                ended = current == END;
            }
            var count = Math.min(length, current.length - position);
            System.arraycopy(current, position, buffer, offset, count);
            position += count;

            return count;
        }
    }

    /** What the server writes, handed to the test one line at a time. */
    private static final class Lines extends OutputStream {

        private final BlockingQueue<String> lines;
        private final ByteArrayOutputStream line = new ByteArrayOutputStream();

        private Lines(BlockingQueue<String> lines) {
            this.lines = lines;
        }

        @Override
        public synchronized void write(int b) {
            if (b == '\n') {
                lines.add(line.toString(StandardCharsets.UTF_8));
                line.reset();
            } else {
                line.write(b);
            }
        }
    }
}
