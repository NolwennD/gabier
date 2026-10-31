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
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * The catalogue of the contract scenarios: every answer the core relies on from a language server for Java. The
 * project is two small classes of a package {@code demo}: a {@code Greeter} with a field, a method and a local
 * variable, and a {@code Main} that uses it; and, for the problems to report, a {@code Broken} (an error) and a {@code
 * Sloppy} (a warning). All the positions are 0-based ({@code line}, {@code column}) as in {@link Position}; every path
 * is relative to the root of the project.
 *
 * <p>The scenarios are replayed against a real jdtls by {@code ScenarioReplayTest} (module {@code daemon}, {@code
 * ./gradlew jdtlsProbe}), which fails on any difference. How the adapter maps what jdtls really does:
 *
 * <ul>
 *   <li>a file outside the project: jdtls does not refuse it, it opens it as a non-project file and warns that only
 *       syntax errors are reported. The adapter refuses it ({@link #DIAGNOSTICS_OUTSIDE_THE_PROJECT}, {@link
 *       #RENAME_OUTSIDE_THE_PROJECT}) because it is not under a folder jdtls imported, before asking;
 *   <li>a path under the project with no file ({@link #DIAGNOSTICS_OF_A_MISSING_FILE}) and a folder ({@link
 *       #DIAGNOSTICS_OF_A_FOLDER}): the adapter refuses them before asking, as it does for a file outside the project,
 *       with a reason of their own (the file is missing, the path is not a file);
 *   <li>the refusal while the project is imported ({@link #DIAGNOSTICS_WHILE_THE_SERVER_IMPORTS}, {@link
 *       #RENAME_WHILE_THE_SERVER_IMPORTS}) is the answer of the adapter to a request made while jdtls is still
 *       starting; the replay asks at once, before the import is over, which takes seconds;
 *   <li>a position with no symbol ({@link #RENAME_WHERE_THERE_IS_NO_SYMBOL}): jdtls answers {@code prepareRename} with
 *       a response error of the code {@code InvalidRequest} ("Renaming this element is not supported."), and an empty
 *       edit to the rename itself. The adapter asks {@code prepareRename} first and maps that error;
 *   <li>an invalid name ({@link #RENAME_TO_AN_INVALID_NAME}): jdtls accepts the position and answers the rename with a
 *       response error of the code {@code InvalidRequest} ("'1st' is not a valid Java identifier"), which the adapter
 *       maps. The same error comes for any condition of the refactoring that is fatal, so it means "this name is not
 *       accepted";
 *   <li>the edits of a rename: jdtls merges the edits of a file into one that spans from the first occurrence to the
 *       last and repeats the text between them, so the scenarios hold that edit, copied from what jdtls answered, and
 *       not one small edit per occurrence. The replay compares the answer to the scenario exactly, and the project
 *       with jdtls' answer applied to a copy must compile. The order of the changes counts, as it does in the
 *       application: the edits of a renamed type come first, the move of its file last, under its old path.
 *   <li>the extraction of a variable: jdtls answers {@code java/getRefactorEdit} with no edit and a message for a
 *       statement and for a call of a {@code void} method ({@link #EXTRACT_VARIABLE_FROM_A_STATEMENT}, {@link
 *       #EXTRACT_VARIABLE_FROM_A_VOID_CALL}), which the adapter maps to {@code not-an-expression}. The name it proposes
 *       ({@code string}) is kept without a rename ({@link #EXTRACT_VARIABLE_TO_THE_NAME_JDTLS_PROPOSES}). A name that the
 *       method already uses ({@link #EXTRACT_VARIABLE_TO_A_NAME_IN_USE}) is not declined by jdtls: its rename answers an
 *       edit that corrupts the declaration ({@code name= ...}), which the adapter detects and refuses as {@code
 *       invalid-name};
 *   <li>files changed on disk between two requests: jdtls reads a file once, and learns of what changes only if the
 *       client tells it ({@code workspace/didChangeWatchedFiles}, which it asks for by registering its watchers). A
 *       {@link Sequel} is two scenarios in a row with the disk changed between them ({@link #sequels()}): a type
 *       renamed, the change set applied (the file moved), then renamed back; and a file edited between two requests
 *       for its diagnostics. The replay changes the disk as an agent does and gets the answer for the new text.
 *   <li>the organization of the imports of a file ({@link #ORGANIZE_IMPORTS}): jdtls answers one edit of the file, from
 *       its first import to its last, or none when the imports are already as it is configured to write them ({@link
 *       #ORGANIZE_IMPORTS_IN_ORDER}). What it writes is decided by two preferences it is started with (see {@code
 *       JdtlsSettings.javaPreferences}): with its defaults it would put a blank line between its groups of imports and
 *       collapse a hundred imports of one package into a star ({@link #ORGANIZE_IMPORTS_KEEPS_EXPLICIT_IMPORTS}). A type
 *       that two packages declare and that a file uses without importing it is left unresolved, without a word.
 * </ul>
 *
 * <p>The fake answers from the request alone: it does not check that the files a test wrote are the ones of the
 * scenario, so a test that scripts a scenario writes the project with {@code Scenario#writeProject} when it matters.
 *
 * <p>To add a scenario, follow the skill {@code .claude/skills/add-scenario/SKILL.md}.
 */
public final class Scenarios {

    private static final Path GREETER = Path.of("src/main/java/demo/Greeter.java");
    private static final Path WELCOMER = Path.of("src/main/java/demo/Welcomer.java");
    private static final Path MAIN = Path.of("src/main/java/demo/Main.java");
    private static final Path BROKEN = Path.of("src/main/java/demo/Broken.java");
    private static final Path SLOPPY = Path.of("src/main/java/demo/Sloppy.java");
    private static final Path MISSING = Path.of("src/main/java/demo/Missing.java");
    private static final Path FOLDER = Path.of("src/main/java/demo");
    private static final Path ELSEWHERE = Path.of("../outside/Elsewhere.java");
    private static final Path UNTIDY = Path.of("src/main/java/demo/Untidy.java");
    private static final Path TIDY = Path.of("src/main/java/demo/Tidy.java");
    private static final Path WIDE = Path.of("src/main/java/demo/Wide.java");

    /** How many types of one package, and constants of one class, {@code Wide} imports one by one: over the 99 of jdtls. */
    private static final int MANY = 100;

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

    private static final String SLOPPY_SOURCE = """
        package demo;

        public class Sloppy {
            public void run() {
                int unused = 42;
            }
        }
        """;

    /**
     * The imports in every wrong way: one unused ({@code Set}), two missing ({@code ArrayList}, {@code Collections}),
     * and the rest out of order, with static imports among the others.
     */
    private static final String UNTIDY_SOURCE = """
        package demo;

        import java.util.Set;
        import static java.util.Map.entry;
        import java.util.Map;
        import org.w3c.dom.Node;
        import java.io.File;
        import javax.swing.JButton;
        import java.util.List;
        import static java.util.stream.Collectors.toList;

        public class Untidy {
            List<String> names = Collections.emptyList();
            ArrayList<String> more = new ArrayList<>();
            Map<String, String> pairs = Map.ofEntries(entry("a", "b"));
            Node node;
            File file;
            JButton button;
            Object all = names.stream().collect(toList());
        }
        """;

    /** Imports as the project keeps them: static first, a blank line, then one block in ASCII order. */
    private static final String TIDY_SOURCE = """
        package demo;

        import static java.util.Map.entry;

        import java.util.List;
        import java.util.Map;

        public class Tidy {
            List<String> names = List.of("a");
            Map<String, String> pairs = Map.ofEntries(entry("a", "b"));
        }
        """;

    private static final Map<Path, String> PROJECT = Map.of(GREETER, GREETER_SOURCE, MAIN, MAIN_SOURCE);

    /**
     * {@code Untidy}, {@code Tidy} and {@code Wide}, with the types and the constants {@code Wide} imports: the project
     * of the scenarios about imports.
     */
    private static final Map<Path, String> IMPORTS_PROJECT = importsProject();

    /** The project after {@link #RENAME_TYPE}: the type is {@code Welcomer}, in its own file, and {@code Main} uses it. */
    private static final Map<Path, String> RENAMED_PROJECT = Map.of(
            WELCOMER, GREETER_SOURCE.replace("Greeter", "Welcomer"), MAIN, MAIN_SOURCE.replace("Greeter", "Welcomer"));

    /** A file without a problem: no diagnostic. */
    public static final Scenario CLEAN_FILE = new Scenario(
            "diagnostics-of-a-clean-file", PROJECT, new Request.Diagnostics(MAIN), new Answer.Diagnosed(List.of()));

    /** A string where an {@code int} is expected: one error, at the string. */
    public static final Scenario FILE_WITH_AN_ERROR = new Scenario(
            "diagnostics-of-a-file-with-an-error",
            withProblems(),
            new Request.Diagnostics(BROKEN),
            new Answer.Diagnosed(List.of(new Diagnostic(
                    Severity.ERROR, new Position(4, 15), "Type mismatch: cannot convert from String to int"))));

    /** A local variable that nothing reads: a warning, not an error, at the name of the variable. */
    public static final Scenario FILE_WITH_A_WARNING = new Scenario(
            "diagnostics-of-a-file-with-a-warning",
            withProblems(),
            new Request.Diagnostics(SLOPPY),
            new Answer.Diagnosed(List.of(new Diagnostic(
                    Severity.WARNING, new Position(4, 12), "The value of the local variable unused is not used"))));

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

    /** A path under the project where there is no file: the file is missing, which is not being outside the project. */
    public static final Scenario DIAGNOSTICS_OF_A_MISSING_FILE = new Scenario(
            "diagnostics-of-a-file-that-does-not-exist",
            PROJECT,
            new Request.Diagnostics(MISSING),
            new Answer.Refused(LanguageServerErrorKey.FILE_MISSING));

    /** A folder of the project: it is not a file, whatever its name. */
    public static final Scenario DIAGNOSTICS_OF_A_FOLDER = new Scenario(
            "diagnostics-of-a-folder",
            PROJECT,
            new Request.Diagnostics(FOLDER),
            new Answer.Refused(LanguageServerErrorKey.NOT_A_FILE));

    /**
     * {@code message}, declared and used in the same method: jdtls answers one edit, from the declaration to the use,
     * that repeats what is between them.
     */
    public static final Scenario RENAME_LOCAL_VARIABLE = new Scenario(
            "rename-a-local-variable",
            PROJECT,
            new Request.Rename(GREETER, new Position(6, 15), "text"),
            new Answer.Renamed(new ChangeSet(List.of(
                    edits(GREETER, replace(6, 15, 7, 22, "text = greeting + \", \" + name;\n        return text"))))));

    /** {@code greeting}, the field of the class: one edit from its declaration to its use, as for a local variable. */
    public static final Scenario RENAME_FIELD = new Scenario(
            "rename-a-field",
            PROJECT,
            new Request.Rename(GREETER, new Position(3, 19), "salutation"),
            new Answer.Renamed(new ChangeSet(List.of(edits(
                    GREETER,
                    replace(
                            3,
                            19,
                            6,
                            33,
                            "salutation = \"Hello\";\n\n"
                                    + "    public String greet(String name) {\n"
                                    + "        String message = salutation"))))));

    /** {@code greet}, called from another class: edits in two files. */
    public static final Scenario RENAME_METHOD = new Scenario(
            "rename-a-method",
            PROJECT,
            new Request.Rename(GREETER, new Position(5, 18), "welcome"),
            new Answer.Renamed(new ChangeSet(List.of(
                    edits(GREETER, replace(5, 18, 23, "welcome")), edits(MAIN, replace(5, 35, 40, "welcome"))))));

    /**
     * {@code Greeter}, a public type: its uses are edited (in {@code Main}, one edit for the two uses of the line), then
     * its file is moved to the name of the new type, as an IDE does (the edits name the old path, as the set is applied
     * in order).
     */
    public static final Scenario RENAME_TYPE = new Scenario(
            "rename-a-type",
            PROJECT,
            new Request.Rename(GREETER, new Position(2, 13), "Welcomer"),
            new Answer.Renamed(new ChangeSet(List.of(
                    edits(GREETER, replace(2, 13, 20, "Welcomer")),
                    edits(MAIN, replace(4, 8, 4, 37, "Welcomer greeter = new Welcomer")),
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

    /**
     * {@code Untidy}: {@code Set} is removed, {@code ArrayList} and {@code Collections} are added, and the imports come
     * out as the project keeps them (the static imports, a blank line, then one block in ASCII order, {@code java} and
     * {@code javax} and {@code org} not apart), none as a star. jdtls answers one edit, from the first import to the
     * last, which it settles by two preferences (see {@code JdtlsSettings.javaPreferences}).
     */
    public static final Scenario ORGANIZE_IMPORTS = new Scenario(
            "organize-imports-of-a-file",
            IMPORTS_PROJECT,
            new Request.OrganizeImports(UNTIDY),
            new Answer.Organized(new ChangeSet(List.of(edits(UNTIDY, replace(2, 0, 9, 49, """
                import static java.util.Map.entry;
                import static java.util.stream.Collectors.toList;

                import java.io.File;
                import java.util.ArrayList;
                import java.util.Collections;
                import java.util.List;
                import java.util.Map;
                import javax.swing.JButton;
                import org.w3c.dom.Node;\
                """))))));

    /** {@code Tidy}: its imports are as the project keeps them, jdtls has no edit to give. */
    public static final Scenario ORGANIZE_IMPORTS_IN_ORDER = new Scenario(
            "organize-imports-of-a-file-in-order",
            IMPORTS_PROJECT,
            new Request.OrganizeImports(TIDY),
            new Answer.AlreadyOrganized());

    /**
     * {@code Wide} imports {@value #MANY} types of one package and as many constants of one class, one by one: jdtls
     * collapses them into {@code import demo.many.*;} and {@code import static demo.many.Constants.*;} with its default
     * thresholds (99, {@code java.sources.organizeImports.starThreshold} and {@code staticStarThreshold}), so with them
     * this scenario fails. The project bans star imports, and the answer is that nothing changes.
     */
    public static final Scenario ORGANIZE_IMPORTS_KEEPS_EXPLICIT_IMPORTS = new Scenario(
            "organize-imports-keeps-explicit-imports",
            IMPORTS_PROJECT,
            new Request.OrganizeImports(WIDE),
            new Answer.AlreadyOrganized());

    /** The project is still being imported: the server cannot say what organizing the imports changes. */
    public static final Scenario ORGANIZE_IMPORTS_WHILE_THE_SERVER_IMPORTS = new Scenario(
            "organize-imports-while-the-server-imports",
            IMPORTS_PROJECT,
            new Request.OrganizeImports(UNTIDY),
            new Answer.Refused(LanguageServerErrorKey.NOT_READY));

    /** A file that is not in the project the server was started on. */
    public static final Scenario ORGANIZE_IMPORTS_OUTSIDE_THE_PROJECT = new Scenario(
            "organize-imports-of-a-file-outside-the-project",
            IMPORTS_PROJECT,
            new Request.OrganizeImports(ELSEWHERE),
            new Answer.Refused(LanguageServerErrorKey.FILE_OUTSIDE_PROJECT));

    /**
     * {@code greeting + ", " + name} in {@code greet}, extracted as {@code joined}: the variable is declared where the
     * expression was, and the expression is replaced by its name. jdtls proposes a name of its own and expects the
     * client to rename it (see {@code JdtlsLanguageServer}); the answer holds the one edit from the declaration to the
     * end of the statement that results of both, so {@code joined} is the name the caller gave.
     */
    public static final Scenario EXTRACT_VARIABLE = new Scenario(
            "extract-a-variable",
            PROJECT,
            new Request.ExtractVariable(GREETER, new Range(new Position(6, 25), new Position(6, 47)), "joined"),
            new Answer.Extracted(new ChangeSet(List.of(edits(
                    GREETER,
                    replace(6, 15, 6, 47, "joined = greeting + \", \" + name;\n        String message = joined"))))));

    /** The keyword {@code return}: a selection that is no expression, which jdtls offers no extraction for. */
    public static final Scenario EXTRACT_VARIABLE_FROM_SOMETHING_THAT_IS_NOT_AN_EXPRESSION = new Scenario(
            "extract-a-variable-from-something-that-is-not-an-expression",
            PROJECT,
            new Request.ExtractVariable(GREETER, new Range(new Position(7, 8), new Position(7, 14)), "joined"),
            new Answer.Refused(LanguageServerErrorKey.NOT_AN_EXPRESSION));

    /** A whole statement, {@code ;} included: jdtls has no extraction for it. */
    public static final Scenario EXTRACT_VARIABLE_FROM_A_STATEMENT = new Scenario(
            "extract-a-variable-from-a-statement",
            PROJECT,
            new Request.ExtractVariable(GREETER, new Range(new Position(6, 8), new Position(6, 48)), "joined"),
            new Answer.Refused(LanguageServerErrorKey.NOT_AN_EXPRESSION));

    /** {@code System.out.println(...)}, a call of a {@code void} method: it has no value to keep in a variable. */
    public static final Scenario EXTRACT_VARIABLE_FROM_A_VOID_CALL = new Scenario(
            "extract-a-variable-from-a-void-call",
            PROJECT,
            new Request.ExtractVariable(MAIN, new Range(new Position(5, 8), new Position(5, 50)), "printed"),
            new Answer.Refused(LanguageServerErrorKey.NOT_AN_EXPRESSION));

    /**
     * {@code string}, the name jdtls proposes for a {@code String}: no rename is needed, the answer is the edit of
     * jdtls as it is.
     */
    public static final Scenario EXTRACT_VARIABLE_TO_THE_NAME_JDTLS_PROPOSES = new Scenario(
            "extract-a-variable-to-the-name-jdtls-proposes",
            PROJECT,
            new Request.ExtractVariable(GREETER, new Range(new Position(6, 25), new Position(6, 47)), "string"),
            new Answer.Extracted(new ChangeSet(List.of(edits(
                    GREETER,
                    replace(6, 15, 6, 47, "string = greeting + \", \" + name;\n        String message = string"))))));

    /** {@code name}, the parameter of {@code greet}: a variable of that name would collide with it. */
    public static final Scenario EXTRACT_VARIABLE_TO_A_NAME_IN_USE = new Scenario(
            "extract-a-variable-to-a-name-in-use",
            PROJECT,
            new Request.ExtractVariable(GREETER, new Range(new Position(6, 25), new Position(6, 47)), "name"),
            new Answer.Refused(LanguageServerErrorKey.INVALID_NAME));

    /** A name that begins with a digit is not an identifier: jdtls declines the rename that gives the name. */
    public static final Scenario EXTRACT_VARIABLE_TO_AN_INVALID_NAME = new Scenario(
            "extract-a-variable-to-an-invalid-name",
            PROJECT,
            new Request.ExtractVariable(GREETER, new Range(new Position(6, 25), new Position(6, 47)), "1st"),
            new Answer.Refused(LanguageServerErrorKey.INVALID_NAME));

    /** The project is still being imported: the server cannot say what an extraction changes. */
    public static final Scenario EXTRACT_VARIABLE_WHILE_THE_SERVER_IMPORTS = new Scenario(
            "extract-a-variable-while-the-server-imports",
            PROJECT,
            new Request.ExtractVariable(GREETER, new Range(new Position(6, 25), new Position(6, 47)), "joined"),
            new Answer.Refused(LanguageServerErrorKey.NOT_READY));

    /** A file that is not in the project the server was started on. */
    public static final Scenario EXTRACT_VARIABLE_OUTSIDE_THE_PROJECT = new Scenario(
            "extract-a-variable-of-a-file-outside-the-project",
            PROJECT,
            new Request.ExtractVariable(ELSEWHERE, new Range(new Position(0, 0), new Position(0, 1)), "joined"),
            new Answer.Refused(LanguageServerErrorKey.FILE_OUTSIDE_PROJECT));

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

    /**
     * {@code Welcomer} renamed back to {@code Greeter}, once the files of {@link #RENAME_TYPE} are on disk: the first
     * rename with the names swapped, which jdtls only gives if it has learnt that the file was moved and its text
     * edited.
     */
    public static final Scenario RENAME_THE_TYPE_BACK = new Scenario(
            "rename-the-type-back",
            RENAMED_PROJECT,
            new Request.Rename(WELCOMER, new Position(2, 13), "Greeter"),
            new Answer.Renamed(new ChangeSet(List.of(
                    edits(MAIN, replace(4, 8, 4, 39, "Greeter greeter = new Greeter")),
                    edits(WELCOMER, replace(2, 13, 21, "Greeter")),
                    new RenameFile(WELCOMER, GREETER)))));

    /** {@code Broken} after its error was edited into another one: the diagnostic is the one of the new text. */
    public static final Scenario FILE_WITH_ANOTHER_ERROR = new Scenario(
            "diagnostics-of-a-file-with-another-error",
            withBrokenAs(BROKEN_SOURCE.replace("\"forty-two\"", "true")),
            new Request.Diagnostics(BROKEN),
            new Answer.Diagnosed(List.of(new Diagnostic(
                    Severity.ERROR, new Position(4, 15), "Type mismatch: cannot convert from boolean to int"))));

    /** A type renamed, the change set applied to the disk, then the type renamed back. */
    public static final Sequel RENAME_AFTER_A_RENAME =
            new Sequel("rename-after-a-rename", RENAME_TYPE, RENAME_THE_TYPE_BACK);

    /** The error of a file edited on disk between two requests for its diagnostics. */
    public static final Sequel DIAGNOSTICS_AFTER_AN_EDIT =
            new Sequel("diagnostics-after-an-edit", FILE_WITH_AN_ERROR, FILE_WITH_ANOTHER_ERROR);

    private Scenarios() {}

    /** Every scenario of the catalogue. */
    public static List<Scenario> all() {
        return List.of(
                CLEAN_FILE,
                FILE_WITH_AN_ERROR,
                FILE_WITH_A_WARNING,
                DIAGNOSTICS_WHILE_THE_SERVER_IMPORTS,
                DIAGNOSTICS_OUTSIDE_THE_PROJECT,
                DIAGNOSTICS_OF_A_MISSING_FILE,
                DIAGNOSTICS_OF_A_FOLDER,
                RENAME_LOCAL_VARIABLE,
                RENAME_FIELD,
                RENAME_METHOD,
                RENAME_TYPE,
                RENAME_WHERE_THERE_IS_NO_SYMBOL,
                RENAME_TO_AN_INVALID_NAME,
                RENAME_WHILE_THE_SERVER_IMPORTS,
                RENAME_OUTSIDE_THE_PROJECT,
                ORGANIZE_IMPORTS,
                ORGANIZE_IMPORTS_IN_ORDER,
                ORGANIZE_IMPORTS_KEEPS_EXPLICIT_IMPORTS,
                ORGANIZE_IMPORTS_WHILE_THE_SERVER_IMPORTS,
                ORGANIZE_IMPORTS_OUTSIDE_THE_PROJECT,
                EXTRACT_VARIABLE,
                EXTRACT_VARIABLE_FROM_SOMETHING_THAT_IS_NOT_AN_EXPRESSION,
                EXTRACT_VARIABLE_FROM_A_STATEMENT,
                EXTRACT_VARIABLE_FROM_A_VOID_CALL,
                EXTRACT_VARIABLE_TO_THE_NAME_JDTLS_PROPOSES,
                EXTRACT_VARIABLE_TO_A_NAME_IN_USE,
                EXTRACT_VARIABLE_TO_AN_INVALID_NAME,
                EXTRACT_VARIABLE_WHILE_THE_SERVER_IMPORTS,
                EXTRACT_VARIABLE_OUTSIDE_THE_PROJECT);
    }

    /** The sequels of the catalogue: scenarios in a row, with the files changed on disk between them. */
    public static List<Sequel> sequels() {
        return List.of(RENAME_AFTER_A_RENAME, DIAGNOSTICS_AFTER_AN_EDIT);
    }

    /** The project with the two files that have something to report, so that their scenarios share one project. */
    private static Map<Path, String> withProblems() {
        Map<Path, String> files = new HashMap<>(PROJECT);
        files.put(BROKEN, BROKEN_SOURCE);
        files.put(SLOPPY, SLOPPY_SOURCE);

        return files;
    }

    /** The project of the scenarios about imports: {@code Wide}'s types and constants are generated, not typed. */
    private static Map<Path, String> importsProject() {
        Map<Path, String> files = new HashMap<>();
        files.put(UNTIDY, UNTIDY_SOURCE);
        files.put(TIDY, TIDY_SOURCE);
        files.put(WIDE, wideSource());
        for (int index = 0; index < MANY; index++) {
            String type = "T%03d".formatted(index);
            files.put(
                    Path.of("src/main/java/demo/many/" + type + ".java"),
                    "package demo.many;\n\npublic class " + type + " {}\n");
        }
        files.put(Path.of("src/main/java/demo/many/Constants.java"), constantsSource());

        return files;
    }

    /** A class of {@value #MANY} constants: {@code C000} to {@code C099}. */
    private static String constantsSource() {
        String fields = IntStream.range(0, MANY)
                .mapToObj(index -> "    public static final int C%03d = %d;\n".formatted(index, index))
                .collect(Collectors.joining());

        return "package demo.many;\n\npublic class Constants {\n" + fields + "}\n";
    }

    /** A class that uses each of them, with its imports as the project keeps them: nothing to organize. */
    private static String wideSource() {
        String statics = IntStream.range(0, MANY)
                .mapToObj(index -> "import static demo.many.Constants.C%03d;\n".formatted(index))
                .collect(Collectors.joining());
        String types = IntStream.range(0, MANY)
                .mapToObj(index -> "import demo.many.T%03d;\n".formatted(index))
                .collect(Collectors.joining());
        String constants = IntStream.range(0, MANY)
                .mapToObj(index -> "        C%03d,\n".formatted(index))
                .collect(Collectors.joining());
        String fields = IntStream.range(0, MANY)
                .mapToObj(index -> "    T%03d t%03d;\n".formatted(index, index))
                .collect(Collectors.joining());

        return "package demo;\n\n" + statics + "\n" + types + "\npublic class Wide {\n    int[] constants = {\n"
                + constants + "    };\n" + fields + "}\n";
    }

    private static Map<Path, String> withBrokenAs(String source) {
        Map<Path, String> files = withProblems();
        files.put(BROKEN, source);

        return files;
    }

    private static FileChange edits(Path file, TextEdit... edits) {
        return new EditFile(file, List.of(edits));
    }

    /** Replaces the text of one line, from a column to another. */
    private static TextEdit replace(int line, int from, int to, String text) {
        return replace(line, from, line, to, text);
    }

    /** Replaces the text from a position to another, possibly on other lines. */
    private static TextEdit replace(int fromLine, int fromColumn, int toLine, int toColumn, String text) {
        return new TextEdit(new Range(new Position(fromLine, fromColumn), new Position(toLine, toColumn)), text);
    }
}
