package eu.gabier.cli.ranking;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The words of a text, for the ranking: cut at case changes ({@code stringConcatToTextBlock} is "string concat to text
 * block", {@code XMLParser} is "xml parser"), at punctuation, hyphens, underscores and digits, and lower-cased. A word
 * of one letter ({@code i}, {@code s}) is dropped: in code it is a name, not a subject. There is no stemming and no list
 * of stop words.
 */
final class Words {

    private Words() {}

    /** The words of this text, in order, a word as many times as it is there. */
    static List<String> split(String text) {
        var words = new ArrayList<String>();
        var word = new StringBuilder();
        for (var position = 0; position < text.length(); position++) {
            var letter = text.charAt(position);
            if (!Character.isLetter(letter) || startsNewWord(text, position, word)) {
                add(words, word);
            }
            if (Character.isLetter(letter)) {
                word.append(letter);
            }
        }
        add(words, word);

        return List.copyOf(words);
    }

    /**
     * Whether the capital at this position starts a word after the letters of {@code word}: it follows a lower-case
     * letter ({@code concatTo}), or it ends a run of capitals that is followed by a lower-case letter ({@code XMLParser}).
     */
    private static boolean startsNewWord(String text, int position, StringBuilder word) {
        if (word.isEmpty() || !Character.isUpperCase(text.charAt(position))) {
            return false;
        }
        var before = text.charAt(position - 1);

        return Character.isLowerCase(before)
                || (position + 1 < text.length() && Character.isLowerCase(text.charAt(position + 1)));
    }

    private static void add(List<String> words, StringBuilder word) {
        if (word.length() > 1) {
            words.add(word.toString().toLowerCase(Locale.ROOT));
        }
        word.setLength(0);
    }
}
