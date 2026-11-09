package eu.gabier.cli.infrastructure.primary;

import eu.gabier.cli.ranking.Document;
import eu.gabier.shared.error.domain.Assert;

/**
 * One situation of the guide, a {@code ###} section of {@code docs/improve.md}.
 *
 * @param title the title, the situation
 * @param words the line {@code Words:}, what a person says about the problem
 * @param how the paragraph {@code Ask:} or {@code By hand:}, as the section writes it, on one line
 * @param body the rest of the text of the section, with no code
 */
record GuideSection(String title, String words, String how, String body) {

    private static final String BY_HAND = "By hand:";

    GuideSection {
        Assert.notBlank("title", title);
        Assert.notNull("words", words);
        Assert.notBlank("how", how);
        Assert.notNull("body", body);
    }

    /** Whether gabier has no command for this situation. */
    boolean byHand() {
        return how.startsWith(BY_HAND);
    }

    /** What the section says to do, without its label: the command or the action, or the way to do it by hand. */
    String instruction() {
        return how.substring(how.indexOf(':') + 1).strip();
    }

    /** The text to find it by. */
    Document document() {
        return new Document(title, words, how + " " + body);
    }
}
