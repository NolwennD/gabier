package eu.gabier.cli.domain;

import eu.gabier.shared.error.domain.Assert;
import java.util.List;

/**
 * The guide of the improvements: the page {@code docs/improve.md} as a text, and the situations it describes. How the
 * page is found (a resource of the build, for the command line) is for the adapter that reads it.
 *
 * @param text the text of the page
 */
public record Guide(String text) {

    public Guide {
        Assert.notBlank("text", text);
    }

    /** The situations of the guide, in the order of the page. */
    public List<GuideSection> sections() {
        return GuideSections.parse(text);
    }
}
