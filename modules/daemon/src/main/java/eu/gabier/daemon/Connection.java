package eu.gabier.daemon;

import eu.gabier.protocol.JsonRpc;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import org.jspecify.annotations.Nullable;

/**
 * One client of the daemon: reads its lines, answers each with one line, until the client has nothing more to say. The
 * streams are the ones of a socket, or any streams in a test.
 */
final class Connection {

    private Connection() {}

    /**
     * Serves the client, then closes the streams. A client that disconnects, or whose connection fails, ends its own
     * connection and nothing else: there is no one left to tell.
     */
    static void serve(InputStream in, OutputStream out, RequestHandler handler) {
        try (in;
                out) {
            var lines = new LineReader(in);
            RequestHandler.Reply reply;
            while ((reply = nextReply(lines, handler)) != null) {
                out.write((reply.line() + "\n").getBytes(StandardCharsets.UTF_8));
                out.flush();
                reply.afterReply().run();
            }
        } catch (IOException e) {
            // The client is gone, or the daemon closes the connection while it stops: nothing to answer to.
        }
    }

    /** The answer to the next line, null when the client has nothing more to say; a line over the limit is refused. */
    private static RequestHandler.@Nullable Reply nextReply(LineReader lines, RequestHandler handler)
            throws IOException {
        try {
            var line = lines.readLine();

            return line == null ? null : handler.handle(line);
        } catch (LineTooLongException tooLong) {
            return RequestHandler.Reply.of(JsonRpc.failure(null, tooLong));
        }
    }
}
