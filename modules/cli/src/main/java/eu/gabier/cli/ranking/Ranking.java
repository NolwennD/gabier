package eu.gabier.cli.ranking;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The documents that answer a question, best first: BM25 over the words of the documents ({@link Words}), with the
 * parameters {@code k1 = 1.5} and {@code b = 0.75} and the idf of Lucene, {@code ln(1 + (N - n + 0.5) / (n + 0.5))},
 * for N documents of which n hold the word.
 *
 * <p>A document is one text for the score: its title and its keywords are counted several times (see {@link
 * #TITLE_WEIGHT} and {@link #KEYWORDS_WEIGHT}), in the frequency of a word and in the length of the document, then its
 * body once. The question is a set of words: a word asked twice counts once.
 *
 * <p>The score ranks, it does not say that a document answers: a document answers when it also holds {@value
 * #MINIMUM_TOPIC_WORDS} distinctive words of the question in its title or its keywords. A word every other document
 * holds ("the", "and") proves nothing, and neither does a word of the body only, or a lone word: a question of one word
 * has no answer.
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
     * questions of {@code questionsWithNoAnswer} (the table of questions, {@code GuideQuestionsTest}) that hold two
     * distinctive words of a section, and so are not cut by {@link #MINIMUM_TOPIC_WORDS} (at most 0.242: "document the
     * new class in the wiki"; 0.236: "bake a new class of bread"), and the confidence of the best section for the real
     * questions of the table (at least 0.352: "remove imports I don't use"). The other questions of that list are cut
     * by the minimum, whatever their confidence ("run the build and the tests" is at 0.451, "the" at 0.914).
     */
    static final double THRESHOLD = 0.3;

    /**
     * A document is part of the answer when its confidence is at least this share of the best one's. 0.6 keeps the
     * second section of "turn this string concatenation into a text block" (0.77 of the first).
     */
    static final double NEAR_THE_BEST = 0.6;

    /**
     * The idf from which a word tells documents apart: {@code ln(1 + (N - n + 0.5) / (n + 0.5)) >= 1}, that is a word
     * that about a third of the documents hold at most (n = 15 of the 43 sections of the guide, n = 3 of 10). A word of
     * a question that more documents hold ("the", "and", "run") says nothing about the task.
     */
    static final double DISTINCTIVE_IDF = 1.0;

    /**
     * How many distinctive words of the question the title and the keywords of a document must hold for it to answer.
     * A document found by one word alone, however distinctive, or by words of its body, is not an answer: a question
     * of one word has none, whatever the word ("block" held by nine sections of 43, "the" by forty).
     */
    static final int MINIMUM_TOPIC_WORDS = 2;

    private static final double K1 = 1.5;

    private static final double B = 0.75;

    private final List<Map<String, Integer>> frequencies;

    private final List<Integer> lengths;

    private final List<Set<String>> topics;

    private final Map<String, Integer> documentsWithTheWord;

    private final double averageLength;

    private Ranking(
            List<Map<String, Integer>> frequencies,
            List<Integer> lengths,
            List<Set<String>> topics,
            Map<String, Integer> documentsWithTheWord) {
        this.frequencies = frequencies;
        this.lengths = lengths;
        this.topics = topics;
        this.documentsWithTheWord = documentsWithTheWord;
        averageLength = lengths.stream().mapToInt(Integer::intValue).average().orElse(0);
    }

    /** The ranking of these documents. */
    public static Ranking of(List<Document> documents) {
        var frequencies = new ArrayList<Map<String, Integer>>();
        var lengths = new ArrayList<Integer>();
        var topics = new ArrayList<Set<String>>();
        var documentsWithTheWord = new HashMap<String, Integer>();
        for (var document : documents) {
            var counts = new HashMap<String, Integer>();
            count(counts, document.title(), TITLE_WEIGHT);
            count(counts, document.keywords(), KEYWORDS_WEIGHT);
            count(counts, document.body(), 1);
            frequencies.add(counts);
            lengths.add(counts.values().stream().mapToInt(Integer::intValue).sum());
            var topic = new HashSet<>(Words.split(document.title()));
            topic.addAll(Words.split(document.keywords()));
            topics.add(Set.copyOf(topic));
            counts.keySet().forEach(word -> documentsWithTheWord.merge(word, 1, Integer::sum));
        }

        return new Ranking(
                List.copyOf(frequencies), List.copyOf(lengths), List.copyOf(topics), Map.copyOf(documentsWithTheWord));
    }

    private static void count(Map<String, Integer> counts, String text, int weight) {
        Words.split(text).forEach(word -> counts.merge(word, weight, Integer::sum));
    }

    /**
     * Every document that holds a word of the question, best first, equal scores in the order of the documents. Nothing
     * for a question with no word that any document holds.
     */
    public List<Match> rank(String question) {
        var words = wordsOf(question);
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
     * at least {@value #NEAR_THE_BEST} of its, all with a confidence of at least {@value #THRESHOLD}, among the
     * documents that hold at least {@value #MINIMUM_TOPIC_WORDS} distinctive words of the question ({@link
     * #DISTINCTIVE_IDF}) in their title or their keywords. Empty when none does, or when the best one is under the
     * threshold: nothing matches.
     */
    public List<Match> answer(String question) {
        var words = wordsOf(question);
        var matches = rank(question).stream()
                .filter(match -> topicWordsHeldBy(match.index(), words) >= MINIMUM_TOPIC_WORDS)
                .toList();
        if (matches.isEmpty()) {
            return List.of();
        }
        var floor = Math.max(THRESHOLD, matches.getFirst().confidence() * NEAR_THE_BEST);

        return matches.stream()
                .filter(match -> match.confidence() >= floor)
                .limit(MOST_ANSWERS)
                .toList();
    }

    private static Set<String> wordsOf(String question) {
        return new LinkedHashSet<>(Words.split(question));
    }

    private long topicWordsHeldBy(int index, Collection<String> words) {
        return words.stream()
                .filter(topics.get(index)::contains)
                .filter(word -> idf(word) >= DISTINCTIVE_IDF)
                .count();
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
