package eu.gabier.cli;

import eu.gabier.protocol.JsonRpc;
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
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A daemon of the tests whose jdtls goes through states: it answers {@code start} with the first status it was given and
 * each {@code status} with the next one (a whole JSON-RPC line stands for itself), the last staying for the ones that follow. A test changes what comes next with
 * {@link #thenSays}, and can have the daemon stop listening after it answers ({@link #stopsListeningAfterTheStart}).
 */
final class StartingJdtlsDaemon implements AutoCloseable {

    private final ServerSocketChannel listener;
    private final List<String> requests = new CopyOnWriteArrayList<>();
    private final String start;
    private List<String> statuses;
    private int next;
    private boolean closesAfterTheStart;

    /**
     * Listens on the socket.
     *
     * @param start the result of {@code start}
     * @param statuses the results of the following {@code status} requests, in turn; the last one repeats
     */
    StartingJdtlsDaemon(Path socket, String start, String... statuses) throws IOException {
        this.start = start;
        this.statuses = List.of(statuses);
        listener = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
        listener.bind(UnixDomainSocketAddress.of(socket));
        Thread.ofVirtual().start(this::accept);
    }

    /** From now on, {@code status} answers these results in turn. */
    synchronized void thenSays(String... statuses) {
        this.statuses = List.of(statuses);
        next = 0;
    }

    /** The daemon stops listening once it has answered {@code start}, as one that ends does. */
    synchronized void stopsListeningAfterTheStart() {
        closesAfterTheStart = true;
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
            // The test is over, or the daemon went away.
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
            requests.add(line);
            var isStart = line.contains("\"method\":\"start\"");
            var result = isStart ? start : nextStatus();
            var answer = result.startsWith("{\"jsonrpc\"") ? result : JsonRpc.success("1", result);
            client.write(ByteBuffer.wrap((answer + "\n").getBytes(StandardCharsets.UTF_8)));
            if (isStart && closesAfterTheStart()) {
                listener.close();
            }
        } catch (IOException disconnected) {
            // The cli went away.
        }
    }

    private synchronized String nextStatus() {
        var status = statuses.get(Math.min(next, statuses.size() - 1));
        next++;

        return status;
    }

    private synchronized boolean closesAfterTheStart() {
        return closesAfterTheStart;
    }

    @Override
    public void close() throws IOException {
        listener.close();
    }
}
