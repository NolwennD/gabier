package eu.gabier.cli.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/**
 * The sections of the guide, read from its text: a {@code ###} heading, the line {@code Words:}, the paragraph {@code
 * Ask:} or {@code By hand:}, and the rest. A section ends at the next heading, of any level; a line that starts with
 * {@code #} inside a fence is code (a shell comment), not a heading. The code between fences is left out: it holds
 * names of the example, not of the situation.
 */
final class GuideSections {

    private static final String SECTION = "### ";

    private static final String HEADING = "#";

    private static final String FENCE = "```";

    private static final String WORDS = "Words:";

    private static final List<String> HOW = List.of("Ask:", "By hand:");

    private GuideSections() {}

    /** The {@code ###} sections of this page, in order. */
    static List<GuideSection> parse(String page) {
        var sections = new ArrayList<GuideSection>();
        String title = null;
        var lines = new ArrayList<String>();
        var fenced = false;
        for (var line : page.lines().toList()) {
            if (line.startsWith(FENCE)) {
                fenced = !fenced;
            }
            if (!fenced && line.startsWith(HEADING) && title != null) {
                sections.add(section(title, lines));
                title = null;
                lines.clear();
            }
            if (!fenced && line.startsWith(SECTION)) {
                title = line.substring(SECTION.length()).strip();
            } else if (title != null) {
                lines.add(line);
            }
        }
        if (title != null) {
            sections.add(section(title, lines));
        }

        return List.copyOf(sections);
    }

    private static GuideSection section(String title, List<String> lines) {
        var words = "";
        var how = new StringJoiner(" ");
        var body = new ArrayList<String>();
        var fenced = false;
        var inHow = false;
        for (var line : lines) {
            if (line.startsWith(FENCE)) {
                fenced = !fenced;
            } else if (!fenced) {
                inHow = inHow && !line.isBlank();
                if (inHow) {
                    how.add(line.strip());
                } else if (line.startsWith(WORDS)) {
                    words = line.substring(WORDS.length()).strip();
                } else if (HOW.stream().anyMatch(line::startsWith)) {
                    how.add(line.strip());
                    inHow = true;
                } else {
                    body.add(line);
                }
            }
        }

        return new GuideSection(
                title, words, how.toString(), String.join("\n", body).strip());
    }
}
