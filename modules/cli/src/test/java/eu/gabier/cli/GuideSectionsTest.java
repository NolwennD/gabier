package eu.gabier.cli;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GuideSectionsTest {

    private static final String PAGE = """
        # The guide

        An introduction that is no section.

        ## One family

        ### A string that grows

        Words: text block, multi-line string

        Ask: `gabier cleanup --dry-run <file> stringConcatToTextBlock`, or for one
        concatenation the action `Convert to Text Block`.

        Before:

        ```java
        String a = "one" + "two";
        ```

        Limits: needs Java 15.

        ## Another family

        A sentence about the family, which is no part of the section above.

        ### An else after a return

        By hand: remove the `else`.

        Words: unnecessary else
        """;

    @Test
    void aSectionHasItsTitleItsWordsItsCommandOnOneLineAndTheRestOfItsTextWithNoCode() {
        var sections = GuideSections.parse(PAGE);

        assertThat(sections).hasSize(2);
        var first = sections.getFirst();
        assertThat(first.title()).isEqualTo("A string that grows");
        assertThat(first.words()).isEqualTo("text block, multi-line string");
        assertThat(first.how())
                .isEqualTo("Ask: `gabier cleanup --dry-run <file> stringConcatToTextBlock`, or for one"
                        + " concatenation the action `Convert to Text Block`.");
        assertThat(first.byHand()).isFalse();
        assertThat(first.body())
                .contains("Before:", "Limits: needs Java 15.")
                .doesNotContain("String a", "```", "Words:");
    }

    @Test
    void aSectionEndsAtTheNextHeadingOfAnyLevelAndByHandIsRecognisedWhereverItIs() {
        var second = GuideSections.parse(PAGE).get(1);

        assertThat(second.title()).isEqualTo("An else after a return");
        assertThat(second.how()).isEqualTo("By hand: remove the `else`.");
        assertThat(second.byHand()).isTrue();
        assertThat(second.words()).isEqualTo("unnecessary else");
        assertThat(second.body()).isEmpty();
    }

    @Test
    void aSectionWithNoWordsLineHasNoWords() {
        var sections = GuideSections.parse("""
            ### A title

            Ask: `gabier inline <file>`
            """);

        assertThat(sections.getFirst().words()).isEmpty();
    }

    @Test
    void aPageWithNoSectionHasNone() {
        assertThat(GuideSections.parse("# Only a title\n\nSome text.\n")).isEmpty();
    }

    @Test
    void theSectionsOfTheGuideAreAllOfItsHeadingsWithTheirWordsAndTheirCommand() {
        var headings =
                Guide.text().lines().filter(line -> line.startsWith("### ")).toList();

        var sections = GuideSections.ofTheGuide();

        assertThat(sections).extracting(section -> "### " + section.title()).isEqualTo(headings);
        assertThat(sections).extracting(GuideSection::title).doesNotHaveDuplicates();
        assertThat(sections).allSatisfy(section -> {
            assertThat(section.words()).isNotBlank();
            assertThat(section.how()).matches("(Ask|By hand): .+");
        });
        assertThat(sections).extracting(GuideSection::byHand).contains(true, false);
    }

    @Test
    void aSectionIsFoundByItsTitleItsWordsAndItsText() {
        var document = GuideSections.parse(PAGE).getFirst().document();

        assertThat(document.title()).isEqualTo("A string that grows");
        assertThat(document.keywords()).isEqualTo("text block, multi-line string");
        assertThat(document.body()).contains("stringConcatToTextBlock", "needs Java 15");
    }
}
