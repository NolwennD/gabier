package eu.gabier.cli.infrastructure.primary.mcp;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * Where the server writes, one message to a line. Several threads write (the one that reads the client, those that
 * answer a call, the one that tells how a call goes), so a message is written whole, under the lock of this object, which
 * the calls also take to decide, with the writing, that a call is over.
 */
final class Output {

    private final PrintStream out;

    Output(PrintStream out) {
        this.out = out;
    }

    /**
     * Writes a message as one line, in UTF-8 bytes (a {@code PrintStream} would use the encoding of the platform, which
     * can be ASCII where the cli runs). The line and paragraph separators are escaped, which JSON allows only in a
     * string, the only place they can be: a reader that splits lines on them would cut the message.
     */
    synchronized void write(String message) {
        var line = message.replace("\u2028", "\\u2028").replace("\u2029", "\\u2029") + "\n";
        var bytes = line.getBytes(StandardCharsets.UTF_8);
        out.write(bytes, 0, bytes.length);
        out.flush();
    }
}
