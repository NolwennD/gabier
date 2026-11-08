package eu.gabier.cli;

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

    private AgentInstructions() {}

    /** The text to paste, as written. */
    static String text() {
        return TextResource.read(RESOURCE, AgentInstructions.class.getResourceAsStream(RESOURCE));
    }
}
