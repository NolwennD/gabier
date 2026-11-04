package eu.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JsonWithCommentsTest {

    @Test
    void leavesPlainJsonAsItIs() {
        var json = "{\"a\": [1, 2, {\"b\": null}], \"c\": \"d\"}";

        assertThat(JsonWithComments.clean(json)).isEqualTo(json);
    }

    @Test
    void removesTheCommentsOfALineAndOfABlockLeavingASpaceInTheirPlace() {
        assertThat(JsonWithComments.clean("{// one\n\"a\":1/* two\nlines */,\"b\":2}"))
                .isEqualTo("{ \n\"a\":1 ,\"b\":2}");
    }

    @Test
    void endsACommentThatIsNotClosedWithTheText() {
        assertThat(JsonWithComments.clean("1 // to the end")).isEqualTo("1  ");
        assertThat(JsonWithComments.clean("1 /* never closed")).isEqualTo("1  ");
    }

    @Test
    void doesNotTakeWhatAStringHoldsForAComment() {
        var json = "{\"url\": \"http://x/*y*/\", \"quote\": \"a \\\" // b\"}";

        assertThat(JsonWithComments.clean(json)).isEqualTo(json);
    }

    @Test
    void copiesAStringThatIsNotClosed() {
        assertThat(JsonWithComments.clean("\"abc")).isEqualTo("\"abc");
        assertThat(JsonWithComments.clean("\"abc\\")).isEqualTo("\"abc\\");
    }

    @Test
    void removesTheCommaThatAClosingBracketOrBraceFollowsWhateverLiesBetween() {
        assertThat(JsonWithComments.clean("{\"a\": [1, 2, ], \"b\": {\"c\": 3,\n // last\n},}"))
                .isEqualTo("{\"a\": [1, 2 ], \"b\": {\"c\": 3\n  \n}}");
    }

    @Test
    void keepsACommaThatSomethingFollows() {
        assertThat(JsonWithComments.clean("[1, \"a\", [2], {}]")).isEqualTo("[1, \"a\", [2], {}]");
        assertThat(JsonWithComments.clean("[\"a\",]")).isEqualTo("[\"a\"]");
    }

    @Test
    void leavesALoneSlashForTheParserToRefuse() {
        assertThat(JsonWithComments.clean("{/}")).isEqualTo("{/}");
    }
}
