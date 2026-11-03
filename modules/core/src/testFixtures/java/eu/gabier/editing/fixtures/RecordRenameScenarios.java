package eu.gabier.editing.fixtures;

import static eu.gabier.editing.fixtures.Scenarios.edits;
import static eu.gabier.editing.fixtures.Scenarios.replace;

import eu.gabier.editing.domain.ChangeSet;
import eu.gabier.editing.domain.LanguageServerErrorKey;
import eu.gabier.editing.domain.Position;
import eu.gabier.editing.fixtures.Scenario.Answer;
import eu.gabier.editing.fixtures.Scenario.Request;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * The contract scenarios of the rename of a component of a record, kept apart from {@link Scenarios} for the project
 * they share: they are part of its catalogue ({@link Scenarios#all()}) and are replayed like the others.
 *
 * <p>The project has two records, {@code Spot(String file, int line)} with a method that reads {@code file} by its
 * simple name, and {@code Mark(String label, int rank)} with a compact constructor, and {@code Board}, which calls the
 * accessors. What jdtls does when it is asked to rename a component, from the header of the record or from a use of it
 * (checked on the real jdtls):
 *
 * <ul>
 *   <li>a record with a compact constructor: {@code textDocument/rename} fails with the response error {@code -32603}
 *       ("Internal error", a {@code NullPointerException} in the rename of a local variable, which jdtls takes the
 *       component for). The adapter used to answer {@code internal-server-error};
 *   <li>a record with no compact constructor: jdtls answers an edit that renames the component, its accessor calls and
 *       {@code this.file}, but not {@code file} used by its simple name in the body of the record, so the file no
 *       longer compiles;
 *   <li>a position on an accessor call ({@code spot.file()}) or on the parameter of the compact constructor: {@code
 *       textDocument/prepareRename} answers "Renaming this element is not supported".
 * </ul>
 *
 * <p>jdtls cannot be trusted with a component either way, and the adapter cannot tell from outside when it is right
 * (a component read only by its accessor is renamed correctly), so every component is refused with {@code
 * RECORD_COMPONENT}, whatever the position that leads to it: {@code textDocument/definition} gives the same
 * declaration from all of them, and a semantic token of the type {@code recordComponent} tells that the declaration is
 * a component. Nothing else of a record is concerned ({@link #RENAME_A_METHOD_OF_A_RECORD}).
 */
public final class RecordRenameScenarios {

    private static final Path SPOT = Path.of("src/main/java/demo/Spot.java");
    private static final Path MARK = Path.of("src/main/java/demo/Mark.java");
    private static final Path BOARD = Path.of("src/main/java/demo/Board.java");

    private static final Map<Path, String> PROJECT = Map.of(
            SPOT, """
            package demo;

            public record Spot(String file, int line) {
                public String describe() {
                    return file + ":" + line;
                }
            }
            """,
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
            BOARD, """
            package demo;

            public class Board {
                String where(Spot spot, Mark mark) {
                    return spot.file() + mark.label() + spot.describe();
                }
            }
            """);

    /** {@code label} in the header of a record that has a compact constructor: jdtls fails. */
    public static final Scenario RENAME_A_COMPONENT_OF_A_RECORD_WITH_A_COMPACT_CONSTRUCTOR = new Scenario(
            "rename-a-component-of-a-record-with-a-compact-constructor",
            PROJECT,
            new Request.Rename(MARK, new Position(2, 26), "title"),
            new Answer.Refused(LanguageServerErrorKey.RECORD_COMPONENT));

    /** {@code file} in the header of a record that has none: jdtls leaves the use in the body unrenamed. */
    public static final Scenario RENAME_A_COMPONENT_OF_A_RECORD_WITHOUT_A_COMPACT_CONSTRUCTOR = new Scenario(
            "rename-a-component-of-a-record-without-a-compact-constructor",
            PROJECT,
            new Request.Rename(SPOT, new Position(2, 26), "path"),
            new Answer.Refused(LanguageServerErrorKey.RECORD_COMPONENT));

    /** {@code file} read by its simple name in the body of the record: it is the component all the same. */
    public static final Scenario RENAME_A_COMPONENT_FROM_ITS_USE_IN_THE_RECORD = new Scenario(
            "rename-a-component-from-its-use-in-the-record",
            PROJECT,
            new Request.Rename(SPOT, new Position(4, 15), "path"),
            new Answer.Refused(LanguageServerErrorKey.RECORD_COMPONENT));

    /** {@code label} called as an accessor from another class, where jdtls declines to prepare the rename. */
    public static final Scenario RENAME_A_COMPONENT_FROM_A_CALL_OF_ITS_ACCESSOR = new Scenario(
            "rename-a-component-from-a-call-of-its-accessor",
            PROJECT,
            new Request.Rename(BOARD, new Position(4, 34), "title"),
            new Answer.Refused(LanguageServerErrorKey.RECORD_COMPONENT));

    /** {@code describe}, a method of a record, is renamed like the method of any class: the record is no obstacle. */
    public static final Scenario RENAME_A_METHOD_OF_A_RECORD = new Scenario(
            "rename-a-method-of-a-record",
            PROJECT,
            new Request.Rename(SPOT, new Position(3, 18), "summary"),
            new Answer.Renamed(new ChangeSet(List.of(
                    edits(SPOT, replace(3, 18, 3, 26, "summary")), edits(BOARD, replace(4, 49, 4, 57, "summary"))))));

    private RecordRenameScenarios() {}

    /** Every scenario of this class, in the order they are declared. */
    public static List<Scenario> all() {
        return List.of(
                RENAME_A_COMPONENT_OF_A_RECORD_WITH_A_COMPACT_CONSTRUCTOR,
                RENAME_A_COMPONENT_OF_A_RECORD_WITHOUT_A_COMPACT_CONSTRUCTOR,
                RENAME_A_COMPONENT_FROM_ITS_USE_IN_THE_RECORD,
                RENAME_A_COMPONENT_FROM_A_CALL_OF_ITS_ACCESSOR,
                RENAME_A_METHOD_OF_A_RECORD);
    }
}
