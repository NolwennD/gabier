package fr.craft.gabier.editing.fixtures;

import fr.craft.gabier.editing.domain.ChangeSet;
import fr.craft.gabier.editing.domain.Diagnostic;
import fr.craft.gabier.editing.domain.Diagnostic.Severity;
import fr.craft.gabier.editing.domain.FileChange;
import fr.craft.gabier.editing.domain.FileChange.EditFile;
import fr.craft.gabier.editing.domain.FileChange.RenameFile;
import fr.craft.gabier.editing.domain.LanguageServerErrorKey;
import fr.craft.gabier.editing.domain.Position;
import fr.craft.gabier.editing.domain.Range;
import fr.craft.gabier.editing.domain.TextEdit;
import fr.craft.gabier.editing.fixtures.Scenario.Answer;
import fr.craft.gabier.editing.fixtures.Scenario.Request;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The catalogue of the contract scenarios: every answer the core relies on from a language server for Java. The
 * project is two small classes of a package {@code demo} (and a third, broken, for the error): a {@code Greeter} with a
 * field, a method and a local variable, and a {@code Main} that uses it. All the positions are 0-based
 * ({@code line}, {@code column}) as in {@link Position}; every path is relative to the root of the project.
 *
 * <p>What nobody could check yet, because no real server has answered: the replay of these scenarios belongs to the
 * adapter of jdtls. Until then they are the expected behaviour as the driver and the protocol describe it, and these
 * points are the likeliest to differ:
 *
 * <ul>
 *   <li>the text of the message of the error ({@link #FILE_WITH_AN_ERROR});
 *   <li>the order of the changes and of the edits in a rename (here the file of the symbol first, edits before the move
 *       of a file that a rename of a public type causes), which a replay may compare as sets;
 *   <li>the refusal while the project is imported ({@link #DIAGNOSTICS_WHILE_THE_SERVER_IMPORTS}), which no replay can
 *       provoke on demand without a race: the adapter's own test covers how it is detected.
 * </ul>
 *
 * <p>To add a scenario: write it here, add its name to {@link #all()} and to the test of the catalogue, and add its
 * replay to the integration tests of the adapter.
 */
public final class Scenarios {

    private static final Path GREETER = Path.of("src/main/java/demo/Greeter.java");
    private static final Path WELCOMER = Path.of("src/main/java/demo/Welcomer.java");
    private static final Path MAIN = Path.of("src/main/java/demo/Main.java");
    private static final Path BROKEN = Path.of("src/main/java/demo/Broken.java");
    private static final Path ELSEWHERE = Path.of("../outside/Elsewhere.java");

    private static final String GREETER_SOURCE = """
        package demo;

        public class Greeter {
            private String greeting = "Hello";

            public String greet(String name) {
                String message = greeting + ", " + name;
                return message;
            }
        }
        """;

    private static final String MAIN_SOURCE = """
        package demo;

        public class Main {
            public static void main(String[] args) {
                Greeter greeter = new Greeter();
                System.out.println(greeter.greet("world"));
            }
        }
        """;

    private static final String BROKEN_SOURCE = """
        package demo;

        public class Broken {
            public int answer() {
                return "forty-two";
            }
        }
        """;

    private static final Map<Path, String> PROJECT = Map.of(GREETER, GREETER_SOURCE, MAIN, MAIN_SOURCE);

    /** A file without a problem: no diagnostic. */
    public static final Scenario CLEAN_FILE = new Scenario(
            "diagnostics-of-a-clean-file", PROJECT, new Request.Diagnostics(MAIN), new Answer.Diagnosed(List.of()));

    /** A string where an {@code int} is expected: one error, at the string. */
    public static final Scenario FILE_WITH_AN_ERROR = new Scenario(
            "diagnostics-of-a-file-with-an-error",
            withBroken(),
            new Request.Diagnostics(BROKEN),
            new Answer.Diagnosed(List.of(new Diagnostic(
                    Severity.ERROR, new Position(4, 15), "Type mismatch: cannot convert from String to int"))));

    /** The project is still being imported: the server cannot say. */
    public static final Scenario DIAGNOSTICS_WHILE_THE_SERVER_IMPORTS = new Scenario(
            "diagnostics-while-the-server-imports",
            PROJECT,
            new Request.Diagnostics(MAIN),
            new Answer.Refused(LanguageServerErrorKey.NOT_READY));

    /** A file that is not in the project the server was started on. */
    public static final Scenario DIAGNOSTICS_OUTSIDE_THE_PROJECT = new Scenario(
            "diagnostics-of-a-file-outside-the-project",
            PROJECT,
            new Request.Diagnostics(ELSEWHERE),
            new Answer.Refused(LanguageServerErrorKey.FILE_OUTSIDE_PROJECT));

    /** {@code message}, declared and used in the same method: two edits in one file. */
    public static final Scenario RENAME_LOCAL_VARIABLE = new Scenario(
            "rename-a-local-variable",
            PROJECT,
            new Request.Rename(GREETER, new Position(6, 15), "text"),
            new Answer.Renamed(
                    new ChangeSet(List.of(edits(GREETER, replace(6, 15, 22, "text"), replace(7, 15, 22, "text"))))));

    /** {@code greeting}, the field of the class: its declaration and its use. */
    public static final Scenario RENAME_FIELD = new Scenario(
            "rename-a-field",
            PROJECT,
            new Request.Rename(GREETER, new Position(3, 19), "salutation"),
            new Answer.Renamed(new ChangeSet(
                    List.of(edits(GREETER, replace(3, 19, 27, "salutation"), replace(6, 25, 33, "salutation"))))));

    /** {@code greet}, called from another class: edits in two files. */
    public static final Scenario RENAME_METHOD = new Scenario(
            "rename-a-method",
            PROJECT,
            new Request.Rename(GREETER, new Position(5, 18), "welcome"),
            new Answer.Renamed(new ChangeSet(List.of(
                    edits(GREETER, replace(5, 18, 23, "welcome")), edits(MAIN, replace(5, 35, 40, "welcome"))))));

    /**
     * {@code Greeter}, a public type: its uses are edited, then its file is moved to the name of the new type, as an
     * IDE does (the edits name the old path, as the set is applied in order).
     */
    public static final Scenario RENAME_TYPE = new Scenario(
            "rename-a-type",
            PROJECT,
            new Request.Rename(GREETER, new Position(2, 13), "Welcomer"),
            new Answer.Renamed(new ChangeSet(List.of(
                    edits(GREETER, replace(2, 13, 20, "Welcomer")),
                    edits(MAIN, replace(4, 8, 15, "Welcomer"), replace(4, 30, 37, "Welcomer")),
                    new RenameFile(GREETER, WELCOMER)))));

    /** An empty line: there is nothing to rename. */
    public static final Scenario RENAME_WHERE_THERE_IS_NO_SYMBOL = new Scenario(
            "rename-where-there-is-no-symbol",
            PROJECT,
            new Request.Rename(GREETER, new Position(4, 0), "anything"),
            new Answer.Refused(LanguageServerErrorKey.NO_SUCH_SYMBOL));

    /** A name that begins with a digit is not an identifier. */
    public static final Scenario RENAME_TO_AN_INVALID_NAME = new Scenario(
            "rename-to-an-invalid-name",
            PROJECT,
            new Request.Rename(GREETER, new Position(6, 15), "1st"),
            new Answer.Refused(LanguageServerErrorKey.INVALID_NAME));

    /** The project is still being imported: the server cannot say what a rename changes. */
    public static final Scenario RENAME_WHILE_THE_SERVER_IMPORTS = new Scenario(
            "rename-while-the-server-imports",
            PROJECT,
            new Request.Rename(GREETER, new Position(6, 15), "text"),
            new Answer.Refused(LanguageServerErrorKey.NOT_READY));

    /** A file that is not in the project the server was started on. */
    public static final Scenario RENAME_OUTSIDE_THE_PROJECT = new Scenario(
            "rename-a-file-outside-the-project",
            PROJECT,
            new Request.Rename(ELSEWHERE, new Position(0, 0), "anything"),
            new Answer.Refused(LanguageServerErrorKey.FILE_OUTSIDE_PROJECT));

    private Scenarios() {}

    /** Every scenario of the catalogue. */
    public static List<Scenario> all() {
        return List.of(
                CLEAN_FILE,
                FILE_WITH_AN_ERROR,
                DIAGNOSTICS_WHILE_THE_SERVER_IMPORTS,
                DIAGNOSTICS_OUTSIDE_THE_PROJECT,
                RENAME_LOCAL_VARIABLE,
                RENAME_FIELD,
                RENAME_METHOD,
                RENAME_TYPE,
                RENAME_WHERE_THERE_IS_NO_SYMBOL,
                RENAME_TO_AN_INVALID_NAME,
                RENAME_WHILE_THE_SERVER_IMPORTS,
                RENAME_OUTSIDE_THE_PROJECT);
    }

    private static Map<Path, String> withBroken() {
        Map<Path, String> files = new HashMap<>(PROJECT);
        files.put(BROKEN, BROKEN_SOURCE);

        return files;
    }

    private static FileChange edits(Path file, TextEdit... edits) {
        return new EditFile(file, List.of(edits));
    }

    /** Replaces the text of one line, from a column to another. */
    private static TextEdit replace(int line, int from, int to, String text) {
        return new TextEdit(new Range(new Position(line, from), new Position(line, to)), text);
    }
}
