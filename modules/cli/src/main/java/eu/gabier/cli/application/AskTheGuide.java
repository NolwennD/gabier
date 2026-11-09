package eu.gabier.cli.application;

import eu.gabier.cli.application.InvalidValuesException.OperationName;
import eu.gabier.cli.domain.Guide;
import eu.gabier.cli.domain.GuideSection;
import eu.gabier.cli.domain.ranking.Ranking;
import eu.gabier.shared.error.domain.GeneratorException;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.IntStream;

/**
 * Asks the guide a question: the sections that answer a task, best first, each with its confidence. A function of the
 * question and the guide alone, with no daemon and no network, so that every front end gives the same answer.
 */
public final class AskTheGuide {

    private static final OperationName QUESTION = new OperationName("guide");
    private static final String TASK = "task";

    private AskTheGuide() {}

    /**
     * The task of a question given as named values, as an operation takes its values: the one value {@code task}, a
     * text that is not blank, without the spaces around it.
     *
     * @throws GeneratorException (the key {@code operation-invalid-values}, as for an operation) when {@code task} is
     *     missing, is not a text or is blank, or another value is given; a value that is unknown is said before one
     *     that is missing
     */
    public static String taskIn(Map<String, Object> values) {
        for (var name : new TreeSet<>(values.keySet())) {
            if (!name.equals(TASK)) {
                throw InvalidValuesException.unknown(QUESTION, name);
            }
        }
        var task = values.get(TASK);
        if (task == null) {
            throw InvalidValuesException.missing(QUESTION, TASK);
        }
        if (!(task instanceof String text)) {
            throw InvalidValuesException.invalid(QUESTION, TASK, "be a text");
        }
        if (text.isBlank()) {
            throw InvalidValuesException.invalid(QUESTION, TASK, "not be blank");
        }

        return text.strip();
    }

    /**
     * The sections of the guide that answer the task, best first; none when nothing in the guide does.
     *
     * @param task the question, in English
     * @param guide the guide to ask
     */
    public static List<GuideMatch> answer(String task, Guide guide) {
        var sections = guide.sections();
        var matches = Ranking.of(sections.stream().map(GuideSection::document).toList())
                .answer(task);

        return IntStream.range(0, matches.size())
                .mapToObj(rank -> new GuideMatch(
                        rank + 1,
                        sections.get(matches.get(rank).index()),
                        matches.get(rank).confidence()))
                .toList();
    }
}
