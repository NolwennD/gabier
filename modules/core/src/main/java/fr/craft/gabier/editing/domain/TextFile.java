package fr.craft.gabier.editing.domain;

import fr.craft.gabier.shared.error.domain.Assert;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * The text of a file, as gabier edits it. A file is UTF-8, with or without the byte order mark ({@code EF BB BF}): the
 * mark is not part of the text (so it does not shift the first column of the first line) and is put back when the text
 * is encoded. Bytes that are not valid UTF-8 are refused, never replaced: a file in another encoding is left alone
 * instead of being corrupted.
 *
 * @param text the text, without the byte order mark
 * @param bom whether the file starts with the byte order mark
 */
record TextFile(String text, boolean bom) {

    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private static final Pattern LINE_TERMINATOR = Pattern.compile("\\r\\n|\\r|\\n");

    static Optional<TextFile> tryDecode(byte[] bytes) {
        boolean bom = bytes.length >= BOM.length && Arrays.equals(bytes, 0, BOM.length, BOM, 0, BOM.length);
        int offset = bom ? BOM.length : 0;

        try {
            String text = StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes, offset, bytes.length - offset))
                    .toString();

            return Optional.of(new TextFile(text, bom));
        } catch (CharacterCodingException e) {
            return Optional.empty();
        }
    }

    static TextFile decode(Path file, byte[] bytes) {
        return tryDecode(bytes).orElseThrow(() -> EditingException.unsupportedEncoding(file, "is not valid UTF-8"));
    }

    /**
     * Applies edits together. Every position is measured on this text, not on the text as the other edits changed it,
     * so the order of the list does not matter, except for insertions at the same point, which come out in the order of
     * the list. A replacement is written with the line terminator of the file (the first one it has), so that a file
     * keeps its line endings; a file without any terminator takes the replacement as it is.
     *
     * @throws EditingException if a position is not in the text, or if two edits share text
     */
    TextFile edit(Path file, List<TextEdit> edits) {
        LineIndex index = new LineIndex(text);
        List<Located> located = edits.stream()
                .map(edit -> locate(file, index, edit))
                .sorted(Comparator.comparingInt(Located::start).thenComparingInt(Located::end))
                .toList();
        for (int i = 1; i < located.size(); i++) {
            Located before = located.get(i - 1);
            Located after = located.get(i);
            if (before.end() > after.start()) {
                throw EditingException.overlappingEdits(
                        file, before.edit().range(), after.edit().range());
            }
        }

        StringBuilder result = new StringBuilder();
        int cursor = 0;
        for (Located edit : located) {
            result.append(text, cursor, edit.start())
                    .append(withTerminatorOf(index, edit.edit().newText()));
            cursor = edit.end();
        }
        result.append(text, cursor, text.length());

        return new TextFile(result.toString(), bom);
    }

    /**
     * Where {@code word} first occurs as a whole word: not next to a character that can be part of a Java identifier,
     * so {@code greet} is not found in {@code greeting}. Comments and string literals are text like any other.
     */
    Optional<Position> firstWord(String word) {
        Assert.notBlank("word", word);

        for (int at = text.indexOf(word); at >= 0; at = text.indexOf(word, at + 1)) {
            if (isWholeWord(at, at + word.length())) {
                LineIndex index = new LineIndex(text);
                int line = index.lineOf(at);

                return Optional.of(new Position(line, at - index.start(line)));
            }
        }

        return Optional.empty();
    }

    private boolean isWholeWord(int start, int end) {
        return (start == 0 || !Character.isJavaIdentifierPart(text.codePointBefore(start)))
                && (end == text.length() || !Character.isJavaIdentifierPart(text.codePointAt(end)));
    }

    private Located locate(Path file, LineIndex index, TextEdit edit) {
        return new Located(
                offset(file, index, edit.range().start()),
                offset(file, index, edit.range().end()),
                edit);
    }

    private int offset(Path file, LineIndex index, Position position) {
        if (position.line() >= index.lineCount() || position.column() > index.contentLength(position.line())) {
            throw EditingException.staleEdit(file, position);
        }
        int offset = index.start(position.line()) + position.column();
        // A low surrogate is always preceded by its high surrogate in a text that UTF-8 can encode.
        if (offset < text.length() && Character.isLowSurrogate(text.charAt(offset))) {
            throw EditingException.staleEdit(file, position);
        }

        return offset;
    }

    private static String withTerminatorOf(LineIndex index, String newText) {
        String terminator = index.firstTerminator();

        return terminator == null
                ? newText
                : LINE_TERMINATOR.matcher(newText).replaceAll(Matcher.quoteReplacement(terminator));
    }

    private record Located(int start, int end, TextEdit edit) {}

    /** Where the lines of a text start and end, and the first terminator it uses. */
    private static final class LineIndex {

        private final int[] starts;
        private final int[] contentEnds;
        private final @Nullable String firstTerminator;

        LineIndex(String text) {
            List<Integer> lineStarts = new ArrayList<>();
            List<Integer> lineEnds = new ArrayList<>();
            String terminator = null;
            lineStarts.add(0);
            int i = 0;
            while (i < text.length()) {
                char c = text.charAt(i);
                if (c == '\n' || c == '\r') {
                    boolean crlf = c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n';
                    int length = crlf ? 2 : 1;
                    if (terminator == null) {
                        terminator = text.substring(i, i + length);
                    }
                    lineEnds.add(i);
                    i += length;
                    lineStarts.add(i);
                } else {
                    i++;
                }
            }
            lineEnds.add(text.length());
            starts = lineStarts.stream().mapToInt(Integer::intValue).toArray();
            contentEnds = lineEnds.stream().mapToInt(Integer::intValue).toArray();
            firstTerminator = terminator;
        }

        int lineCount() {
            return starts.length;
        }

        /** The line that holds the offset, which must be inside a line and not in its terminator. */
        int lineOf(int offset) {
            int found = Arrays.binarySearch(starts, offset);

            return found >= 0 ? found : -found - 2;
        }

        int start(int line) {
            return starts[line];
        }

        int contentLength(int line) {
            return contentEnds[line] - starts[line];
        }

        @Nullable
        String firstTerminator() {
            return firstTerminator;
        }
    }

    byte[] encode(Path file) {
        try {
            ByteBuffer encoded = StandardCharsets.UTF_8
                    .newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(CharBuffer.wrap(text));
            byte[] bytes = new byte[(bom ? BOM.length : 0) + encoded.remaining()];
            int offset = 0;
            if (bom) {
                System.arraycopy(BOM, 0, bytes, 0, BOM.length);
                offset = BOM.length;
            }
            encoded.get(bytes, offset, encoded.remaining());

            return bytes;
        } catch (CharacterCodingException e) {
            throw EditingException.unsupportedEncoding(file, "has a text that UTF-8 cannot encode");
        }
    }
}
