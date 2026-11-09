package eu.gabier.cli.domain.ranking;

import eu.gabier.shared.error.domain.Assert;

/**
 * A document found for a question.
 *
 * @param index the position of the document in the list the ranking was made of
 * @param score the BM25 score of the document, more than 0
 * @param confidence the score as a share of the highest score a document could give the question, from 0 to 1
 */
public record Match(int index, double score, double confidence) {

    public Match {
        Assert.field("index", index).positive();
        Assert.field("score", score).strictlyPositive();
        Assert.field("confidence", confidence).positive().max(1);
    }
}
