package fr.craft.gabier.daemon;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

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
            String line;
            while ((line = lines.readLine()) != null) {
                var reply = handler.handle(line);
                out.write((reply.line() + "\n").getBytes(StandardCharsets.UTF_8));
                out.flush();
                reply.afterReply().run();
            }
        } catch (IOException e) {
            // The client is gone, or the daemon closes the connection while it stops: nothing to answer to.
        }
    }
}
