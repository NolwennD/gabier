package fr.craft.gabier.daemon;

import java.util.regex.Pattern;

/**
 * The text of a new file as it is written, from the snippet jdtls completes an empty file with: the snippet is for an
 * editor, which puts its cursor at {@code ${0}}. The line of the cursor in a body goes (an editor would leave its
 * indentation there), the cursor between the parentheses of a record goes, and the file ends with a line break.
 */
final class NewTypeText {

    private static final Pattern CURSOR_LINE = Pattern.compile("(?m)^[ \\t]*\\$\\{0}[ \\t]*\\n");
    private static final String CURSOR = "${0}";

    private NewTypeText() {}

    static String of(String snippet) {
        String text = CURSOR_LINE.matcher(snippet).replaceAll("").replace(CURSOR, "");

        return text.endsWith("\n") ? text : text + "\n";
    }
}
