package eu.gabier.daemon;

import eu.gabier.protocol.JsonRpc;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import org.jspecify.annotations.Nullable;

/**
 * One client of the daemon: reads its lines, answers each with one line, until the client has nothing more to say. A
 * request that asked to follow something goes on telling the client what changes on the same connection, after its
 * answer ({@link Outbox}: one writer per connection). The streams are the ones of a socket, or any streams in a test.
 */
final class Connection {

    private Connection() {}

    /**
     * Serves the client, then closes the streams. A client that disconnects, or whose connection fails, ends its own
     * connection and nothing else: there is no one left to tell.
     */
    static void serve(InputStream in, OutputStream out, RequestHandler handler) {
        try (in;
                var outbox = new Outbox(out, () -> closeQuietly(in))) {
            var lines = new LineReader(in);
            RequestHandler.Reply reply;
            while ((reply = nextReply(lines, handler, outbox)) != null) {
                outbox.answer(reply.line());
                outbox.startNotifying();
                reply.afterReply().run();
            }
        } catch (IOException e) {
            // The client is gone, or the daemon closes the connection while it stops: nothing to answer to.
        }
    }

    /** The client cannot be written to: ending its input ends the connection, which the reader is waiting on. */
    private static void closeQuietly(InputStream in) {
        try {
            in.close();
        } catch (IOException alreadyClosed) {
            // The connection is ending anyway.
        }
    }

    /** The answer to the next line, null when the client has nothing more to say; a line over the limit is refused. */
    private static RequestHandler.@Nullable Reply nextReply(LineReader lines, RequestHandler handler, Peer peer)
            throws IOException {
        try {
            var line = lines.readLine();

            return line == null ? null : handler.handle(line, peer);
        } catch (LineTooLongException tooLong) {
            return RequestHandler.Reply.of(JsonRpc.failure(null, tooLong));
        }
    }
}
