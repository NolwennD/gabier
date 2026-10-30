package fr.craft.gabier.protocol;

/**
 * Text that is safe to print on a terminal: what the client shows a human, as opposed to the JSON a program reads. One
 * function, for the text form of a problem and for every line the cli prints from what a server or a user gave.
 */
public final class TerminalText {

    private static final int COMBINING_GRAPHEME_JOINER = 0x034F;
    private static final int HANGUL_CHOSEONG_FILLER = 0x115F;
    private static final int HANGUL_JUNGSEONG_FILLER = 0x1160;
    private static final int BRAILLE_PATTERN_BLANK = 0x2800;
    private static final int HANGUL_FILLER = 0x3164;
    private static final int HALFWIDTH_HANGUL_FILLER = 0xFFA0;
    private static final int FIRST_VARIATION_SELECTOR = 0xFE00;
    private static final int LAST_VARIATION_SELECTOR = 0xFE0F;
    private static final int FIRST_VARIATION_SELECTOR_SUPPLEMENT = 0xE0100;
    private static final int LAST_VARIATION_SELECTOR_SUPPLEMENT = 0xE01EF;

    private TerminalText() {}

    /**
     * The text in which what a terminal would obey, or what it would not show, is written out.
     *
     * <ul>
     *   <li>These are shown as an escape: a backslash, {@code u} and four hexadecimal digits (the escape of the ESC
     *       character ends in {@code 001b}), or, for a code point beyond the basic plane (a tag character, a
     *       variation selector of the supplement), a backslash, {@code u} and the hexadecimal code point between
     *       braces:
     *       <ul>
     *         <li>the control characters (C0, DEL, C1: {@link Character#CONTROL});
     *         <li>the line and paragraph separators (U+2028, U+2029);
     *         <li>the format characters (bidirectional controls, zero-width characters, tag characters:
     *             {@link Character#FORMAT});
     *         <li>every space separator but the space itself (U+0020): the no-break space U+00A0, U+1680,
     *             U+2000 to U+200A, U+202F, U+205F and U+3000 ({@link Character#SPACE_SEPARATOR});
     *         <li>the characters that print as blank or that change their neighbour without showing: the Hangul
     *             fillers (U+115F, U+1160, U+3164, U+FFA0), the braille pattern blank (U+2800), the combining grapheme
     *             joiner (U+034F) and the variation selectors (U+FE00 to U+FE0F, U+E0100 to U+E01EF);
     *         <li>an unpaired surrogate: it is no character at all, and a terminal would print a replacement for it,
     *             so it is shown as the escape of its code unit.
     *       </ul>
     *       The text stays on its line and inert.
     *   <li>A backslash is shown as two, so that an escape is told from the same characters written by hand: the
     *       text is unambiguous, and the original can be read back from it. A path with backslashes is shown doubled.
     *   <li>Everything else stays as it is: the space (U+0020), the letters, digits, punctuation and symbols of any
     *       script (an accent, an ideograph), and a pair of surrogates, which is one character (an emoji).
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
        return switch (Character.getType(codePoint)) {
            case Character.CONTROL,
                    Character.FORMAT,
                    Character.LINE_SEPARATOR,
                    Character.PARAGRAPH_SEPARATOR,
                    Character.SURROGATE -> true;
            case Character.SPACE_SEPARATOR -> codePoint != ' ';
            default -> isBlankOrInvisibleMark(codePoint);
        };
    }

    // What the Unicode categories do not call hidden, and a terminal does not show.
    private static boolean isBlankOrInvisibleMark(int codePoint) {
        return switch (codePoint) {
            case COMBINING_GRAPHEME_JOINER,
                    HANGUL_CHOSEONG_FILLER,
                    HANGUL_JUNGSEONG_FILLER,
                    BRAILLE_PATTERN_BLANK,
                    HANGUL_FILLER,
                    HALFWIDTH_HANGUL_FILLER -> true;
            default ->
                isBetween(codePoint, FIRST_VARIATION_SELECTOR, LAST_VARIATION_SELECTOR)
                        || isBetween(
                                codePoint, FIRST_VARIATION_SELECTOR_SUPPLEMENT, LAST_VARIATION_SELECTOR_SUPPLEMENT);
        };
    }

    private static boolean isBetween(int codePoint, int first, int last) {
        return codePoint >= first && codePoint <= last;
    }

    private static String escape(int codePoint) {
        return Character.isBmpCodePoint(codePoint)
                ? String.format("\\u%04x", codePoint)
                : String.format("\\u{%x}", codePoint);
    }
}
