package eu.gabier.daemon;

/**
 * The JSON of the settings files of VS Code, which allow what JSON does not: comments (a line from {@code //},
 * a block between {@code /*} and its end), and a comma before a closing bracket or brace. {@link #clean} turns such a text into plain JSON, for the
 * parser of the protocol, without reading it: what a string holds is never taken for a comment or a comma, and a text
 * that is not JSON stays so for the parser to refuse.
 */
final class JsonWithComments {

    private JsonWithComments() {}

    /**
     * The text without its comments (each becomes a space, so that the tokens around it stay apart) and without the
     * commas that a closing bracket or brace follows.
     */
    static String clean(String text) {
        var cleaned = new StringBuilder(text.length());
        int comma = -1;
        int position = 0;
        while (position < text.length()) {
            char character = text.charAt(position);
            if (text.startsWith("//", position)) {
                position = endOfLine(text, position);
                cleaned.append(' ');
            } else if (text.startsWith("/*", position)) {
                position = endOfBlock(text, position);
                cleaned.append(' ');
            } else if (character == '"') {
                position = copyString(text, position, cleaned);
                comma = -1;
            } else {
                if (!Character.isWhitespace(character)) {
                    if ((character == '}' || character == ']') && comma >= 0) {
                        cleaned.deleteCharAt(comma);
                    }
                    comma = character == ',' ? cleaned.length() : -1;
                }
                cleaned.append(character);
                position++;
            }
        }

        return cleaned.toString();
    }

    private static int endOfLine(String text, int from) {
        int end = text.indexOf('\n', from);

        return end < 0 ? text.length() : end;
    }

    private static int endOfBlock(String text, int from) {
        int end = text.indexOf("*/", from + 2);

        return end < 0 ? text.length() : end + 2;
    }

    /** Copies the string that starts at the quote, up to its closing quote (an escaped one does not close it). */
    private static int copyString(String text, int from, StringBuilder cleaned) {
        int position = from;
        cleaned.append(text.charAt(position++));
        while (position < text.length()) {
            char character = text.charAt(position++);
            cleaned.append(character);
            if (character == '\\' && position < text.length()) {
                cleaned.append(text.charAt(position++));
            } else if (character == '"') {
                break;
            }
        }

        return position;
    }
}
