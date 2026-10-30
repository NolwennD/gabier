package fr.craft.gabier.daemon;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.jspecify.annotations.Nullable;

/**
 * Reads the lines a client sends, in UTF-8. A line is complete once its line break is read: what a client sent before
 * it disconnected, without the line break, is a request that was cut, and it is not read.
 */
final class LineReader {

    private final InputStream input;

    LineReader(InputStream input) {
        this.input = new BufferedInputStream(input);
    }

    /** The next line without its line break, or null when the client has nothing more to say. */
    @Nullable
    String readLine() throws IOException {
        var line = new ByteArrayOutputStream();
        int next;
        while ((next = input.read()) != -1) {
            if (next == '\n') {
                return line.toString(StandardCharsets.UTF_8).stripTrailing();
            }
            line.write(next);
        }

        return null;
    }
}
