package eu.gabier.cli.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GuideTest {

    @Test
    void holdsTheTextOfThePage() {
        assertThat(new Guide("# The guide\n").text()).isEqualTo("# The guide\n");
    }

    @Test
    void hasTheSectionsOfThePageInOrder() {
        var guide = new Guide("""
            # The guide

            ### First

            Words: one

            Ask: `gabier one`.

            ### Second

            Words: two

            By hand: do two.
            """);

        assertThat(guide.sections()).extracting(GuideSection::title).containsExactly("First", "Second");
    }
}
