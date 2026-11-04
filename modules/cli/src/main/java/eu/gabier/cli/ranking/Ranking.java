package eu.gabier.cli.ranking;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * The documents that answer a question, best first: BM25 over the words of the documents ({@link Words}), with the
 * parameters {@code k1 = 1.5} and {@code b = 0.75} and the idf of Lucene, {@code ln(1 + (N - n + 0.5) / (n + 0.5))},
 * for N documents of which n hold the word.
 *
 * <p>A document is one text for the score: its title and its keywords are counted several times (see {@link
 * #TITLE_WEIGHT} and {@link #KEYWORDS_WEIGHT}), in the frequency of a word and in the length of the document, then its
 * body once. The question is a set of words: a word asked twice counts once.
 */
public final class Ranking {

    /** How many times a word of the title counts. */
    static final int TITLE_WEIGHT = 2;

    /**
     * How many times a word of the keywords counts: the line is written to say what a person says about the problem,
     * so it weighs more than the title, which names the situation.
     */
    static final int KEYWORDS_WEIGHT = 3;

    /** The most documents {@link #answer(String)} gives. */
    static final int MOST_ANSWERS = 3;

    /**
     * The confidence under which {@link #answer(String)} says that nothing matches. Between the confidences of the
     * unrelated questions of the table of questions (at most 0.244: "write a unit test for this class") and those of
     * the best section for the real ones (at least 0.428).
     */
    static final double THRESHOLD = 0.3;

    /**
     * A document is part of the answer when its confidence is at least this share of the best one's. 0.6 keeps the
     * second section of "turn this string concatenation into a text block" (0.63 of the first) and leaves out the
     * third of "invert this if" (0.58 of the first), which shares only a word with it.
     */
    static final double NEAR_THE_BEST = 0.6;

    private static final double K1 = 1.5;

    private static final double B = 0.75;

    private final List<Map<String, Integer>> frequencies;

    private final List<Integer> lengths;

    private final Map<String, Integer> documentsWithTheWord;

    private final double averageLength;

    private Ranking(
            List<Map<String, Integer>> frequencies, List<Integer> lengths, Map<String, Integer> documentsWithTheWord) {
        this.frequencies = frequencies;
        this.lengths = lengths;
        this.documentsWithTheWord = documentsWithTheWord;
        averageLength = lengths.stream().mapToInt(Integer::intValue).average().orElse(0);
    }

    /** The ranking of these documents. */
    public static Ranking of(List<Document> documents) {
        var frequencies = new ArrayList<Map<String, Integer>>();
        var lengths = new ArrayList<Integer>();
        var documentsWithTheWord = new HashMap<String, Integer>();
        for (var document : documents) {
            var counts = new HashMap<String, Integer>();
            count(counts, document.title(), TITLE_WEIGHT);
            count(counts, document.keywords(), KEYWORDS_WEIGHT);
            count(counts, document.body(), 1);
            frequencies.add(counts);
            lengths.add(counts.values().stream().mapToInt(Integer::intValue).sum());
            counts.keySet().forEach(word -> documentsWithTheWord.merge(word, 1, Integer::sum));
        }

        return new Ranking(List.copyOf(frequencies), List.copyOf(lengths), Map.copyOf(documentsWithTheWord));
    }

    private static void count(Map<String, Integer> counts, String text, int weight) {
        Words.split(text).forEach(word -> counts.merge(word, weight, Integer::sum));
    }

    /**
     * Every document that holds a word of the question, best first, equal scores in the order of the documents. Nothing
     * for a question with no word that any document holds.
     */
    public List<Match> rank(String question) {
        var words = new LinkedHashSet<>(Words.split(question));
        var best = (K1 + 1) * words.stream().mapToDouble(this::idf).sum();
        var matches = new ArrayList<Match>();
        for (var index = 0; index < frequencies.size(); index++) {
            var score = score(index, words);
            if (score > 0) {
                matches.add(new Match(index, score, score / best));
            }
        }
        matches.sort(Comparator.comparingDouble(Match::score).reversed());

        return List.copyOf(matches);
    }

    /**
     * The documents that answer the question: at most {@value #MOST_ANSWERS}, the best one and those whose confidence is
     * at least {@value #NEAR_THE_BEST} of its, all with a confidence of at least {@value #THRESHOLD}. Empty when the
     * best one is under it: nothing matches.
     */
    public List<Match> answer(String question) {
        var matches = rank(question);
        if (matches.isEmpty()) {
            return List.of();
        }
        var floor = Math.max(THRESHOLD, matches.getFirst().confidence() * NEAR_THE_BEST);

        return matches.stream()
                .filter(match -> match.confidence() >= floor)
                .limit(MOST_ANSWERS)
                .toList();
    }

    private double score(int index, Iterable<String> words) {
        var score = 0.0;
        var lengthNorm = 1 - B + B * lengths.get(index) / averageLength;
        for (var word : words) {
            var frequency = frequencies.get(index).getOrDefault(word, 0);
            if (frequency > 0) {
                score += idf(word) * frequency * (K1 + 1) / (frequency + K1 * lengthNorm);
            }
        }

        return score;
    }

    private double idf(String word) {
        var holding = documentsWithTheWord.getOrDefault(word, 0);

        return Math.log(1 + (frequencies.size() - holding + 0.5) / (holding + 0.5));
    }
}
