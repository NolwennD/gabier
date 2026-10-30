package fr.craft.gabier.protocol;

/**
 * Text that is safe to print on a terminal: what the client shows a human, as opposed to the JSON a program reads. One
 * function, for the text form of a problem and for every line the cli prints from what a server or a user gave.
 */
public final class TerminalText {

    private TerminalText() {}

    /**
     * The text in which what a terminal would obey, or what it would not show, is written out.
     *
     * <ul>
     *   <li>A control character (C0, DEL, C1), a line or paragraph separator, a format character (bidirectional
     *       controls, zero-width characters, tag characters: {@link Character#FORMAT}) and an unpaired surrogate are
     *       shown as an escape: a backslash, {@code u} and four hexadecimal digits (the escape of the ESC character
     *       ends in {@code 001b}), or, for a code point beyond the basic plane (a tag character), a backslash, {@code
     *       u} and the hexadecimal code point between braces. The text stays on its line and inert.
     *   <li>A backslash is shown as two, so that an escape is told from the same characters written by hand: the
     *       text is unambiguous, and the original can be read back from it. A path with backslashes is shown doubled.
     *   <li>A pair of surrogates is one character and stays as it is, an emoji for instance. An unpaired surrogate is
     *       no character at all, and a terminal would print a replacement for it: it is shown as the escape of its
     *       code unit.
     * </ul>
     */
    public static String neutralise(CharSequence text) {
        var visible = new StringBuilder(text.length());
        text.codePoints().forEach(codePoint -> {
            if (codePoint == '\\') {
                visible.append("\\\\");
            } else if (isInvisibleOrActive(codePoint)) {
                visible.append(escape(codePoint));
            } else {
                visible.appendCodePoint(codePoint);
            }
        });

        return visible.toString();
    }

    private static boolean isInvisibleOrActive(int codePoint) {
        return Character.isISOControl(codePoint)
                || codePoint == ' '
                || codePoint == ' '
                || switch (Character.getType(codePoint)) {
                    case Character.FORMAT, Character.SURROGATE -> true;
                    default -> false;
                };
    }

    private static String escape(int codePoint) {
        return Character.isBmpCodePoint(codePoint)
                ? String.format("\\u%04x", codePoint)
                : String.format("\\u{%x}", codePoint);
    }
}
