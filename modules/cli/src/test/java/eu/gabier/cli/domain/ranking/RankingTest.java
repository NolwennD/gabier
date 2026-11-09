package eu.gabier.cli.domain.ranking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The formula is BM25 with k1 = 1.5, b = 0.75 and the idf of Lucene, ln(1 + (N - n + 0.5) / (n + 0.5)), N documents and
 * n of them holding the word. Every expected value below is worked out by hand in the comment of its test, with the
 * arithmetic written out, and none is read from a run.
 *
 * <p>The documents of the first tests have only a body, so that the weights of the title and of the keywords play no
 * part; the weights have their own tests below.
 */
class RankingTest {

    private static final double EXACT = 1e-9;

    private static Document body(String text) {
        return new Document("", "", text);
    }

    /**
     * Three documents of four words each, N = 3, average length 4, so that the length of each is the average and
     * the factor of a word that is there once is tf (k1 + 1) / (tf + k1 (1 - b + b dl / avgdl)) = 1 * 2.5 / (1 + 1.5 *
     * (0.25 + 0.75 * 4 / 4)) = 2.5 / (1 + 1.5) = 1. The score of a document is then the sum of the idf of the words of
     * the question that it holds.
     *
     * <p>The idf of "apple" (3 documents of 3): ln(1 + (3 - 3 + 0.5) / (3 + 0.5)) = ln(1 + 0.5 / 3.5) = ln(8 / 7). The
     * idf of "banana" (2 of 3): ln(1 + (3 - 2 + 0.5) / (2 + 0.5)) = ln(1 + 1.5 / 2.5) = ln(1.6). The idf of "cherry"
     * (1 of 3): ln(1 + (3 - 1 + 0.5) / (1 + 0.5)) = ln(1 + 2.5 / 1.5) = ln(8 / 3).
     *
     * <p>For the question "apple banana cherry": document 0 holds the three words, 1 holds apple and banana, 2 holds
     * apple only. Their scores are ln(8/7) + ln(1.6) + ln(8/3), ln(8/7) + ln(1.6) and ln(8/7). The confidence is the
     * score over the highest score a document could give, which a word repeated for ever would reach: its factor
     * tf (k1 + 1) / (tf + ...) tends to k1 + 1 = 2.5, so 2.5 * (ln(8/7) + ln(1.6) + ln(8/3)). The confidences are
     * 1 / 2.5 = 0.4, (ln(8/7) + ln(1.6)) / (2.5 * (ln(8/7) + ln(1.6) + ln(8/3))) and ln(8/7) / (2.5 * (ln(8/7) +
     * ln(1.6) + ln(8/3))).
     */
    @Test
    void theScoreOfADocumentOfAverageLengthIsTheSumOfTheIdfOfTheWordsItHolds() {
        var ranking = Ranking.of(
                List.of(body("apple banana cherry date"), body("apple banana egg fig"), body("apple grape kiwi lime")));

        var matches = ranking.rank("apple banana cherry");

        var apple = Math.log(8.0 / 7);
        var banana = Math.log(1.6);
        var cherry = Math.log(8.0 / 3);
        assertThat(matches).extracting(Match::index).containsExactly(0, 1, 2);
        assertThat(matches.get(0).score()).isCloseTo(apple + banana + cherry, within(EXACT));
        assertThat(matches.get(1).score()).isCloseTo(apple + banana, within(EXACT));
        assertThat(matches.get(2).score()).isCloseTo(apple, within(EXACT));
        var most = 2.5 * (apple + banana + cherry);
        assertThat(matches.get(0).confidence()).isCloseTo(0.4, within(EXACT));
        assertThat(matches.get(1).confidence()).isCloseTo((apple + banana) / most, within(EXACT));
        assertThat(matches.get(2).confidence()).isCloseTo(apple / most, within(EXACT));
    }

    /**
     * Three documents of 2, 4 and 6 words, N = 3, average length (2 + 4 + 6) / 3 = 4. "alpha" is in documents 0 (once)
     * and 2 (twice): n = 2, idf = ln(1 + (3 - 2 + 0.5) / (2 + 0.5)) = ln(1.6). Document 1 does not hold it and has no
     * score.
     *
     * <p>Document 0: tf = 1, dl = 2, so 1 - b + b dl / avgdl = 0.25 + 0.75 * 2 / 4 = 0.625 and the factor is 1 * 2.5 /
     * (1 + 1.5 * 0.625) = 2.5 / 1.9375 = 40 / 31. Document 2: tf = 2, dl = 6, so 0.25 + 0.75 * 6 / 4 = 1.375 and the
     * factor is 2 * 2.5 / (2 + 1.5 * 1.375) = 5 / 4.0625 = 16 / 13. The shorter document with one "alpha" beats the
     * longer one with two: 40/31 = 1.29 against 16/13 = 1.23.
     *
     * <p>The confidence is the score over 2.5 * ln(1.6), so it is the factor over 2.5: (40/31) / 2.5 = 16/31 and
     * (16/13) / 2.5 = 32/65.
     */
    @Test
    void aShortDocumentBeatsALongOneThatRepeatsTheWordAndNoDocumentWithoutTheWordIsFound() {
        var ranking = Ranking.of(List.of(
                body("alpha beta"), body("beta gamma delta epsilon"), body("alpha alpha gamma delta epsilon zeta")));

        var matches = ranking.rank("alpha");

        var idf = Math.log(1.6);
        assertThat(matches).extracting(Match::index).containsExactly(0, 2);
        assertThat(matches.get(0).score()).isCloseTo(idf * 40 / 31, within(EXACT));
        assertThat(matches.get(1).score()).isCloseTo(idf * 16 / 13, within(EXACT));
        assertThat(matches.get(0).confidence()).isCloseTo(16.0 / 31, within(EXACT));
        assertThat(matches.get(1).confidence()).isCloseTo(32.0 / 65, within(EXACT));
    }

    /**
     * The same documents as the first test and the question "apple zebra". "zebra" is in no document, n = 0, and its
     * idf is ln(1 + (3 - 0 + 0.5) / (0 + 0.5)) = ln(8). Document 0 scores ln(8/7) (apple, factor 1) and the highest
     * score of the question is 2.5 * (ln(8/7) + ln(8)): the confidence is ln(8/7) / (2.5 * (ln(8/7) + ln(8))). A word
     * that the corpus does not know makes the question less sure.
     */
    @Test
    void aWordOfTheQuestionThatNoDocumentHoldsLowersTheConfidence() {
        var ranking = Ranking.of(
                List.of(body("apple banana cherry date"), body("apple banana egg fig"), body("apple grape kiwi lime")));

        var matches = ranking.rank("apple zebra");

        var apple = Math.log(8.0 / 7);
        var zebra = Math.log(8);
        assertThat(matches).extracting(Match::index).containsExactly(0, 1, 2);
        assertThat(matches.get(0).confidence()).isCloseTo(apple / (2.5 * (apple + zebra)), within(EXACT));
    }

    @Test
    void aWordAskedTwiceCountsOnce() {
        var ranking = Ranking.of(
                List.of(body("apple banana cherry date"), body("apple banana egg fig"), body("apple grape kiwi lime")));

        assertThat(ranking.rank("cherry cherry cherry")).isEqualTo(ranking.rank("cherry"));
    }

    @Test
    void equalScoresKeepTheOrderOfTheDocuments() {
        var ranking = Ranking.of(List.of(body("apple banana"), body("apple banana"), body("cherry date")));

        assertThat(ranking.rank("banana")).extracting(Match::index).containsExactly(0, 1);
    }

    /**
     * A word of the title counts 2 times, a word of the keywords 3 times and a word of the body once, in the frequency
     * and in the length of the document. N = 3, "alpha" is in all three: idf = ln(1 + 0.5 / 3.5) = ln(8/7).
     *
     * <p>Document 0 has "alpha" in its title and "beta gamma" in its body: tf = 2, dl = 2 + 1 + 1 = 4. Document 1 has
     * it in its keywords: tf = 3, dl = 3 + 1 + 1 = 5. Document 2 has it in its body, with two more words: tf = 1, dl =
     * 3. The average length is (4 + 5 + 3) / 3 = 4, and the factor is tf * 2.5 / (tf + 1.5 * (0.25 + 0.75 * dl / 4)):
     * document 0, 5 / (2 + 1.5 * 1) = 10/7; document 1, 7.5 / (3 + 1.5 * 1.1875) = 7.5 / 4.78125 = 80/51; document 2,
     * 2.5 / (1 + 1.5 * 0.8125) = 2.5 / 2.21875 = 80/71. So the order is 1, 0, 2: the keywords beat the title, which
     * beats the body, even with the longer text.
     */
    @Test
    void aWordOfTheKeywordsCountsThreeTimesAWordOfTheTitleTwiceAndAWordOfTheBodyOnce() {
        var ranking = Ranking.of(List.of(
                new Document("alpha", "", "beta gamma"),
                new Document("", "alpha", "beta gamma"),
                new Document("", "", "alpha beta gamma")));

        var matches = ranking.rank("alpha");

        var idf = Math.log(8.0 / 7);
        assertThat(matches).extracting(Match::index).containsExactly(1, 0, 2);
        assertThat(matches.get(0).score()).isCloseTo(idf * 80 / 51, within(EXACT));
        assertThat(matches.get(1).score()).isCloseTo(idf * 10 / 7, within(EXACT));
        assertThat(matches.get(2).score()).isCloseTo(idf * 80 / 71, within(EXACT));
    }

    /**
     * The question "alpha beta" and six documents, N = 6: the keywords "alpha beta" are those of documents 0 (6 words
     * in all, 3 for each) and 1 (6 and forty "filler" in its body, 46); the four others hold eleven "filler" in their
     * body. The average length is (6 + 46 + 4 * 11) / 6 = 16. Both words are in two documents: idf = ln(1 + (6 - 2 +
     * 0.5) / (2 + 0.5)) = ln(2.8) = 1.03, distinctive, and the same for both, so the confidence is the factor over 2.5.
     * The factor is tf * 2.5 / (tf + 1.5 * (0.25 + 0.75 * dl / 16)) with tf = 3: document 0, 7.5 / (3 + 1.5 * 17/32) =
     * 160/81, a confidence of 64/81 = 0.79; document 1, 7.5 / (3 + 1.5 * 77/32) = 160/141, a confidence of 64/141 =
     * 0.454. The floor of the answer is the larger of 0.3 and 0.6 of the best confidence, 0.6 * 64/81 = 0.474:
     * document 1 is over the threshold and under the floor, so it is not part of the answer, and the best one is.
     */
    @Test
    void anAnswerLeavesOutADocumentThatIsFarBehindTheBestOne() {
        var documents = new ArrayList<Document>();
        documents.add(new Document("", "alpha beta", ""));
        documents.add(new Document("", "alpha beta", "filler ".repeat(40)));
        for (var other = 0; other < 4; other++) {
            documents.add(body("filler ".repeat(11)));
        }
        var ranking = Ranking.of(documents);

        assertThat(ranking.rank("alpha beta")).extracting(Match::index).containsExactly(0, 1);
        assertThat(ranking.rank("alpha beta").get(0).confidence()).isCloseTo(64.0 / 81, within(EXACT));
        assertThat(ranking.rank("alpha beta").get(1).confidence()).isCloseTo(64.0 / 141, within(EXACT));
        assertThat(ranking.answer("alpha beta")).extracting(Match::index).containsExactly(0);
    }

    /**
     * Twelve documents of six words, N = 12, the average length 6: four hold the keywords "alpha beta" (3 each) and
     * eight hold six "filler" in their body. The two words are in four documents: idf = ln(1 + (12 - 4 + 0.5) / (4 +
     * 0.5)) = ln(2.89) = 1.06, distinctive. The factor is 3 * 2.5 / (3 + 1.5) = 5/3 and the confidence 2/3, over the
     * threshold of 0.3 and over 0.6 of the best (0.4). All four are found; the answer keeps the first three.
     */
    @Test
    void anAnswerHasThreeDocumentsAtMost() {
        var documents = new ArrayList<Document>();
        for (var holder = 0; holder < 4; holder++) {
            documents.add(new Document("", "alpha beta", ""));
        }
        for (var other = 0; other < 8; other++) {
            documents.add(body("filler ".repeat(6)));
        }
        var ranking = Ranking.of(documents);

        assertThat(ranking.rank("alpha beta")).hasSize(4);
        assertThat(ranking.answer("alpha beta")).extracting(Match::index).containsExactly(0, 1, 2);
        assertThat(ranking.answer("alpha beta").getFirst().confidence()).isCloseTo(2.0 / 3, within(EXACT));
    }

    /**
     * "apple zebra" on the first corpus: the confidence of the best document is ln(8/7) / (2.5 * (ln(8/7) + ln(8))),
     * about 0.06, under the threshold of 0.3: nothing matches, although a document holds "apple".
     */
    @Test
    void anAnswerIsEmptyWhenTheBestDocumentIsUnderTheThreshold() {
        var ranking = Ranking.of(
                List.of(body("apple banana cherry date"), body("apple banana egg fig"), body("apple grape kiwi lime")));

        assertThat(ranking.rank("apple zebra")).isNotEmpty();
        assertThat(ranking.answer("apple zebra")).isEmpty();
        assertThat(ranking.answer("")).isEmpty();
    }

    /**
     * Ten documents, N = 10. A word is distinctive when its idf, ln(1 + (10 - n + 0.5) / (n + 0.5)), is at least 1,
     * which is n = 3 of the 10 at most (n = 3: ln(1 + 7.5 / 3.5) = 1.14; n = 4: ln(1 + 6.5 / 4.5) = 0.89). "alpha" and
     * "beta" are in the keywords of document 0 only, "gamma" in its body only, "common" in the keywords of all ten.
     */
    private static Ranking corpusOfTen() {
        var documents = new ArrayList<Document>();
        documents.add(new Document("", "alpha beta common", "gamma delta"));
        for (var other = 1; other < 10; other++) {
            documents.add(new Document("", "common", "filler" + other));
        }

        return Ranking.of(documents);
    }

    @Test
    void anAnswerNeedsTwoDistinctiveWordsOfTheQuestionInTheKeywordsOrTheTitle() {
        var ranking = corpusOfTen();

        assertThat(ranking.answer("alpha beta")).extracting(Match::index).containsExactly(0);
    }

    @Test
    void aQuestionOfOneWordHasNoAnswerWhateverTheWord() {
        var ranking = corpusOfTen();

        assertThat(ranking.rank("alpha")).extracting(Match::index).containsExactly(0);
        assertThat(ranking.rank("alpha").getFirst().confidence()).isGreaterThan(Ranking.THRESHOLD);
        assertThat(ranking.answer("alpha")).isEmpty();
    }

    @Test
    void aWordOfTheBodyOnlyIsNoEvidence() {
        var ranking = corpusOfTen();

        assertThat(ranking.rank("alpha gamma").getFirst().index()).isZero();
        assertThat(ranking.rank("alpha gamma").getFirst().confidence()).isGreaterThan(Ranking.THRESHOLD);
        assertThat(ranking.answer("alpha gamma")).isEmpty();
    }

    @Test
    void aWordThatTheDocumentsAllHoldIsNoEvidence() {
        var ranking = corpusOfTen();

        assertThat(ranking.rank("alpha common").getFirst().index()).isZero();
        assertThat(ranking.rank("alpha common").getFirst().confidence()).isGreaterThan(Ranking.THRESHOLD);
        assertThat(ranking.answer("alpha common")).isEmpty();
    }

    /**
     * A word is distinctive by the documents that hold it in their title or their keywords, not by the documents that
     * hold it anywhere: "alpha" and "beta" are in the keywords of document 0 and in the body of the nine others, as
     * "file" is in the text of most sections of the guide and in the title of one.
     */
    @Test
    void aWordIsDistinctiveByTheDocumentsThatHoldItInTheirKeywordsNotInTheirBody() {
        var documents = new ArrayList<Document>();
        documents.add(new Document("", "alpha beta", ""));
        for (var other = 1; other < 10; other++) {
            documents.add(new Document("", "", "alpha beta filler"));
        }
        var ranking = Ranking.of(documents);

        assertThat(ranking.answer("alpha beta")).extracting(Match::index).containsExactly(0);
    }

    @Test
    void aWordHeldByHalfOfTheDocumentsInTheirKeywordsIsNoEvidence() {
        var documents = new ArrayList<Document>();
        for (var holder = 0; holder < 5; holder++) {
            documents.add(new Document("", "alpha beta", ""));
        }
        for (var other = 5; other < 10; other++) {
            documents.add(body("filler"));
        }
        var ranking = Ranking.of(documents);

        assertThat(ranking.rank("alpha beta")).isNotEmpty();
        assertThat(ranking.answer("alpha beta")).isEmpty();
    }

    /**
     * "this" points at the code in front of the person, it does not name the task: the question "this alpha" is the
     * question "alpha", of one word, even when a document holds "this" in its keywords.
     */
    @Test
    void aDemonstrativeOfTheQuestionIsNotAWordOfTheTask() {
        var documents = new ArrayList<Document>();
        documents.add(new Document("", "alpha this", ""));
        for (var other = 1; other < 10; other++) {
            documents.add(new Document("", "common", "filler" + other));
        }
        var ranking = Ranking.of(documents);

        assertThat(ranking.rank("those alpha")).isEqualTo(ranking.rank("alpha"));
        assertThat(ranking.rank("that alpha")).isEqualTo(ranking.rank("alpha"));
        assertThat(ranking.rank("these alpha")).isEqualTo(ranking.rank("alpha"));
        assertThat(ranking.answer("this alpha")).isEmpty();
        assertThat(ranking.answer("alpha this")).isEmpty();
    }

    @Test
    void aQuestionWithNoWordOfTheDocumentsFindsNothing() {
        var ranking = Ranking.of(List.of(body("apple banana"), body("cherry date")));

        assertThat(ranking.rank("bread")).isEmpty();
        assertThat(ranking.rank("")).isEmpty();
        assertThat(ranking.rank("a 1 -")).isEmpty();
    }

    @Test
    void noDocumentsFindsNothing() {
        assertThat(Ranking.of(List.of()).rank("apple")).isEmpty();
    }
}
