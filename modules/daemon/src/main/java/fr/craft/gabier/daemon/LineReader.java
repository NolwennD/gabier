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
 *
 * <p>A line holds {@value #MAX_LINE_BYTES} bytes at most, its line break not counted: the reader never keeps more than
 * that, whatever the client sends. A longer line is refused as soon as its first byte over the limit is read; the rest
 * of it is dropped, unread by anyone, when the next line is asked for.
 */
final class LineReader {

    /** A request is a path and a few names: one mebibyte is far more than any of them needs. */
    static final int MAX_LINE_BYTES = 1024 * 1024;

    private final InputStream input;
    private final int limit;
    private boolean dropping;

    LineReader(InputStream input) {
        this(input, MAX_LINE_BYTES);
    }

    LineReader(InputStream input, int limit) {
        this.input = new BufferedInputStream(input);
        this.limit = limit;
    }

    /**
     * The next line without its line break, or null when the client has nothing more to say.
     *
     * @throws LineTooLongException when the line is longer than the limit
     */
    @Nullable
    String readLine() throws IOException {
        if (dropping && !dropRestOfLine()) {
            return null;
        }
        var line = new ByteArrayOutputStream();
        int next;
        while ((next = input.read()) != -1) {
            if (next == '\n') {
                return line.toString(StandardCharsets.UTF_8).stripTrailing();
            }
            if (line.size() == limit) {
                dropping = true;
                throw LineTooLongException.over(limit);
            }
            line.write(next);
        }

        return null;
    }

    /** Reads up to the end of the line that was refused, and tells whether its line break was there. */
    private boolean dropRestOfLine() throws IOException {
        int next;
        do {
            next = input.read();
        } while (next != -1 && next != '\n');
        dropping = false;

        return next != -1;
    }
}
