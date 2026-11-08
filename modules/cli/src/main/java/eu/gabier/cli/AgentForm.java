package eu.gabier.cli;

import eu.gabier.protocol.ProtocolVersion;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * The form in which a project teaches its agents gabier, and where it lives in the project: the instructions of {@link
 * AgentInstructions} written as a skill, as a rule, or as a block of {@code AGENTS.md}. The block of gabier is marked
 * ({@link #START}, {@link #END}) in the three, so that a second run finds it and a file gabier did not write is known.
 */
enum AgentForm {
    SKILL(".claude/skills/gabier/SKILL.md", "# ", """
        ---
        name: gabier
        description: Use when changing Java code (renaming, extracting, moving, inlining, creating a type, fixing \
        imports or errors) in a project that has gabier. It does the change through a Java language server instead of \
        a text edit.
        ---

        """, false),
    RULE(".claude/rules/gabier.md", "# ", "", false),
    SNIPPET("AGENTS.md", "## ", "", true);

    /** The line that opens the block of gabier. */
    static final String START = "<!-- gabier:start -->";

    /** The line that closes the block of gabier. */
    static final String END = "<!-- gabier:end -->";

    private final String file;
    private final String heading;
    private final String frontmatter;
    private final boolean appendsToAFile;

    AgentForm(String file, String heading, String frontmatter, boolean appendsToAFile) {
        this.file = file;
        this.heading = heading;
        this.frontmatter = frontmatter;
        this.appendsToAFile = appendsToAFile;
    }

    /** The form named on the command line, as it is typed. */
    static Optional<AgentForm> named(String word) {
        return Arrays.stream(values()).filter(form -> form.word().equals(word)).findFirst();
    }

    /** The word that names the form on the command line and in the answer. */
    String word() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The file of the form, relative to the project folder. */
    String file() {
        return file;
    }

    /** Whether a file that holds no block of gabier gets it appended (a shared file) rather than refused. */
    boolean appendsToAFile() {
        return appendsToAFile;
    }

    /** The block of gabier: the markers around the version that wrote it and the instructions under their heading. */
    String block(String instructions) {
        return START + "\n<!-- gabier " + ProtocolVersion.CURRENT + " wrote this block (gabier init-agent " + word()
                + "); run it again after an upgrade -->\n"
                + instructions.stripTrailing().replaceFirst("^## ", heading) + "\n" + END;
    }

    /** The frontmatter the form needs and the text of the file lacks: none when the text opens with one. */
    String missingFrontmatter(String text) {
        var opens = text.lines()
                .findFirst()
                .filter(first -> first.strip().equals("---"))
                .isPresent();

        return opens ? "" : frontmatter;
    }

    /** The whole text of a new file of this form: its frontmatter, if it has one, and the block. */
    String newFile(String block) {
        return frontmatter + block + "\n";
    }
}
