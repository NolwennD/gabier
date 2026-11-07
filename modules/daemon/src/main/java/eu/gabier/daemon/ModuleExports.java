package eu.gabier.daemon;

import eu.gabier.editing.domain.Position;
import eu.gabier.editing.domain.Range;
import eu.gabier.editing.domain.TextEdit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The {@code exports} and {@code opens} of a module descriptor when all the classes of a package move to another:
 * jdtls, which moves the files and rewrites what uses them, does not touch {@code module-info.java}, so the directive
 * still names a package that is gone and the module stops compiling.
 *
 * <p>The directive of the package that is emptied names the package the classes went to. When the descriptor already
 * has that directive for that package, the old one is dropped, since a package is exported once.
 */
final class ModuleExports {

    /** A directive that names a package and ends with {@code ;}, with or without {@code to}: its keyword and package. */
    private static final Pattern DIRECTIVE =
            Pattern.compile("\\b(exports|opens)\\s+([\\p{L}\\p{N}_$.]+)(?=\\s*;|\\s+to\\b[^;]*;)");

    private static final Pattern COMMENT = Pattern.compile("//[^\\n]*|/\\*.*?\\*/", Pattern.DOTALL);

    private ModuleExports() {}

    /**
     * The edits that make the directives follow the packages that were emptied.
     *
     * @param descriptor the text of the descriptor
     * @param renamed the emptied packages by their name, each to the package its classes went to
     * @return the edits, none when no directive names an emptied package; they do not overlap
     */
    static List<TextEdit> following(String descriptor, Map<String, String> renamed) {
        String code = withoutComments(descriptor);
        Map<String, Set<String>> declared = new HashMap<>();
        Matcher found = DIRECTIVE.matcher(code);
        while (found.find()) {
            declared.computeIfAbsent(found.group(1), keyword -> new HashSet<>()).add(found.group(2));
        }

        List<TextEdit> edits = new ArrayList<>();
        found.reset();
        while (found.find()) {
            String target = renamed.get(found.group(2));
            if (target == null) {
                continue;
            }
            if (declared.computeIfAbsent(found.group(1), keyword -> new HashSet<>())
                    .add(target)) {
                edits.add(new TextEdit(range(descriptor, found.start(2), found.end(2)), target));
            } else {
                edits.add(new TextEdit(dropped(code, found.start(), found.end()), ""));
            }
        }

        return edits;
    }

    /** The text with each comment replaced by blanks of the same length, so that the offsets stay true. */
    private static String withoutComments(String descriptor) {
        Matcher comment = COMMENT.matcher(descriptor);

        return comment.replaceAll(match -> match.group().replaceAll("[^\\n]", " "));
    }

    /**
     * The range of a directive that is dropped, from its keyword to its {@code ;}: the whole lines when nothing else is
     * on them, so that no blank line is left.
     */
    private static Range dropped(String code, int start, int nameEnd) {
        int end = code.indexOf(';', nameEnd) + 1;
        int lineStart = code.lastIndexOf('\n', start) + 1;
        int lineEnd = code.indexOf('\n', end);
        if (lineEnd < 0) {
            lineEnd = code.length();
        } else {
            lineEnd++;
        }
        boolean alone = code.substring(lineStart, start).isBlank()
                && code.substring(end, lineEnd).isBlank();

        return alone ? range(code, lineStart, lineEnd) : range(code, start, end);
    }

    private static Range range(String text, int start, int end) {
        return new Range(position(text, start), position(text, end));
    }

    private static Position position(String text, int offset) {
        int line = 0;
        int lineStart = 0;
        for (int newline = text.indexOf('\n');
                newline >= 0 && newline < offset;
                newline = text.indexOf('\n', newline + 1)) {
            line++;
            lineStart = newline + 1;
        }

        return new Position(line, offset - lineStart);
    }
}
