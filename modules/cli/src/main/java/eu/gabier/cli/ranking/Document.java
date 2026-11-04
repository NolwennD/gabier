package eu.gabier.cli.ranking;

import eu.gabier.shared.error.domain.Assert;

/**
 * A piece of text that can be found: a title, the keywords a person would use for it, and the rest of the text.
 *
 * @param title the title, which weighs more than the body
 * @param keywords a line of the words a person says about it, which weighs more than the body
 * @param body the rest of the text
 */
public record Document(String title, String keywords, String body) {

    public Document {
        Assert.notNull("title", title);
        Assert.notNull("keywords", keywords);
        Assert.notNull("body", body);
    }
}
