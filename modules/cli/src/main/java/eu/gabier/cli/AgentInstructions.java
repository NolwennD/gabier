package eu.gabier.cli;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The instructions to give a coding agent, {@code gabier help agents}: a short Markdown section, the resource {@code
 * agents.md} of the cli (the native image keeps it, see {@code resource-config.json}), to paste as it is in {@code
 * AGENTS.md} or {@code CLAUDE.md}. This class is the one place that holds it, so that a command that writes it into a
 * project calls {@link #text()}.
 */
final class AgentInstructions {

    /** The word after {@code help} that asks for the instructions. */
    static final String TOPIC = "agents";

    /** What the instructions are for, in one line. */
    static final String SUMMARY = "the lines to paste in the instructions of a coding agent (AGENTS.md, CLAUDE.md)";

    private static final String RESOURCE = "agents.md";

    /** A command cited as {@code `gabier <command>}: the first word after {@code gabier}. */
    private static final Pattern CITED = Pattern.compile("`gabier ([a-z][a-z-]*)");

    private AgentInstructions() {}

    /** The text to paste, as written. */
    static String text() {
        return TextResource.read(RESOURCE, AgentInstructions.class.getResourceAsStream(RESOURCE));
    }

    /**
     * The text cut to the commands a project chooses to teach: a bullet (its line and the lines that continue it) that
     * cites commands is kept when at least one of them is among {@code only}; a bullet that cites none, and every line
     * that is no bullet (the heading, the lead), stay.
     */
    static String text(Collection<String> only) {
        var kept = new ArrayList<String>();
        var bullet = new ArrayList<String>();
        for (var line : text().stripTrailing().lines().toList()) {
            var continues = !bullet.isEmpty() && line.startsWith("  ");
            if (continues) {
                bullet.add(line);

                continue;
            }
            keepIfChosen(bullet, only, kept);
            bullet.clear();
            if (line.startsWith("- ")) {
                bullet.add(line);
            } else {
                kept.add(line);
            }
        }
        keepIfChosen(bullet, only, kept);

        return String.join("\n", kept) + "\n";
    }

    private static void keepIfChosen(List<String> bullet, Collection<String> only, List<String> kept) {
        var cited = CITED.matcher(String.join("\n", bullet))
                .results()
                .map(match -> match.group(1))
                .toList();
        if (cited.isEmpty() || cited.stream().anyMatch(only::contains)) {
            kept.addAll(bullet);
        }
    }
}
