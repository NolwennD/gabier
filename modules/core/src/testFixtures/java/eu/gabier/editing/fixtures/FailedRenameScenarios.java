package eu.gabier.editing.fixtures;

import eu.gabier.editing.domain.LanguageServerErrorKey;
import eu.gabier.editing.domain.Position;
import eu.gabier.editing.fixtures.Scenario.Answer;
import eu.gabier.editing.fixtures.Scenario.Request;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * The contract scenarios of the renames that jdtls (1.63.0) fails on, kept apart from {@link Scenarios}: they are part
 * of its catalogue ({@link Scenarios#all()}) and are replayed like the others.
 *
 * <p>jdtls fails with the response error {@code -32603}, "Internal error", whose data is the stack trace of a {@code
 * NullPointerException}, when it renames the component of a record that has a compact constructor and the one of a
 * record that has an explicit canonical constructor, and when it renames the name of a module (checked on the real jdtls): the fork of jdtls of {@code
 * RenameSupport} has no case for a record component, and takes the parameter of such a constructor for a local
 * variable. The adapter relays the failure as a refusal with the key {@code REQUEST_FAILED}, whatever the request; the
 * replay compares the key, not the code or the words of jdtls.
 *
 * <p>The rename of a component of a record that has neither has no scenario: where jdtls leaves a use by simple name in
 * the body, the effect of its answer does not compile, which the replay rejects (it compiles the result), and whether
 * it leaves one depends on the name of the component, not on the shape of the record.
 */
public final class FailedRenameScenarios {

    private static final Path MARK = Path.of("src/main/java/demo/Mark.java");
    private static final Path CANON = Path.of("src/main/java/demo/Canon.java");

    private static final Map<Path, String> PROJECT = Map.of(
            MARK, """
            package demo;

            public record Mark(String label, int rank) {
                public Mark {
                    if (rank < 0) {
                        throw new IllegalArgumentException(label);
                    }
                }
            }
            """,
            CANON, """
            package demo;

            public record Canon(String label, int rank) {
                public Canon(String label, int rank) {
                    this.label = label;
                    this.rank = rank;
                }
            }
            """);

    /** What jdtls answers when a handler of its throws: the code of the response error and its message. */
    private static final List<String> INTERNAL_ERROR = List.of("-32603", "Internal error.");

    /** {@code label} in the header of a record with a compact constructor: jdtls takes it for a local variable. */
    public static final Scenario RENAME_A_COMPONENT_OF_A_RECORD_WITH_A_COMPACT_CONSTRUCTOR = new Scenario(
            "rename-a-component-of-a-record-with-a-compact-constructor",
            PROJECT,
            new Request.Rename(MARK, new Position(2, 26), "caption"),
            new Answer.Refused(LanguageServerErrorKey.REQUEST_FAILED, INTERNAL_ERROR));

    /** {@code label} in the header of a record with an explicit canonical constructor: the same failure. */
    public static final Scenario RENAME_A_COMPONENT_OF_A_RECORD_WITH_AN_EXPLICIT_CANONICAL_CONSTRUCTOR = new Scenario(
            "rename-a-component-of-a-record-with-an-explicit-canonical-constructor",
            PROJECT,
            new Request.Rename(CANON, new Position(2, 27), "caption"),
            new Answer.Refused(LanguageServerErrorKey.REQUEST_FAILED, INTERNAL_ERROR));

    private static final Path MODULE_INFO = Path.of("src/main/java/module-info.java");

    /**
     * {@code demo.records} in {@code module demo.records {}}: jdtls fails on the name of a module (the same code and
     * message, with a {@code NullPointerException} whose element is null), since its rename has no case for it.
     */
    public static final Scenario RENAME_THE_NAME_OF_A_MODULE = new Scenario(
            "rename-the-name-of-a-module",
            Map.of(MODULE_INFO, """
                module demo.records {
                    exports demo;
                }
                """, MARK, """
                package demo;

                public record Mark(String label, int rank) {}
                """),
            new Request.Rename(MODULE_INFO, new Position(0, 7), "demo.renamed"),
            new Answer.Refused(LanguageServerErrorKey.REQUEST_FAILED, INTERNAL_ERROR));

    private FailedRenameScenarios() {}

    /** Every scenario of this class, in the order they are declared. */
    public static List<Scenario> all() {
        return List.of(
                RENAME_A_COMPONENT_OF_A_RECORD_WITH_A_COMPACT_CONSTRUCTOR,
                RENAME_A_COMPONENT_OF_A_RECORD_WITH_AN_EXPLICIT_CANONICAL_CONSTRUCTOR,
                RENAME_THE_NAME_OF_A_MODULE);
    }
}
