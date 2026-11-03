package eu.gabier.editing.fixtures;

import static eu.gabier.editing.fixtures.Scenarios.edits;
import static eu.gabier.editing.fixtures.Scenarios.replace;

import eu.gabier.editing.domain.ChangeSet;
import eu.gabier.editing.domain.Diagnostic;
import eu.gabier.editing.domain.Diagnostic.Severity;
import eu.gabier.editing.domain.FileChange.RenameFile;
import eu.gabier.editing.domain.LanguageServerErrorKey;
import eu.gabier.editing.domain.Position;
import eu.gabier.editing.fixtures.Scenario.Answer;
import eu.gabier.editing.fixtures.Scenario.Request;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The contract scenarios of the rename of a package ({@code gabier rename} on the name in a {@code package} line), kept
 * apart from {@link Scenarios} for their size: they are part of its catalogue ({@link Scenarios#all()}) and are replayed
 * like the others.
 *
 * <p>The project has a package {@code demo.app} (two classes, a sub-package {@code demo.app.sub} whose class uses one of
 * them, a user in {@code demo.other}, and a test class of the same package {@code demo.app} in the test sources) and a
 * package {@code legacy.tools} that holds one class and nothing else.
 *
 * <ul>
 *   <li>{@code textDocument/prepareRename} declines a package name ("Renaming this element is not supported"), while
 *       {@code textDocument/rename} renames the package: the adapter does not let the first one stop a rename whose
 *       position is on the name of a {@code package} line. jdtls takes the position on any segment of the name, and
 *       renames the package named up to that segment; the new name is the whole new name of that package ({@link
 *       #RENAME_A_PACKAGE}, clicked on {@code app}, renames {@code demo.app}).
 *   <li>It renames the package of the source folder of the file only: the same package of the test sources keeps its
 *       name and its folder, and gets an import of the renamed classes; the sub-package keeps its name and its folder,
 *       and its import is rewritten. The same package of another source set is a rename of its own.
 *   <li>The answer moves each file of the package to the folder of the new name, which may not exist, and leaves the old
 *       folder to whoever applies the set (jdtls creates a file {@code .temp} in the new folder, moves the files, then
 *       deletes it: the adapter drops that pair, as the application of the set makes the folder). The folders the move
 *       empties are removed by the use case ({@link #RENAME_A_PACKAGE_UP_TO_THE_SOURCE_FOLDER}).
 *   <li>jdtls declines a name that is not a package name, and a package that exists ({@link
 *       #RENAME_A_PACKAGE_TO_AN_INVALID_NAME}, {@link #RENAME_A_PACKAGE_TO_A_PACKAGE_THAT_EXISTS}); both are an invalid
 *       name for the adapter.
 * </ul>
 */
public final class PackageRenameScenarios {

    private static final Path GREETER = Path.of("src/main/java/demo/app/Greeter.java");
    private static final Path FAREWELL = Path.of("src/main/java/demo/app/Farewell.java");
    private static final Path HELPER = Path.of("src/main/java/demo/app/sub/Helper.java");
    private static final Path MAIN = Path.of("src/main/java/demo/other/Main.java");
    private static final Path GREETER_TEST = Path.of("src/test/java/demo/app/GreeterTest.java");
    private static final Path RULER = Path.of("src/main/java/legacy/tools/Ruler.java");

    private static final Path SHOP_GREETER = Path.of("src/main/java/demo/shop/Greeter.java");
    private static final Path SHOP_FAREWELL = Path.of("src/main/java/demo/shop/Farewell.java");
    private static final Path TOOLS_RULER = Path.of("src/main/java/tools/Ruler.java");
    private static final Path SHOP_GREETER_TEST = Path.of("src/test/java/demo/shop/GreeterTest.java");

    private static final Map<Path, String> PROJECT = Map.of(
            GREETER, """
            package demo.app;

            public class Greeter {
                public String greet() {
                    return "hi";
                }
            }
            """,
            FAREWELL, """
            package demo.app;

            public class Farewell {
                String bye() {
                    return new Greeter().greet();
                }
            }
            """,
            HELPER, """
            package demo.app.sub;

            import demo.app.Greeter;

            public class Helper {
                String hello() {
                    return new Greeter().greet();
                }
            }
            """,
            MAIN, """
            package demo.other;

            import demo.app.Farewell;
            import demo.app.Greeter;

            public class Main {
                String run() {
                    return new Greeter().greet() + new Farewell();
                }
            }
            """,
            GREETER_TEST, """
            package demo.app;

            class GreeterTest {
                String run() {
                    return new Greeter().greet();
                }
            }
            """,
            RULER, """
            package legacy.tools;

            public class Ruler {
            }
            """);

    /** The project once {@link #RENAME_A_PACKAGE} is applied: the tests are still in {@code demo.app}. */
    private static final Map<Path, String> AFTER_THE_RENAME = Map.of(
            SHOP_GREETER, PROJECT.get(GREETER).replace("package demo.app;", "package demo.shop;"),
            SHOP_FAREWELL, PROJECT.get(FAREWELL).replace("package demo.app;", "package demo.shop;"),
            HELPER, PROJECT.get(HELPER).replace("import demo.app.Greeter;", "import demo.shop.Greeter;"),
            MAIN,
                    PROJECT.get(MAIN)
                            .replace("import demo.app.Farewell;", "import demo.shop.Farewell;")
                            .replace("import demo.app.Greeter;", "import demo.shop.Greeter;"),
            GREETER_TEST,
                    PROJECT.get(GREETER_TEST)
                            .replace("package demo.app;\n", "package demo.app;\n\nimport demo.shop.Greeter;\n"),
            RULER, PROJECT.get(RULER));

    /**
     * {@code app} of {@code package demo.app;}: the package {@code demo.app} becomes {@code demo.shop}. Its two classes
     * change their {@code package} line and move to the folder of the new name; the classes that use them are edited
     * (the sub-package, the other package, the test class of the test sources, which stays in {@code demo.app}).
     */
    public static final Scenario RENAME_A_PACKAGE = new Scenario(
            "rename-a-package",
            PROJECT,
            new Request.Rename(GREETER, new Position(0, 13), "demo.shop"),
            new Answer.Renamed(new ChangeSet(List.of(
                    edits(GREETER, replace(0, 8, 0, 16, "demo.shop")),
                    edits(FAREWELL, replace(0, 8, 0, 16, "demo.shop")),
                    edits(HELPER, replace(2, 0, 2, 24, "import demo.shop.Greeter;")),
                    edits(
                            MAIN,
                            replace(2, 0, 2, 25, "import demo.shop.Farewell;"),
                            replace(3, 0, 3, 24, "import demo.shop.Greeter;")),
                    edits(GREETER_TEST, replace(0, 17, 2, 0, "\n\nimport demo.shop.Greeter;\n\n")),
                    new RenameFile(GREETER, SHOP_GREETER),
                    new RenameFile(FAREWELL, SHOP_FAREWELL)))));

    /**
     * {@code tools} of {@code package legacy.tools;}, to {@code tools}: the package moves to the source folder, and the
     * folders {@code legacy/tools} and {@code legacy} it leaves are empty.
     */
    public static final Scenario RENAME_A_PACKAGE_UP_TO_THE_SOURCE_FOLDER = new Scenario(
            "rename-a-package-up-to-the-source-folder",
            PROJECT,
            new Request.Rename(RULER, new Position(0, 18), "tools"),
            new Answer.Renamed(new ChangeSet(
                    List.of(edits(RULER, replace(0, 8, 0, 20, "tools")), new RenameFile(RULER, TOOLS_RULER)))));

    /** A name that begins with a digit is not a package name. */
    public static final Scenario RENAME_A_PACKAGE_TO_AN_INVALID_NAME = new Scenario(
            "rename-a-package-to-an-invalid-name",
            PROJECT,
            new Request.Rename(GREETER, new Position(0, 13), "demo.1st"),
            new Answer.Refused(LanguageServerErrorKey.INVALID_NAME));

    /** {@code demo.other} has a class: a package of that name exists, and jdtls declines to merge into it. */
    public static final Scenario RENAME_A_PACKAGE_TO_A_PACKAGE_THAT_EXISTS = new Scenario(
            "rename-a-package-to-a-package-that-exists",
            PROJECT,
            new Request.Rename(GREETER, new Position(0, 13), "demo.other"),
            new Answer.Refused(LanguageServerErrorKey.INVALID_NAME));

    /**
     * The same package of the test sources, once {@link #RENAME_A_PACKAGE} is on disk: the second call that the first one
     * leaves to make. The test class takes the package line and the folder of the new name, and keeps the import that
     * the first call gave it, which is now of its own package (a warning of the compiler, not an error).
     */
    public static final Scenario RENAME_THE_SAME_PACKAGE_OF_THE_TESTS = new Scenario(
            "rename-the-same-package-of-the-tests",
            AFTER_THE_RENAME,
            new Request.Rename(GREETER_TEST, new Position(0, 13), "demo.shop"),
            new Answer.Renamed(new ChangeSet(List.of(
                    edits(GREETER_TEST, replace(0, 8, 0, 16, "demo.shop")),
                    new RenameFile(GREETER_TEST, SHOP_GREETER_TEST)))));

    /**
     * The project of the two calls: no error, and the one warning that remains, the import of a class of the package
     * the test class is in. jdtls reports a redundant import as a warning, not an error.
     */
    public static final Scenario DIAGNOSTICS_AFTER_RENAMING_A_PACKAGE_IN_BOTH_SOURCE_SETS = new Scenario(
            "diagnostics-after-renaming-a-package-in-both-source-sets",
            afterTheTwoCalls(),
            new Request.Diagnostics(SHOP_GREETER_TEST),
            new Answer.Diagnosed(List.of(new Diagnostic(
                    Severity.WARNING, new Position(2, 7), "The import demo.shop.Greeter is never used"))));

    /** The same project: organizing the imports of the test class removes the import that the two calls left. */
    public static final Scenario ORGANIZE_IMPORTS_AFTER_RENAMING_A_PACKAGE_IN_BOTH_SOURCE_SETS = new Scenario(
            "organize-imports-after-renaming-a-package-in-both-source-sets",
            afterTheTwoCalls(),
            new Request.OrganizeImports(SHOP_GREETER_TEST),
            new Answer.Organized(new ChangeSet(List.of(edits(SHOP_GREETER_TEST, replace(2, 0, 4, 0, ""))))));

    /** A package renamed in the main sources, the change set applied to the disk, then the same package of the tests. */
    public static final Sequel RENAME_A_PACKAGE_THEN_THE_SAME_PACKAGE_OF_THE_TESTS = new Sequel(
            "rename-a-package-then-the-same-package-of-the-tests",
            RENAME_A_PACKAGE,
            RENAME_THE_SAME_PACKAGE_OF_THE_TESTS);

    private PackageRenameScenarios() {}

    /**
     * The project once the package is renamed in the main and in the test sources ({@link #RENAME_A_PACKAGE}, then
     * {@link #RENAME_THE_SAME_PACKAGE_OF_THE_TESTS}, each applied): the test class is in {@code demo.shop} and keeps the
     * import of a class of its own package.
     */
    public static Map<Path, String> afterTheTwoCalls() {
        Map<Path, String> files = new HashMap<>(AFTER_THE_RENAME);
        files.put(SHOP_GREETER_TEST, files.remove(GREETER_TEST).replace("package demo.app;", "package demo.shop;"));

        return Map.copyOf(files);
    }

    /** Every scenario of this class, in the order they are declared. */
    public static List<Scenario> all() {
        return List.of(
                RENAME_A_PACKAGE,
                RENAME_A_PACKAGE_UP_TO_THE_SOURCE_FOLDER,
                RENAME_A_PACKAGE_TO_AN_INVALID_NAME,
                RENAME_A_PACKAGE_TO_A_PACKAGE_THAT_EXISTS,
                DIAGNOSTICS_AFTER_RENAMING_A_PACKAGE_IN_BOTH_SOURCE_SETS,
                ORGANIZE_IMPORTS_AFTER_RENAMING_A_PACKAGE_IN_BOTH_SOURCE_SETS);
    }
}
