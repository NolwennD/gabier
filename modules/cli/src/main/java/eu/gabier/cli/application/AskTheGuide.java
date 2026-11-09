package eu.gabier.cli.application;

import eu.gabier.cli.domain.Guide;
import eu.gabier.cli.domain.GuideSection;
import eu.gabier.cli.domain.ranking.Ranking;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Asks the guide a question: the sections that answer a task, best first, each with its confidence. A function of the
 * question and the guide alone, with no daemon and no network, so that every front end gives the same answer.
 */
public final class AskTheGuide {

    private AskTheGuide() {}

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
