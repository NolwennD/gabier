package fr.craft.gabier.protocol;

/**
 * Text that is safe to print on a terminal: what the client shows a human, as opposed to the JSON a program reads. One
 * function, for the text form of a problem and for every line the cli prints from what a server or a user gave.
 */
public final class TerminalText {

    private TerminalText() {}

    /**
     * The text with each control character (C0, DEL, C1) and each line or paragraph separator shown as its four-digit
     * Unicode escape (a backslash, {@code u} and four hexadecimal digits). Sent to a terminal, they are read as a
     * command or a line break: shown as escapes, the text stays on its line and inert.
     */
    public static String neutralise(CharSequence text) {
        var visible = new StringBuilder(text.length());
        text.chars().forEach(character -> {
            if (Character.isISOControl(character) || character == ' ' || character == ' ') {
                visible.append(String.format("\\u%04x", character));
            } else {
                visible.append((char) character);
            }
        });

        return visible.toString();
    }
}
