package fr.craft.gabier.editing.domain;

import fr.craft.gabier.shared.error.domain.Assert;
import java.nio.file.Path;
import java.util.List;

/**
 * Computing with the text of a source file held in memory, for whoever has to work out what a sequence of edits comes
 * to before anything is written: the same reading of positions, and the same line endings, as {@link ChangeApplier}.
 */
public final class SourceText {

    private SourceText() {}

    /**
     * The text once the edits are applied. They all refer to the text given, as the edits of a {@link
     * FileChange.EditFile} do.
     *
     * @param file the file the text is from, to name it in a refusal
     * @throws EditingException if an edit does not fit the text, or two of them share text
     */
    public static String edited(Path file, String text, List<TextEdit> edits) {
        Assert.notNull("file", file);
        Assert.notNull("text", text);
        Assert.notNull("edits", edits);

        return new TextFile(text, false).edit(file, edits).text();
    }

    /** Where an offset is, counted in characters (UTF-16 code units) from the start of the text. */
    public static Position positionAt(String text, int offset) {
        Assert.notNull("text", text);

        return new TextFile(text, false).positionAt(offset);
    }

    /**
     * The one edit that turns a text into another: it covers what lies between their common beginning and their common
     * end, and splits neither a surrogate pair nor a line terminator. Two equal texts give an empty replacement of an
     * empty range.
     */
    public static TextEdit replacing(String before, String after) {
        Assert.notNull("before", before);
        Assert.notNull("after", after);

        int shortest = Math.min(before.length(), after.length());
        int start = 0;
        while (start < shortest && before.charAt(start) == after.charAt(start)) {
            start++;
        }
        while (splitsAt(before, start)) {
            start--;
        }
        int common = 0;
        while (common < shortest - start
                && before.charAt(before.length() - 1 - common) == after.charAt(after.length() - 1 - common)) {
            common++;
        }
        while (splitsAt(before, before.length() - common)) {
            common--;
        }
        TextFile file = new TextFile(before, false);
        Range range = new Range(file.positionAt(start), file.positionAt(before.length() - common));

        return new TextEdit(range, after.substring(start, after.length() - common));
    }

    /** Whether the offset is between the two halves of a surrogate pair or of a {@code \r\n}. */
    private static boolean splitsAt(String text, int offset) {
        if (offset == 0 || offset == text.length()) {
            return false;
        }
        char previous = text.charAt(offset - 1);
        char next = text.charAt(offset);

        return (Character.isHighSurrogate(previous) && Character.isLowSurrogate(next))
                || (previous == '\r' && next == '\n');
    }
}
