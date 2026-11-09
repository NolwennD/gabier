package eu.gabier.cli.application;

import eu.gabier.protocol.TerminalText;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * The text of the answer of the guide to a task, as a person reads it: a function of the question and the sections that
 * answer it, so that every front end says the same thing. The question is shown on one line ({@link TerminalText}): it
 * comes from whoever asks, and may not forge an entry of the answer. What a front end adds to point to its own way of
 * reading the whole guide is its own.
 */
public final class GuideText {

    private GuideText() {}

    /**
     * The sections that answer the task, best first, each with its confidence and what to do; or the plain word that
     * nothing in the guide does.
     */
    public static String answer(String task, List<GuideMatch> found) {
        if (found.isEmpty()) {
            return "Nothing in the guide matches \"%s\".".formatted(TerminalText.neutralise(task));
        }
        var matches = found.stream().map(GuideText::entry).collect(Collectors.joining("\n\n"));

        return """
            For "%s":

            %s\
            """.formatted(TerminalText.neutralise(task), matches);
    }

    private static String entry(GuideMatch match) {
        return """
            %d. %s (confidence %s)
               %s\
            """.formatted(
                        match.rank(),
                        match.section().title(),
                        confidence(match),
                        match.section().how());
    }

    /** The confidence written with two decimals. */
    public static String confidence(GuideMatch match) {
        return String.format(Locale.ROOT, "%.2f", match.confidence());
    }
}
