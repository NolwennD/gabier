package fr.craft.gabier.editing.fixtures;

import fr.craft.gabier.editing.domain.ChangeSet;
import fr.craft.gabier.editing.domain.Diagnostic;
import fr.craft.gabier.editing.domain.Diagnostic.Severity;
import fr.craft.gabier.editing.domain.FileChange;
import fr.craft.gabier.editing.domain.FileChange.EditFile;
import fr.craft.gabier.editing.domain.FileChange.RenameFile;
import fr.craft.gabier.editing.domain.LanguageServerErrorKey;
import fr.craft.gabier.editing.domain.Location;
import fr.craft.gabier.editing.domain.Position;
import fr.craft.gabier.editing.domain.Range;
import fr.craft.gabier.editing.domain.ReferenceLocations;
import fr.craft.gabier.editing.domain.TextEdit;
import fr.craft.gabier.editing.domain.TypeKind;
import fr.craft.gabier.editing.fixtures.Scenario.Answer;
import fr.craft.gabier.editing.fixtures.Scenario.Request;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * The catalogue of the contract scenarios: every answer the core relies on from a language server for Java. The
 * project is two small classes of a package {@code demo}: a {@code Greeter} with a field, a method and a local
 * variable, and a {@code Main} that uses it; and, for the problems to report, a {@code Broken} (an error) and a {@code
 * Sloppy} (a warning); for the moves of a type, a {@code Shapes} with nested types and a {@code Drawing} that uses one
 * of them from the package {@code demo.app}; for the inlinings, a {@code Prices} and a {@code Checkout} that uses it. All the positions are 0-based ({@code line}, {@code column}) as in {@link Position}; every path
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
 *   <li>the extraction of a variable: {@code java/getRefactorEdit} with the command {@code extractVariable}. The name it
 *       proposes ({@code string}) is kept without a rename ({@link #EXTRACT_VARIABLE_TO_THE_NAME_JDTLS_PROPOSES}). A name
 *       that the method already uses ({@link #EXTRACT_VARIABLE_TO_A_NAME_IN_USE}) is not declined by jdtls: its rename
 *       answers an edit that corrupts the declaration ({@code name= ...}), which the adapter detects and refuses as
 *       {@code invalid-name};
 *   <li>the extraction of a method: the command {@code extractMethod}. The name it proposes is renamed to the one asked
 *       for, and the answer holds the one edit that results from both, from the first character that differs from the
 *       file to the last. The rename of jdtls declines a name that is not an identifier ({@link
 *       #EXTRACT_METHOD_TO_AN_INVALID_NAME}) but not the name of a method the class already has with the same
 *       parameters: it answers an edit that leaves two methods of one signature. The adapter refuses it, from the
 *       methods that jdtls lists for the text ({@code textDocument/documentSymbol}), as {@code invalid-name} ({@link
 *       #EXTRACT_METHOD_TO_A_NAME_IN_USE});
 *   <li>the extraction of a constant: the command {@code extractConstant} (no client capability, no setting) answers the
 *       declaration at the top of the class and the replacement of the expression; the name it proposes is renamed as
 *       for a variable, and the answer holds the one edit that results from both ({@link #EXTRACT_CONSTANT}). A name
 *       that is not an identifier is declined by the rename ({@link #EXTRACT_CONSTANT_TO_AN_INVALID_NAME});
 *   <li>the extraction of a field: the command {@code extractField} (no client capability, no setting; its optional
 *       argument, the place of the initialisation, is not sent) answers the declaration at the top of the class, the
 *       assignment in the current method, which is jdtls's default, and the replacement of the expression. The place it
 *       gives for the name to rename is the assignment, below the declaration; the rename changes the text before it
 *       and the answer holds the one edit that results from both ({@link #EXTRACT_FIELD}). A name that is not an
 *       identifier is declined by the rename ({@link #EXTRACT_FIELD_TO_AN_INVALID_NAME});
 *   <li>a selection that cannot be extracted: the four extractions refuse it in one way, {@code not-extractable}, with
 *       one text written by gabier, never the message of jdtls. jdtls (1.63) answers {@code java/getRefactorEdit} with
 *       nothing, and no message, for all of them: a keyword, a statement, a call of a {@code void} method, half a
 *       statement, an {@code if} whose branch returns, an expression that reads a local variable, an expression outside
 *       a class body ({@link #EXTRACT_VARIABLE_FROM_SOMETHING_THAT_IS_NOT_AN_EXPRESSION}, {@link
 *       #EXTRACT_VARIABLE_FROM_A_STATEMENT}, {@link #EXTRACT_VARIABLE_FROM_A_VOID_CALL}, {@link
 *       #EXTRACT_METHOD_FROM_SOMETHING_THAT_IS_NOT_AN_EXPRESSION}, {@link #EXTRACT_METHOD_FROM_HALF_A_STATEMENT}, {@link
 *       #EXTRACT_METHOD_FROM_A_BRANCH_THAT_RETURNS}, {@link #EXTRACT_CONSTANT_FROM_SOMETHING_THAT_IS_NOT_AN_EXPRESSION},
 *       {@link #EXTRACT_CONSTANT_THAT_USES_A_LOCAL_VARIABLE}, {@link
 *       #EXTRACT_FIELD_FROM_SOMETHING_THAT_IS_NOT_AN_EXPRESSION}, {@link #EXTRACT_FIELD_FROM_OUTSIDE_A_CLASS_BODY}). The
 *       handler of jdtls has no message for these commands, but the adapter takes the other shapes of a refusal for the
 *       same one (an edit that is null with an {@code errorMessage}, a response error {@code InvalidRequest}), which
 *       the adapter's unit tests show with a fake;
 *   <li>the move of a nested type to a file of its own: {@code java/move} with the move kind {@code moveTypeToNewFile}
 *       answers the edits of the files that refer to the type, of the outer class, the creation of the new file
 *       (because the client declares the resource operations) and the edit that gives it its text ({@link
 *       #MOVE_A_STATIC_NESTED_TYPE}); the type that encloses the position is the one that moves, a position in a
 *       method of it included ({@link #MOVE_THE_TYPE_THAT_ENCLOSES_A_POSITION_IN_ITS_METHOD}). An inner class that
 *       uses the outer instance gets a field and a constructor parameter of the outer type, and the field it reads
 *       loses its {@code private} ({@link #MOVE_AN_INNER_TYPE_THAT_USES_THE_OUTER_INSTANCE}); one that does not is moved
 *       as it is ({@link #MOVE_AN_INNER_TYPE_THAT_DOES_NOT_USE_THE_OUTER_INSTANCE}). jdtls refuses with a message and
 *       no edit a position outside any type, a top-level type and a type whose file exists in the package ({@code A
 *       compilation unit named 'X.java' already exists}): one refusal for gabier, {@code not-movable}, which does not
 *       tell them apart ({@link #MOVE_A_TYPE_FROM_A_POSITION_OUTSIDE_ANY_TYPE}, {@link #MOVE_A_TOP_LEVEL_TYPE}, {@link
 *       #MOVE_A_TYPE_TO_A_FILE_THAT_EXISTS}). Nothing is overwritten because jdtls refuses before it computes an edit;
 *       the new file has no line break at its end;
 *   <li>the inlining of a local variable, a method or a constant: the code action {@code refactor.inline} at the
 *       position (an empty range), resolved for its edit. jdtls offers "Make static" with the same kind, so the adapter
 *       takes the action titled "Inline ...". A variable is inlined everywhere whatever the position, with the
 *       expression parenthesized where it could read differently, as in a concatenation ({@link #INLINE_A_LOCAL_VARIABLE}, {@link
 *       #INLINE_A_LOCAL_VARIABLE_FROM_ONE_OF_ITS_USES}); a method and a constant are inlined everywhere, and removed, from
 *       their declaration ({@link #INLINE_A_METHOD}, {@link #INLINE_A_CONSTANT}), and at the one call or use only, the
 *       declaration staying, from a call or a use ({@link #INLINE_ONE_CALL_OF_A_METHOD}, {@link
 *       #INLINE_ONE_USE_OF_A_CONSTANT}). jdtls merges the edits of a file as for a rename. It offers no action, or one
 *       with no edit, for a position on nothing inlinable, a variable assigned twice, a recursive method and an abstract
 *       one, and does not say why: one refusal for gabier, {@code not-inlinable} ({@link
 *       #INLINE_WHERE_THERE_IS_NOTHING_TO_INLINE}, {@link #INLINE_A_VARIABLE_ASSIGNED_TWICE}, {@link
 *       #INLINE_A_RECURSIVE_METHOD}, {@link #INLINE_AN_ABSTRACT_METHOD});
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
 *       that several packages declare and that a file uses without importing it is left out by jdtls, without a word,
 *       unless the client declares that it can choose ({@code advancedOrganizeImportsSupport}): jdtls then sends {@code
 *       java.action.organizeImports.chooseImports} with the candidates, which the adapter keeps and refuses with
 *       ({@link #ORGANIZE_IMPORTS_OF_A_TYPE_TWO_PACKAGES_DECLARE}). The imports of a file that jdtls cannot resolve
 *       ("The package p is not accessible") are deleted by its edit, with the ones nothing uses; one that the file
 *       still uses is refused by the adapter, which names it ({@link
 *       #ORGANIZE_IMPORTS_REFUSES_A_USED_IMPORT_JDTLS_CANNOT_RESOLVE}). An import of a class that does not exist is
 *       kept by jdtls while the file uses it, and removed once nothing does ({@link
 *       #ORGANIZE_IMPORTS_REMOVES_AN_UNUSED_IMPORT_OF_A_CLASS_THAT_DOES_NOT_EXIST}); the adapter does not wait for
 *       diagnostics to write that removal.
 *   <li>the clean-ups of a file ({@link CleanUpScenarios}, {@code gabier cleanup}): {@code java/cleanup} runs the
 *       identifiers of the setting {@code java.cleanup.actions}, which the adapter sets before the request; jdtls
 *       answers one edit of the whole text, or none.
 *   <li>the creation of a type ({@link #CREATE_A_CLASS}, {@link #CREATE_AN_INTERFACE}, {@link #CREATE_A_RECORD}, {@link
 *       #CREATE_AN_ENUM}): the file need not exist. The adapter opens it in jdtls with no text and asks for the
 *       completion at its start: the snippet of the kind is the text of the file, with the {@code package} line that
 *       jdtls derives from the path ({@link #CREATE_A_TYPE_IN_A_SUBPACKAGE}; none in the default package, {@link
 *       #CREATE_A_TYPE_IN_THE_DEFAULT_PACKAGE}; a folder that does not exist yet gives its package too, {@link
 *       #CREATE_A_TYPE_IN_A_NEW_PACKAGE}) and, with the preferences jdtls is started with ({@code
 *       JdtlsSettings.javaPreferences}), no comment. jdtls offers the snippet for any path: for a file under no source
 *       folder it writes no package line, so the adapter asks for the source folders ({@code
 *       java.project.listSourcePaths}) and refuses ({@link #CREATE_A_TYPE_OUTSIDE_THE_SOURCE_FOLDERS}).
 *   <li>the references of a symbol ({@link #REFERENCES_OF_A_TYPE}, {@link #REFERENCES_OF_A_METHOD_WITH_ITS_DECLARATION},
 *       {@link #REFERENCES_OF_A_FIELD_WITHOUT_ITS_ACCESSORS}, {@link #REFERENCES_OF_A_LOCAL_VARIABLE}): the project has a
 *       test source ({@code src/test/java}) and a class in another package that use {@code Ledger}, and the replay marks
 *       the test folder as such. jdtls does not say which place is the declaration: the adapter asks a second time with
 *       it and keeps what is new. With its defaults jdtls lists the accessors of a field with the field; it is started
 *       with {@code java.references.includeAccessors} false (see {@code JdtlsSettings.javaPreferences}). A position on
 *       nothing ({@link #REFERENCES_WHERE_THERE_IS_NO_SYMBOL}, {@link #REFERENCES_FROM_A_COMMENT}) would answer the
 *       references of the enclosing member, so the adapter asks {@code prepareRename} first, as {@code rename} does, and
 *       maps its refusal to {@code no-such-symbol}; a symbol nothing uses is an empty answer ({@link
 *       #REFERENCES_OF_A_SYMBOL_NOTHING_USES}).
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
    private static final Path TWICE = Path.of("src/main/java/demo/Twice.java");
    private static final Path STALE = Path.of("src/main/java/demo/Stale.java");
    private static final Path LOST = Path.of("src/main/java/demo/Lost.java");
    private static final Path MODULE_INFO = Path.of("src/main/java/module-info.java");
    private static final Path TOTALS = Path.of("src/main/java/demo/Totals.java");
    private static final Path SHAPES = Path.of("src/main/java/demo/Shapes.java");
    private static final Path DRAWING = Path.of("src/main/java/demo/app/Drawing.java");
    private static final Path CIRCLE = Path.of("src/main/java/demo/Circle.java");
    private static final Path SQUARE = Path.of("src/main/java/demo/Square.java");
    private static final Path LABEL = Path.of("src/main/java/demo/Label.java");
    private static final Path WIDGET = Path.of("src/main/java/demo/Widget.java");
    private static final Path RENDERER = Path.of("src/main/java/demo/Renderer.java");
    private static final Path POINT = Path.of("src/main/java/demo/Point.java");
    private static final Path COLOR = Path.of("src/main/java/demo/Color.java");
    private static final Path LAUNCHER = Path.of("src/main/java/Launcher.java");
    private static final Path CONSOLE = Path.of("src/main/java/demo/app/Console.java");
    private static final Path UNSOURCED = Path.of("Unsourced.java");
    private static final Path GADGET = Path.of("src/main/java/fresh/Gadget.java");
    private static final Path PRICES = Path.of("src/main/java/demo/Prices.java");
    private static final Path CHECKOUT = Path.of("src/main/java/demo/Checkout.java");
    private static final Path LEDGER = Path.of("src/main/java/demo/Ledger.java");
    private static final Path BANK = Path.of("src/main/java/demo/app/Bank.java");
    private static final Path LEDGER_TEST = Path.of("src/test/java/demo/LedgerTest.java");

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

    /**
     * What the extractions of a method are asked on: a statement that reads a local variable, a block that computes
     * one the rest of the method needs, an expression, an {@code if} whose branch returns, and a method of the
     * signature the extraction is asked to give its method. Nothing refers to it.
     */
    private static final String TOTALS_SOURCE = """
        package demo;

        public class Totals {
            public void print(int[] values) {
                int count = values.length;
                System.out.println("count: " + count);
                System.out.println("done");
            }

            public int total(int[] values, int bonus) {
                int sum = 0;
                for (int value : values) {
                    sum += value;
                }
                return sum + bonus;
            }

            public String describe(int count) {
                if (count == 0) {
                    return "none";
                }
                return "items: " + count;
            }

            public int doubled(int value) {
                return value * 2;
            }

            public int quadrupled(int count) {
                return count * 2 * 2;
            }
        }
        """;

    /**
     * What the moves of a type are asked on: a static nested class ({@code Circle}), an inner class that does not use the
     * instance of {@code Shapes} ({@code Square}), one that does ({@code Label}), and a static nested class named like a
     * type of the package ({@code Totals}), whose file exists. Nothing else refers to it but {@link #DRAWING_SOURCE}.
     */
    private static final String SHAPES_SOURCE = """
        package demo;

        public class Shapes {
            private final String unit = "cm";

            public static class Circle {
                public double area(double radius) {
                    return radius * radius * 3;
                }
            }

            public class Square {
                public double area(double side) {
                    return side * side;
                }
            }

            public class Label {
                public String show() {
                    return "unit: " + unit;
                }
            }

            public static class Totals {
                public int count() {
                    return 0;
                }
            }

            public Circle circle() {
                return new Circle();
            }
        }
        """;

    /** A user of {@code Shapes.Circle} in another package: its imports change when the type moves. */
    private static final String DRAWING_SOURCE = """
        package demo.app;

        import demo.Shapes;

        public class Drawing {
            public double circleArea(Shapes shapes) {
                Shapes.Circle circle = shapes.circle();
                return circle.area(2);
            }
        }
        """;

    /**
     * What the inlinings are asked on: a constant ({@code VAT}), a local variable used twice ({@code vat}), a method
     * called twice in its class and once in {@link #CHECKOUT_SOURCE} ({@code handling}), a variable assigned twice
     * ({@code rate}), a recursive method ({@code countdown}) and an abstract one ({@code apply}).
     */
    private static final String PRICES_SOURCE = """
        package demo;

        public class Prices {
            static final int VAT = 20;

            public int gross(int net) {
                int vat = net * VAT / 100;
                System.out.println("tax: " + vat);
                return net + vat;
            }

            public int shipped(int weight) {
                return handling(weight) + handling(weight * 2);
            }

            int handling(int weight) {
                return weight * 3 + 5;
            }

            public int discounted(int net) {
                int rate = 10;
                rate = rate + 5;
                return net - net * rate / 100;
            }

            int countdown(int steps) {
                return steps <= 0 ? 0 : countdown(steps - 1) + 1;
            }

            abstract static class Rule {
                abstract int apply(int net);
            }
        }
        """;

    /** A user of the constant and of the method of {@link #PRICES_SOURCE} in another file. */
    private static final String CHECKOUT_SOURCE = """
        package demo;

        public class Checkout {
            public int total(Prices prices, int net) {
                return net + Prices.VAT + prices.handling(net);
            }
        }
        """;

    /**
     * What the references are asked on: a type ({@code Ledger}), a field with its getter ({@code balance}), a method
     * ({@code deposit}), a local variable ({@code fee}) with a comment to point at, and a method that nothing calls
     * ({@code unused}).
     */
    private static final String LEDGER_SOURCE = """
        package demo;

        public class Ledger {
            private int balance;

            public int getBalance() {
                return balance;
            }

            public void deposit(int amount) {
                int fee = amount / 100; // one percent
                balance = balance + amount - fee;
            }

            public void reset() {
                balance = 0;
            }

            int unused() {
                return 0;
            }
        }
        """;

    /** A user of the type, the method and the getter of {@link #LEDGER_SOURCE} from another package. */
    private static final String BANK_SOURCE = """
        package demo.app;

        import demo.Ledger;

        public class Bank {
            public int open() {
                Ledger ledger = new Ledger();
                ledger.deposit(100);
                return ledger.getBalance();
            }
        }
        """;

    /** A user of {@link #LEDGER_SOURCE} in a test source. */
    private static final String LEDGER_TEST_SOURCE = """
        package demo;

        class LedgerTest {
            int depositsTwice() {
                Ledger ledger = new Ledger();
                ledger.deposit(5);
                ledger.deposit(7);
                return ledger.getBalance();
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

    private static final Map<Path, String> PROJECT = Map.of(
            GREETER,
            GREETER_SOURCE,
            MAIN,
            MAIN_SOURCE,
            TOTALS,
            TOTALS_SOURCE,
            SHAPES,
            SHAPES_SOURCE,
            DRAWING,
            DRAWING_SOURCE,
            PRICES,
            PRICES_SOURCE,
            CHECKOUT,
            CHECKOUT_SOURCE,
            LEDGER,
            LEDGER_SOURCE,
            BANK,
            BANK_SOURCE,
            LEDGER_TEST,
            LEDGER_TEST_SOURCE);

    /**
     * {@code Untidy}, {@code Tidy} and {@code Wide}, with the types and the constants {@code Wide} imports: the project
     * of the scenarios about imports.
     */
    private static final Map<Path, String> IMPORTS_PROJECT = importsProject();

    /** {@code Date}, used without an import, is declared by {@code java.util} and by {@code java.sql}. */
    private static final String TWICE_SOURCE = """
        package demo;

        import java.util.List;

        public class Twice {
            List<String> names;
            Date when;
        }
        """;

    /** {@code demo.gone.Gone} was deleted: the import is unused, and jdtls cannot resolve it. */
    private static final String STALE_SOURCE = """
        package demo;

        import demo.gone.Gone;
        import java.util.List;

        public class Stale {
            List<String> names;
        }
        """;

    /**
     * The module {@code demo} reads only {@code java.base}: {@code java.sql} is not accessible to it, and {@code Lost}
     * imports {@code java.sql.*} and uses {@code Date}.
     */
    private static final String LOST_SOURCE = """
        package demo;

        import java.sql.*;
        import java.util.List;

        public class Lost {
            List<String> names;
            Date when;
        }
        """;

    private static final String MODULE_INFO_SOURCE = """
        module demo {
        }
        """;

    /** The project of the scenario of an unused import of a deleted class: it compiles once the import is removed. */
    private static final Map<Path, String> STALE_IMPORT_PROJECT = Map.of(STALE, STALE_SOURCE);

    /** {@code Twice} uses a type that two packages declare: the project does not compile as it is, on purpose. */
    private static final Map<Path, String> AMBIGUOUS_IMPORTS_PROJECT = Map.of(TWICE, TWICE_SOURCE);

    /** The module that cannot read the package {@code Lost} imports: the project does not compile, on purpose. */
    private static final Map<Path, String> NOT_ACCESSIBLE_IMPORT_PROJECT =
            Map.of(MODULE_INFO, MODULE_INFO_SOURCE, LOST, LOST_SOURCE);

    /** The project after {@link #RENAME_TYPE}: the type is {@code Welcomer}, in its own file, and {@code Main} uses it. */
    private static final Map<Path, String> RENAMED_PROJECT = Map.of(
            WELCOMER,
            GREETER_SOURCE.replace("Greeter", "Welcomer"),
            MAIN,
            MAIN_SOURCE.replace("Greeter", "Welcomer"),
            TOTALS,
            TOTALS_SOURCE,
            SHAPES,
            SHAPES_SOURCE,
            DRAWING,
            DRAWING_SOURCE,
            PRICES,
            PRICES_SOURCE,
            CHECKOUT,
            CHECKOUT_SOURCE,
            LEDGER,
            LEDGER_SOURCE,
            BANK,
            BANK_SOURCE,
            LEDGER_TEST,
            LEDGER_TEST_SOURCE);

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
            new Answer.Renamed(new ChangeSet(List.of(edits(GREETER, replace(3, 19, 6, 33, """
                salutation = "Hello";

                    public String greet(String name) {
                        String message = salutation\
                """))))));

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

    /**
     * {@code Date}, used without an import: several packages declare it (jdtls offers the nested types of the JDK as
     * well). jdtls cannot choose and asks the client
     * ({@code java.action.organizeImports.chooseImports}); gabier cannot either, so the refusal names the candidates
     * and nothing is written.
     */
    public static final Scenario ORGANIZE_IMPORTS_OF_A_TYPE_TWO_PACKAGES_DECLARE = new Scenario(
            "organize-imports-of-a-type-two-packages-declare",
            AMBIGUOUS_IMPORTS_PROJECT,
            new Request.OrganizeImports(TWICE),
            new Answer.Refused(
                    LanguageServerErrorKey.AMBIGUOUS_IMPORTS,
                    List.of(
                            "java.sql.Date",
                            "java.util.Date",
                            "sun.util.calendar.BaseCalendar.Date",
                            "sun.util.calendar.LocalGregorianCalendar.Date")));

    /**
     * {@code Stale} imports {@code demo.gone.Gone}, a class that does not exist, and nothing uses it: the import is
     * removed, as jdtls's edit says, and the file is not given to jdtls to be judged (nothing that is used can be lost).
     */
    public static final Scenario ORGANIZE_IMPORTS_REMOVES_AN_UNUSED_IMPORT_OF_A_CLASS_THAT_DOES_NOT_EXIST =
            new Scenario(
                    "organize-imports-removes-an-unused-import-of-a-class-that-does-not-exist",
                    STALE_IMPORT_PROJECT,
                    new Request.OrganizeImports(STALE),
                    new Answer.Organized(new ChangeSet(List.of(edits(STALE, replace(2, 0, 3, 0, ""))))));

    /**
     * {@code Lost} imports {@code java.sql.*}, which the module {@code demo} cannot read ("The package java.sql is not
     * accessible"), and uses {@code Date}: jdtls deletes the import with the ones nothing uses, which would leave code
     * that does not compile. The adapter refuses and names it. (jdtls keeps the import of a class that does not exist
     * while the file uses it, and for a single-type import of a module it cannot read it says "The type ... is not
     * accessible" and keeps it: only the package it reports as not accessible is deleted.)
     */
    public static final Scenario ORGANIZE_IMPORTS_REFUSES_A_USED_IMPORT_JDTLS_CANNOT_RESOLVE = new Scenario(
            "organize-imports-refuses-a-used-import-jdtls-cannot-resolve",
            NOT_ACCESSIBLE_IMPORT_PROJECT,
            new Request.OrganizeImports(LOST),
            new Answer.Refused(LanguageServerErrorKey.UNRESOLVED_IMPORTS, List.of("java.sql.*")));

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
            new Answer.Refused(LanguageServerErrorKey.NOT_EXTRACTABLE));

    /** A whole statement, {@code ;} included: jdtls has no extraction for it. */
    public static final Scenario EXTRACT_VARIABLE_FROM_A_STATEMENT = new Scenario(
            "extract-a-variable-from-a-statement",
            PROJECT,
            new Request.ExtractVariable(GREETER, new Range(new Position(6, 8), new Position(6, 48)), "joined"),
            new Answer.Refused(LanguageServerErrorKey.NOT_EXTRACTABLE));

    /** {@code System.out.println(...)}, a call of a {@code void} method: it has no value to keep in a variable. */
    public static final Scenario EXTRACT_VARIABLE_FROM_A_VOID_CALL = new Scenario(
            "extract-a-variable-from-a-void-call",
            PROJECT,
            new Request.ExtractVariable(MAIN, new Range(new Position(5, 8), new Position(5, 50)), "printed"),
            new Answer.Refused(LanguageServerErrorKey.NOT_EXTRACTABLE));

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

    /**
     * {@code System.out.println("count: " + count);}: a statement that reads a local variable, which becomes a
     * parameter. The edit is the one jdtls answers, folded with the rename: it starts where the file differs and ends
     * where it stops differing, so it reaches the next statement.
     */
    public static final Scenario EXTRACT_METHOD_THAT_USES_A_LOCAL_VARIABLE = new Scenario(
            "extract-a-method-that-uses-a-local-variable",
            PROJECT,
            new Request.ExtractMethod(TOTALS, new Range(new Position(5, 8), new Position(5, 46)), "printCount"),
            new Answer.Extracted(new ChangeSet(List.of(edits(TOTALS, replace(5, 8, 6, 33, """
                printCount(count);
                        System.out.println("done");
                    }

                    private void printCount(int count) {
                        System.out.println("count: " + count\
                """))))));

    /** The block that computes {@code sum}, which the rest of the method returns: the method returns it. */
    public static final Scenario EXTRACT_METHOD_THAT_ASSIGNS_A_LOCAL_VARIABLE = new Scenario(
            "extract-a-method-that-assigns-a-local-variable",
            PROJECT,
            new Request.ExtractMethod(TOTALS, new Range(new Position(10, 8), new Position(13, 9)), "sumOf"),
            new Answer.Extracted(new ChangeSet(List.of(edits(TOTALS, replace(10, 18, 14, 26, """
                sumOf(values);
                        return sum + bonus;
                    }

                    private int sumOf(int[] values) {
                        int sum = 0;
                        for (int value : values) {
                            sum += value;
                        }
                        return sum\
                """))))));

    /** {@code count * 2 * 2}: an expression, which becomes the call. */
    public static final Scenario EXTRACT_METHOD_FROM_AN_EXPRESSION = new Scenario(
            "extract-a-method-from-an-expression",
            PROJECT,
            new Request.ExtractMethod(TOTALS, new Range(new Position(29, 15), new Position(29, 28)), "scaled"),
            new Answer.Extracted(new ChangeSet(List.of(edits(TOTALS, replace(29, 15, 29, 15, """
                scaled(count);
                    }

                    private int scaled(int count) {
                        return\s\
                """))))));

    /** The keyword {@code return}: a selection that is not statements or an expression, as for the other extractions. */
    public static final Scenario EXTRACT_METHOD_FROM_SOMETHING_THAT_IS_NOT_AN_EXPRESSION = new Scenario(
            "extract-a-method-from-something-that-is-not-an-expression",
            PROJECT,
            new Request.ExtractMethod(GREETER, new Range(new Position(7, 8), new Position(7, 14)), "joined"),
            new Answer.Refused(LanguageServerErrorKey.NOT_EXTRACTABLE));

    /** From the middle of one statement to the middle of the next: not statements, jdtls offers no extraction. */
    public static final Scenario EXTRACT_METHOD_FROM_HALF_A_STATEMENT = new Scenario(
            "extract-a-method-from-half-a-statement",
            PROJECT,
            new Request.ExtractMethod(TOTALS, new Range(new Position(5, 30), new Position(6, 20)), "partial"),
            new Answer.Refused(LanguageServerErrorKey.NOT_EXTRACTABLE));

    /** An {@code if} whose branch returns, with more code after it: not all the paths of the selection return. */
    public static final Scenario EXTRACT_METHOD_FROM_A_BRANCH_THAT_RETURNS = new Scenario(
            "extract-a-method-from-a-branch-that-returns",
            PROJECT,
            new Request.ExtractMethod(TOTALS, new Range(new Position(18, 8), new Position(20, 9)), "none"),
            new Answer.Refused(LanguageServerErrorKey.NOT_EXTRACTABLE));

    /** A name that begins with a digit is not an identifier: jdtls declines the rename that gives the name. */
    public static final Scenario EXTRACT_METHOD_TO_AN_INVALID_NAME = new Scenario(
            "extract-a-method-to-an-invalid-name",
            PROJECT,
            new Request.ExtractMethod(TOTALS, new Range(new Position(5, 8), new Position(5, 46)), "1st"),
            new Answer.Refused(LanguageServerErrorKey.INVALID_NAME));

    /**
     * {@code doubled(int)}, which the class already has: the extracted method would have the same signature. jdtls does
     * not decline the rename, the adapter refuses it.
     */
    public static final Scenario EXTRACT_METHOD_TO_A_NAME_IN_USE = new Scenario(
            "extract-a-method-to-a-name-in-use",
            PROJECT,
            new Request.ExtractMethod(TOTALS, new Range(new Position(29, 15), new Position(29, 28)), "doubled"),
            new Answer.Refused(LanguageServerErrorKey.INVALID_NAME));

    /** The project is still being imported: the server cannot say what an extraction changes. */
    public static final Scenario EXTRACT_METHOD_WHILE_THE_SERVER_IMPORTS = new Scenario(
            "extract-a-method-while-the-server-imports",
            PROJECT,
            new Request.ExtractMethod(TOTALS, new Range(new Position(5, 8), new Position(5, 46)), "printCount"),
            new Answer.Refused(LanguageServerErrorKey.NOT_READY));

    /** A file that is not in the project the server was started on. */
    public static final Scenario EXTRACT_METHOD_OUTSIDE_THE_PROJECT = new Scenario(
            "extract-a-method-of-a-file-outside-the-project",
            PROJECT,
            new Request.ExtractMethod(ELSEWHERE, new Range(new Position(0, 0), new Position(0, 1)), "anything"),
            new Answer.Refused(LanguageServerErrorKey.FILE_OUTSIDE_PROJECT));

    /**
     * {@code "count: "} in {@code print}, extracted as {@code COUNT_LABEL}: the constant is declared at the top of the
     * class and the literal is replaced by its name. jdtls proposes a name of its own and expects the client to rename
     * it; the answer holds the one edit that results of both.
     */
    public static final Scenario EXTRACT_CONSTANT = new Scenario(
            "extract-a-constant",
            PROJECT,
            new Request.ExtractConstant(TOTALS, new Range(new Position(5, 27), new Position(5, 36)), "COUNT_LABEL"),
            new Answer.Extracted(new ChangeSet(List.of(edits(TOTALS, replace(3, 5, 5, 36, """
                rivate static final String COUNT_LABEL = "count: ";

                    public void print(int[] values) {
                        int count = values.length;
                        System.out.println(COUNT_LABEL\
                """))))));

    /** The keyword {@code return}: a selection that is not an expression, as for the other extractions. */
    public static final Scenario EXTRACT_CONSTANT_FROM_SOMETHING_THAT_IS_NOT_AN_EXPRESSION = new Scenario(
            "extract-a-constant-from-something-that-is-not-an-expression",
            PROJECT,
            new Request.ExtractConstant(GREETER, new Range(new Position(7, 8), new Position(7, 14)), "LABEL"),
            new Answer.Refused(LanguageServerErrorKey.NOT_EXTRACTABLE));

    /** {@code "count: " + count}: it reads a local variable, so it cannot be a constant. */
    public static final Scenario EXTRACT_CONSTANT_THAT_USES_A_LOCAL_VARIABLE = new Scenario(
            "extract-a-constant-that-uses-a-local-variable",
            PROJECT,
            new Request.ExtractConstant(TOTALS, new Range(new Position(5, 27), new Position(5, 44)), "COUNT_LABEL"),
            new Answer.Refused(LanguageServerErrorKey.NOT_EXTRACTABLE));

    /** A name that begins with a digit is not an identifier: jdtls declines the rename that gives the name. */
    public static final Scenario EXTRACT_CONSTANT_TO_AN_INVALID_NAME = new Scenario(
            "extract-a-constant-to-an-invalid-name",
            PROJECT,
            new Request.ExtractConstant(TOTALS, new Range(new Position(5, 27), new Position(5, 36)), "1ST"),
            new Answer.Refused(LanguageServerErrorKey.INVALID_NAME));

    /** The project is still being imported: the server cannot say what an extraction changes. */
    public static final Scenario EXTRACT_CONSTANT_WHILE_THE_SERVER_IMPORTS = new Scenario(
            "extract-a-constant-while-the-server-imports",
            PROJECT,
            new Request.ExtractConstant(TOTALS, new Range(new Position(5, 27), new Position(5, 36)), "COUNT_LABEL"),
            new Answer.Refused(LanguageServerErrorKey.NOT_READY));

    /** A file that is not in the project the server was started on. */
    public static final Scenario EXTRACT_CONSTANT_OUTSIDE_THE_PROJECT = new Scenario(
            "extract-a-constant-of-a-file-outside-the-project",
            PROJECT,
            new Request.ExtractConstant(ELSEWHERE, new Range(new Position(0, 0), new Position(0, 1)), "ANYTHING"),
            new Answer.Refused(LanguageServerErrorKey.FILE_OUTSIDE_PROJECT));

    /**
     * {@code "count: "} in {@code print}, extracted as {@code label}: jdtls declares the field and, by default,
     * initialises it in the current method, before the statement that holds the expression. It proposes a name of its
     * own, which is renamed; the answer holds the one edit that results of both.
     */
    public static final Scenario EXTRACT_FIELD = new Scenario(
            "extract-a-field",
            PROJECT,
            new Request.ExtractField(TOTALS, new Range(new Position(5, 27), new Position(5, 36)), "label"),
            new Answer.Extracted(new ChangeSet(List.of(edits(TOTALS, replace(3, 5, 5, 36, """
                rivate String label;

                    public void print(int[] values) {
                        int count = values.length;
                        label = "count: ";
                        System.out.println(label\
                """))))));

    /** {@code return}: a keyword, not an expression, which jdtls has no field for. */
    public static final Scenario EXTRACT_FIELD_FROM_SOMETHING_THAT_IS_NOT_AN_EXPRESSION = new Scenario(
            "extract-a-field-from-something-that-is-not-an-expression",
            PROJECT,
            new Request.ExtractField(GREETER, new Range(new Position(7, 8), new Position(7, 14)), "label"),
            new Answer.Refused(LanguageServerErrorKey.NOT_EXTRACTABLE));

    /** {@code demo}, in the {@code package} line: outside any class body, so no field can hold it. */
    public static final Scenario EXTRACT_FIELD_FROM_OUTSIDE_A_CLASS_BODY = new Scenario(
            "extract-a-field-from-outside-a-class-body",
            PROJECT,
            new Request.ExtractField(TOTALS, new Range(new Position(0, 8), new Position(0, 12)), "label"),
            new Answer.Refused(LanguageServerErrorKey.NOT_EXTRACTABLE));

    /** A name that begins with a digit is not an identifier: jdtls declines the rename that gives the name. */
    public static final Scenario EXTRACT_FIELD_TO_AN_INVALID_NAME = new Scenario(
            "extract-a-field-to-an-invalid-name",
            PROJECT,
            new Request.ExtractField(TOTALS, new Range(new Position(5, 27), new Position(5, 36)), "1st"),
            new Answer.Refused(LanguageServerErrorKey.INVALID_NAME));

    /** The project is still being imported: the server cannot say what an extraction changes. */
    public static final Scenario EXTRACT_FIELD_WHILE_THE_SERVER_IMPORTS = new Scenario(
            "extract-a-field-while-the-server-imports",
            PROJECT,
            new Request.ExtractField(TOTALS, new Range(new Position(5, 27), new Position(5, 36)), "label"),
            new Answer.Refused(LanguageServerErrorKey.NOT_READY));

    /** A file that is not in the project the server was started on. */
    public static final Scenario EXTRACT_FIELD_OUTSIDE_THE_PROJECT = new Scenario(
            "extract-a-field-of-a-file-outside-the-project",
            PROJECT,
            new Request.ExtractField(ELSEWHERE, new Range(new Position(0, 0), new Position(0, 1)), "anything"),
            new Answer.Refused(LanguageServerErrorKey.FILE_OUTSIDE_PROJECT));

    /**
     * {@code Shapes.Circle}, a static nested class, from its name. jdtls creates {@code Circle.java} in the package, takes
     * the type out of {@code Shapes} (the edit reaches the next members, which it repeats) and updates {@code Drawing},
     * which imported {@code Shapes} and now imports {@code Circle} as well. The new file has no line break at its end.
     */
    public static final Scenario MOVE_A_STATIC_NESTED_TYPE = new Scenario(
            "move-a-static-nested-type",
            PROJECT,
            new Request.MoveTypeToFile(SHAPES, new Position(5, 24)),
            new Answer.Moved(new ChangeSet(List.of(
                    edits(DRAWING, replace(2, 0, 6, 21, """
                        import demo.Circle;
                        import demo.Shapes;

                        public class Drawing {
                            public double circleArea(Shapes shapes) {
                                Circle\
                        """)),
                    edits(SHAPES, replace(5, 4, 30, 25, """
                        public class Square {
                                public double area(double side) {
                                    return side * side;
                                }
                            }

                            public class Label {
                                public String show() {
                                    return "unit: " + unit;
                                }
                            }

                            public static class Totals {
                                public int count() {
                                    return 0;
                                }
                            }

                            public Circle circle() {
                                return new Circle\
                        """)),
                    new FileChange.CreateFile(CIRCLE),
                    edits(CIRCLE, replace(0, 0, 0, 0, """
                        package demo;

                        public class Circle {
                            public double area(double radius) {
                                return radius * radius * 3;
                            }
                        }\
                        """))))));

    /**
     * {@code Shapes.Square}, an inner class that does not use the instance of {@code Shapes}: it becomes a plain top-level
     * class, with no field and no constructor parameter.
     */
    public static final Scenario MOVE_AN_INNER_TYPE_THAT_DOES_NOT_USE_THE_OUTER_INSTANCE = new Scenario(
            "move-an-inner-type-that-does-not-use-the-outer-instance",
            PROJECT,
            new Request.MoveTypeToFile(SHAPES, new Position(11, 17)),
            new Answer.Moved(new ChangeSet(List.of(
                    edits(SHAPES, replace(11, 4, 17, 4, """
                        \
                        """)),
                    new FileChange.CreateFile(SQUARE),
                    edits(SQUARE, replace(0, 0, 0, 0, """
                        package demo;

                        public class Square {
                            public double area(double side) {
                                return side * side;
                            }
                        }\
                        """))))));

    /**
     * {@code Shapes.Label}, an inner class that reads a field of the instance of {@code Shapes}: the new class gets a
     * field of type {@code Shapes} and a constructor that takes it (with the empty Javadoc of the templates of jdtls), the
     * field it reads is no longer private, and every {@code new Label()} of the project would get the outer instance.
     */
    public static final Scenario MOVE_AN_INNER_TYPE_THAT_USES_THE_OUTER_INSTANCE = new Scenario(
            "move-an-inner-type-that-uses-the-outer-instance",
            PROJECT,
            new Request.MoveTypeToFile(SHAPES, new Position(17, 17)),
            new Answer.Moved(new ChangeSet(List.of(
                    edits(SHAPES, replace(3, 4, 23, 4, """
                        final String unit = "cm";

                            public static class Circle {
                                public double area(double radius) {
                                    return radius * radius * 3;
                                }
                            }

                            public class Square {
                                public double area(double side) {
                                    return side * side;
                                }
                            }

                            \
                        """)),
                    new FileChange.CreateFile(LABEL),
                    edits(LABEL, replace(0, 0, 0, 0, """
                        package demo;

                        public class Label {
                            /**
                             *
                             */
                            private final Shapes shapes;

                            /**
                             * @param shapes
                             */
                            Label(Shapes shapes) {
                                this.shapes = shapes;
                            }

                            public String show() {
                                return "unit: " + this.shapes.unit;
                            }
                        }\
                        """))))));

    /** A position inside a method of {@code Circle}: the type that encloses it is the one that moves, as for its name. */
    public static final Scenario MOVE_THE_TYPE_THAT_ENCLOSES_A_POSITION_IN_ITS_METHOD = new Scenario(
            "move-the-type-that-encloses-a-position-in-its-method",
            PROJECT,
            new Request.MoveTypeToFile(SHAPES, new Position(7, 15)),
            MOVE_A_STATIC_NESTED_TYPE.answer());

    /** The {@code package} line is in no type: jdtls answers that no type is selected. */
    public static final Scenario MOVE_A_TYPE_FROM_A_POSITION_OUTSIDE_ANY_TYPE = new Scenario(
            "move-a-type-from-a-position-outside-any-type",
            PROJECT,
            new Request.MoveTypeToFile(SHAPES, new Position(0, 3)),
            new Answer.Refused(LanguageServerErrorKey.NOT_MOVABLE));

    /**
     * {@code Shapes}, a top-level type, is already in a file of its name: jdtls refuses (a compilation unit {@code
     * Shapes.java} already exists in the package).
     */
    public static final Scenario MOVE_A_TOP_LEVEL_TYPE = new Scenario(
            "move-a-top-level-type",
            PROJECT,
            new Request.MoveTypeToFile(SHAPES, new Position(2, 13)),
            new Answer.Refused(LanguageServerErrorKey.NOT_MOVABLE));

    /**
     * {@code Shapes.Totals}, whose name is that of a type of the package that has its file: jdtls refuses (a compilation
     * unit {@code Totals.java} already exists in the package) and nothing is overwritten.
     */
    public static final Scenario MOVE_A_TYPE_TO_A_FILE_THAT_EXISTS = new Scenario(
            "move-a-type-to-a-file-that-exists",
            PROJECT,
            new Request.MoveTypeToFile(SHAPES, new Position(23, 24)),
            new Answer.Refused(LanguageServerErrorKey.NOT_MOVABLE));

    /** The project is still being imported: the server cannot say what a move changes. */
    public static final Scenario MOVE_A_TYPE_WHILE_THE_SERVER_IMPORTS = new Scenario(
            "move-a-type-while-the-server-imports",
            PROJECT,
            new Request.MoveTypeToFile(SHAPES, new Position(5, 24)),
            new Answer.Refused(LanguageServerErrorKey.NOT_READY));

    /** A file that is not in the project the server was started on. */
    public static final Scenario MOVE_A_TYPE_OUTSIDE_THE_PROJECT = new Scenario(
            "move-a-type-of-a-file-outside-the-project",
            PROJECT,
            new Request.MoveTypeToFile(ELSEWHERE, new Position(0, 0)),
            new Answer.Refused(LanguageServerErrorKey.FILE_OUTSIDE_PROJECT));

    /**
     * {@code vat}, a local variable used twice, from its declaration: the use is replaced by the expression that
     * initializes it, the declaration is removed.
     */
    public static final Scenario INLINE_A_LOCAL_VARIABLE = new Scenario(
            "inline-a-local-variable",
            PROJECT,
            new Request.Inline(PRICES, new Position(6, 12)),
            new Answer.Inlined(new ChangeSet(List.of(edits(PRICES, replace(6, 8, 8, 24, """
                System.out.println("tax: " + (net * VAT / 100));
                        return net + net * VAT / 100\
                """))))));

    /** The same variable, from one of its uses: a variable is inlined everywhere, whatever the position. */
    public static final Scenario INLINE_A_LOCAL_VARIABLE_FROM_ONE_OF_ITS_USES = new Scenario(
            "inline-a-local-variable-from-one-of-its-uses",
            PROJECT,
            new Request.Inline(PRICES, new Position(8, 21)),
            INLINE_A_LOCAL_VARIABLE.answer());

    /**
     * {@code handling}, a method called twice in its class and once in {@code Checkout}, from its declaration: each call
     * is replaced by the body, the method is removed.
     */
    public static final Scenario INLINE_A_METHOD = new Scenario(
            "inline-a-method",
            PROJECT,
            new Request.Inline(PRICES, new Position(15, 8)),
            new Answer.Inlined(new ChangeSet(List.of(
                    edits(CHECKOUT, replace(4, 34, 54, "net * 3 + 5")), edits(PRICES, replace(12, 15, 19, 4, """
                        weight * 3 + 5 + weight * 2 * 3 + 5;
                            }

                        \s\s\s\s\
                        """))))));

    /** The first call of {@code handling}: jdtls inlines that call only, the method stays for the others. */
    public static final Scenario INLINE_ONE_CALL_OF_A_METHOD = new Scenario(
            "inline-one-call-of-a-method",
            PROJECT,
            new Request.Inline(PRICES, new Position(12, 15)),
            new Answer.Inlined(new ChangeSet(List.of(edits(PRICES, replace(12, 15, 31, "weight * 3 + 5"))))));

    /**
     * {@code VAT}, a constant used in {@code Prices} and in {@code Checkout}, from its declaration: each use is
     * replaced by the value, the constant is removed.
     */
    public static final Scenario INLINE_A_CONSTANT = new Scenario(
            "inline-a-constant",
            PROJECT,
            new Request.Inline(PRICES, new Position(3, 21)),
            new Answer.Inlined(new ChangeSet(
                    List.of(edits(PRICES, replace(3, 4, 6, 27, """
                        public int gross(int net) {
                                int vat = net * 20\
                        """)), edits(CHECKOUT, replace(4, 21, 31, "20"))))));

    /** The use of {@code VAT} in {@code gross}: jdtls inlines that use only, the constant stays for the others. */
    public static final Scenario INLINE_ONE_USE_OF_A_CONSTANT = new Scenario(
            "inline-one-use-of-a-constant",
            PROJECT,
            new Request.Inline(PRICES, new Position(6, 24)),
            new Answer.Inlined(new ChangeSet(List.of(edits(PRICES, replace(6, 24, 27, "20"))))));

    /** The {@code package} line is on no variable, method or constant: jdtls offers no inlining. */
    public static final Scenario INLINE_WHERE_THERE_IS_NOTHING_TO_INLINE = new Scenario(
            "inline-where-there-is-nothing-to-inline",
            PROJECT,
            new Request.Inline(PRICES, new Position(0, 3)),
            new Answer.Refused(LanguageServerErrorKey.NOT_INLINABLE));

    /** {@code rate} is given a value and then another: its value is not one expression, jdtls offers no inlining. */
    public static final Scenario INLINE_A_VARIABLE_ASSIGNED_TWICE = new Scenario(
            "inline-a-variable-assigned-twice",
            PROJECT,
            new Request.Inline(PRICES, new Position(20, 12)),
            new Answer.Refused(LanguageServerErrorKey.NOT_INLINABLE));

    /** {@code countdown} calls itself: it cannot replace its calls by its body, jdtls offers no inlining. */
    public static final Scenario INLINE_A_RECURSIVE_METHOD = new Scenario(
            "inline-a-recursive-method",
            PROJECT,
            new Request.Inline(PRICES, new Position(25, 8)),
            new Answer.Refused(LanguageServerErrorKey.NOT_INLINABLE));

    /** {@code apply} has no body to put in place of its calls: jdtls offers no inlining. */
    public static final Scenario INLINE_AN_ABSTRACT_METHOD = new Scenario(
            "inline-an-abstract-method",
            PROJECT,
            new Request.Inline(PRICES, new Position(30, 21)),
            new Answer.Refused(LanguageServerErrorKey.NOT_INLINABLE));

    /** The project is still being imported: the server cannot say what an inlining changes. */
    public static final Scenario INLINE_WHILE_THE_SERVER_IMPORTS = new Scenario(
            "inline-while-the-server-imports",
            PROJECT,
            new Request.Inline(PRICES, new Position(3, 21)),
            new Answer.Refused(LanguageServerErrorKey.NOT_READY));

    /** A file that is not in the project the server was started on. */
    public static final Scenario INLINE_OUTSIDE_THE_PROJECT = new Scenario(
            "inline-a-file-outside-the-project",
            PROJECT,
            new Request.Inline(ELSEWHERE, new Position(0, 0)),
            new Answer.Refused(LanguageServerErrorKey.FILE_OUTSIDE_PROJECT));

    /**
     * A class in a new file of the package {@code demo}: the file does not exist and is not written by the server,
     * which answers the creation of the file and the one edit that gives it its text. The package line is derived
     * from the path.
     */
    public static final Scenario CREATE_A_CLASS = new Scenario(
            "create-a-class",
            PROJECT,
            new Request.CreateType(WIDGET, TypeKind.CLASS),
            new Answer.Created(created(WIDGET, """
                package demo;

                public class Widget {

                }
                """)));

    /** An interface: the same, with the keyword of the kind. */
    public static final Scenario CREATE_AN_INTERFACE = new Scenario(
            "create-an-interface",
            PROJECT,
            new Request.CreateType(RENDERER, TypeKind.INTERFACE),
            new Answer.Created(created(RENDERER, """
                package demo;

                public interface Renderer {

                }
                """)));

    /** A record, whose components go between the parentheses. */
    public static final Scenario CREATE_A_RECORD = new Scenario(
            "create-a-record",
            PROJECT,
            new Request.CreateType(POINT, TypeKind.RECORD),
            new Answer.Created(created(POINT, """
                package demo;

                public record Point() {
                }
                """)));

    /** An enum, whose constants go in the body. */
    public static final Scenario CREATE_AN_ENUM = new Scenario(
            "create-an-enum",
            PROJECT,
            new Request.CreateType(COLOR, TypeKind.ENUM),
            new Answer.Created(created(COLOR, """
                package demo;

                public enum Color {

                }
                """)));

    /** A file straight under the source folder is in the default package: no {@code package} line. */
    public static final Scenario CREATE_A_TYPE_IN_THE_DEFAULT_PACKAGE = new Scenario(
            "create-a-type-in-the-default-package",
            PROJECT,
            new Request.CreateType(LAUNCHER, TypeKind.CLASS),
            new Answer.Created(created(LAUNCHER, """
                public class Launcher {

                }
                """)));

    /** A file of a folder two levels down: the package is the path of the folder, with dots. */
    public static final Scenario CREATE_A_TYPE_IN_A_SUBPACKAGE = new Scenario(
            "create-a-type-in-a-subpackage",
            PROJECT,
            new Request.CreateType(CONSOLE, TypeKind.CLASS),
            new Answer.Created(created(CONSOLE, """
                package demo.app;

                public class Console {

                }
                """)));

    /**
     * A file in a folder that does not exist yet: the server derives the package from the path all the same, and the
     * change set it answers holds the file only. The application of the set makes the folders ({@code
     * ChangeApplier}); the replay on jdtls applies the answer to a copy of the project to prove it.
     */
    public static final Scenario CREATE_A_TYPE_IN_A_NEW_PACKAGE = new Scenario(
            "create-a-type-in-a-new-package",
            PROJECT,
            new Request.CreateType(GADGET, TypeKind.CLASS),
            new Answer.Created(created(GADGET, """
                package fresh;

                public class Gadget {

                }
                """)));

    /** The project is still being imported: the server cannot say what the new file holds. */
    public static final Scenario CREATE_A_TYPE_WHILE_THE_SERVER_IMPORTS = new Scenario(
            "create-a-type-while-the-server-imports",
            PROJECT,
            new Request.CreateType(WIDGET, TypeKind.CLASS),
            new Answer.Refused(LanguageServerErrorKey.NOT_READY));

    /** A file that is not in the project the server was started on. */
    public static final Scenario CREATE_A_TYPE_OUTSIDE_THE_PROJECT = new Scenario(
            "create-a-type-outside-the-project",
            PROJECT,
            new Request.CreateType(ELSEWHERE, TypeKind.CLASS),
            new Answer.Refused(LanguageServerErrorKey.FILE_OUTSIDE_PROJECT));

    /**
     * A file of the project that is under none of its source folders (here, its root): no package can be derived for
     * it, and jdtls would offer the snippet all the same, as for the default package. The adapter asks which folders
     * are source folders ({@code java.project.listSourcePaths}) and refuses.
     */
    public static final Scenario CREATE_A_TYPE_OUTSIDE_THE_SOURCE_FOLDERS = new Scenario(
            "create-a-type-outside-the-source-folders",
            PROJECT,
            new Request.CreateType(UNSOURCED, TypeKind.CLASS),
            new Answer.Refused(LanguageServerErrorKey.NOT_CREATABLE));

    /**
     * The type {@code Ledger}, asked from its declaration: its uses in another package (the import and the two
     * mentions in {@code Bank}) and in a test source ({@code LedgerTest}), in the order of the files then of the
     * positions. The declaration is not answered unless it is asked for.
     */
    public static final Scenario REFERENCES_OF_A_TYPE = new Scenario(
            "references-of-a-type",
            PROJECT,
            new Request.References(LEDGER, new Position(2, 13), false),
            new Answer.Referenced(new ReferenceLocations(
                    List.of(),
                    List.of(
                            new Location(BANK, new Position(2, 7)),
                            new Location(BANK, new Position(6, 8)),
                            new Location(BANK, new Position(6, 28)),
                            new Location(LEDGER_TEST, new Position(4, 8)),
                            new Location(LEDGER_TEST, new Position(4, 28))))));

    /**
     * The method {@code deposit}, asked from one of its calls, with its declaration: the declaration is answered apart
     * from the calls, which are in another package and in a test source.
     */
    public static final Scenario REFERENCES_OF_A_METHOD_WITH_ITS_DECLARATION = new Scenario(
            "references-of-a-method-with-its-declaration",
            PROJECT,
            new Request.References(BANK, new Position(7, 15), true),
            new Answer.Referenced(new ReferenceLocations(
                    List.of(new Location(LEDGER, new Position(9, 16))),
                    List.of(
                            new Location(BANK, new Position(7, 15)),
                            new Location(LEDGER_TEST, new Position(5, 15)),
                            new Location(LEDGER_TEST, new Position(6, 15))))));

    /**
     * The field {@code balance}: its uses in its class. The calls of its getter {@code getBalance} in {@code Bank} and
     * {@code LedgerTest} are not uses of the field, and jdtls leaves them out only because {@code
     * java.references.includeAccessors} is {@code false} (it is {@code true} by default).
     */
    public static final Scenario REFERENCES_OF_A_FIELD_WITHOUT_ITS_ACCESSORS = new Scenario(
            "references-of-a-field-without-its-accessors",
            PROJECT,
            new Request.References(LEDGER, new Position(3, 16), false),
            new Answer.Referenced(new ReferenceLocations(
                    List.of(),
                    List.of(
                            new Location(LEDGER, new Position(6, 15)),
                            new Location(LEDGER, new Position(11, 8)),
                            new Location(LEDGER, new Position(11, 18)),
                            new Location(LEDGER, new Position(15, 8))))));

    /** The local variable {@code fee}, asked from its declaration: its one use. */
    public static final Scenario REFERENCES_OF_A_LOCAL_VARIABLE = new Scenario(
            "references-of-a-local-variable",
            PROJECT,
            new Request.References(LEDGER, new Position(10, 12), false),
            new Answer.Referenced(
                    new ReferenceLocations(List.of(), List.of(new Location(LEDGER, new Position(11, 37))))));

    /** A method that nothing calls: no reference, which is an answer and not a refusal. */
    public static final Scenario REFERENCES_OF_A_SYMBOL_NOTHING_USES = new Scenario(
            "references-of-a-symbol-nothing-uses",
            PROJECT,
            new Request.References(LEDGER, new Position(18, 8), false),
            new Answer.Referenced(new ReferenceLocations(List.of(), List.of())));

    /**
     * An empty line inside the class. jdtls answers the references of the type that encloses a position where it
     * finds no symbol ({@code ReferencesHandler} falls back to the element at the offset), so the adapter asks {@code
     * prepareRename} first, as for a rename, and refuses what jdtls declines.
     */
    public static final Scenario REFERENCES_WHERE_THERE_IS_NO_SYMBOL = new Scenario(
            "references-where-there-is-no-symbol",
            PROJECT,
            new Request.References(LEDGER, new Position(4, 0), false),
            new Answer.Refused(LanguageServerErrorKey.NO_SUCH_SYMBOL));

    /** A word of a comment: no symbol, whatever the method that holds the comment. */
    public static final Scenario REFERENCES_FROM_A_COMMENT = new Scenario(
            "references-from-a-comment",
            PROJECT,
            new Request.References(LEDGER, new Position(10, 40), false),
            new Answer.Refused(LanguageServerErrorKey.NO_SUCH_SYMBOL));

    /** The project is still being imported: the server cannot say where a symbol is used. */
    public static final Scenario REFERENCES_WHILE_THE_SERVER_IMPORTS = new Scenario(
            "references-while-the-server-imports",
            PROJECT,
            new Request.References(LEDGER, new Position(2, 13), false),
            new Answer.Refused(LanguageServerErrorKey.NOT_READY));

    /** A file that is not in the project the server was started on. */
    public static final Scenario REFERENCES_OF_A_FILE_OUTSIDE_THE_PROJECT = new Scenario(
            "references-of-a-file-outside-the-project",
            PROJECT,
            new Request.References(ELSEWHERE, new Position(0, 0), false),
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

    /** Every scenario of the catalogue, those of {@link PackageMoveScenarios} and {@link CleanUpScenarios} included. */
    public static List<Scenario> all() {
        return Stream.of(own(), PackageMoveScenarios.all(), CleanUpScenarios.all())
                .flatMap(List::stream)
                .toList();
    }

    private static List<Scenario> own() {
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
                ORGANIZE_IMPORTS_OF_A_TYPE_TWO_PACKAGES_DECLARE,
                ORGANIZE_IMPORTS_REMOVES_AN_UNUSED_IMPORT_OF_A_CLASS_THAT_DOES_NOT_EXIST,
                ORGANIZE_IMPORTS_REFUSES_A_USED_IMPORT_JDTLS_CANNOT_RESOLVE,
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
                EXTRACT_VARIABLE_OUTSIDE_THE_PROJECT,
                EXTRACT_METHOD_THAT_USES_A_LOCAL_VARIABLE,
                EXTRACT_METHOD_THAT_ASSIGNS_A_LOCAL_VARIABLE,
                EXTRACT_METHOD_FROM_AN_EXPRESSION,
                EXTRACT_METHOD_FROM_SOMETHING_THAT_IS_NOT_AN_EXPRESSION,
                EXTRACT_METHOD_FROM_HALF_A_STATEMENT,
                EXTRACT_METHOD_FROM_A_BRANCH_THAT_RETURNS,
                EXTRACT_METHOD_TO_AN_INVALID_NAME,
                EXTRACT_METHOD_TO_A_NAME_IN_USE,
                EXTRACT_METHOD_WHILE_THE_SERVER_IMPORTS,
                EXTRACT_METHOD_OUTSIDE_THE_PROJECT,
                EXTRACT_CONSTANT,
                EXTRACT_CONSTANT_FROM_SOMETHING_THAT_IS_NOT_AN_EXPRESSION,
                EXTRACT_CONSTANT_THAT_USES_A_LOCAL_VARIABLE,
                EXTRACT_CONSTANT_TO_AN_INVALID_NAME,
                EXTRACT_CONSTANT_WHILE_THE_SERVER_IMPORTS,
                EXTRACT_CONSTANT_OUTSIDE_THE_PROJECT,
                EXTRACT_FIELD,
                EXTRACT_FIELD_FROM_SOMETHING_THAT_IS_NOT_AN_EXPRESSION,
                EXTRACT_FIELD_FROM_OUTSIDE_A_CLASS_BODY,
                EXTRACT_FIELD_TO_AN_INVALID_NAME,
                EXTRACT_FIELD_WHILE_THE_SERVER_IMPORTS,
                EXTRACT_FIELD_OUTSIDE_THE_PROJECT,
                MOVE_A_STATIC_NESTED_TYPE,
                MOVE_AN_INNER_TYPE_THAT_DOES_NOT_USE_THE_OUTER_INSTANCE,
                MOVE_AN_INNER_TYPE_THAT_USES_THE_OUTER_INSTANCE,
                MOVE_THE_TYPE_THAT_ENCLOSES_A_POSITION_IN_ITS_METHOD,
                MOVE_A_TYPE_FROM_A_POSITION_OUTSIDE_ANY_TYPE,
                MOVE_A_TOP_LEVEL_TYPE,
                MOVE_A_TYPE_TO_A_FILE_THAT_EXISTS,
                MOVE_A_TYPE_WHILE_THE_SERVER_IMPORTS,
                MOVE_A_TYPE_OUTSIDE_THE_PROJECT,
                INLINE_A_LOCAL_VARIABLE,
                INLINE_A_LOCAL_VARIABLE_FROM_ONE_OF_ITS_USES,
                INLINE_A_METHOD,
                INLINE_ONE_CALL_OF_A_METHOD,
                INLINE_A_CONSTANT,
                INLINE_ONE_USE_OF_A_CONSTANT,
                INLINE_WHERE_THERE_IS_NOTHING_TO_INLINE,
                INLINE_A_VARIABLE_ASSIGNED_TWICE,
                INLINE_A_RECURSIVE_METHOD,
                INLINE_AN_ABSTRACT_METHOD,
                INLINE_WHILE_THE_SERVER_IMPORTS,
                INLINE_OUTSIDE_THE_PROJECT,
                CREATE_A_CLASS,
                CREATE_AN_INTERFACE,
                CREATE_A_RECORD,
                CREATE_AN_ENUM,
                CREATE_A_TYPE_IN_THE_DEFAULT_PACKAGE,
                CREATE_A_TYPE_IN_A_SUBPACKAGE,
                CREATE_A_TYPE_IN_A_NEW_PACKAGE,
                CREATE_A_TYPE_WHILE_THE_SERVER_IMPORTS,
                CREATE_A_TYPE_OUTSIDE_THE_PROJECT,
                CREATE_A_TYPE_OUTSIDE_THE_SOURCE_FOLDERS,
                REFERENCES_OF_A_TYPE,
                REFERENCES_OF_A_METHOD_WITH_ITS_DECLARATION,
                REFERENCES_OF_A_FIELD_WITHOUT_ITS_ACCESSORS,
                REFERENCES_OF_A_LOCAL_VARIABLE,
                REFERENCES_OF_A_SYMBOL_NOTHING_USES,
                REFERENCES_WHERE_THERE_IS_NO_SYMBOL,
                REFERENCES_FROM_A_COMMENT,
                REFERENCES_WHILE_THE_SERVER_IMPORTS,
                REFERENCES_OF_A_FILE_OUTSIDE_THE_PROJECT);
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

    /** The creation of a file and the edit that gives it its whole text, as jdtls answers a new type. */
    private static ChangeSet created(Path file, String text) {
        return new ChangeSet(List.of(new FileChange.CreateFile(file), edits(file, replace(0, 0, 0, 0, text))));
    }

    static FileChange edits(Path file, TextEdit... edits) {
        return new EditFile(file, List.of(edits));
    }

    /** Replaces the text of one line, from a column to another. */
    private static TextEdit replace(int line, int from, int to, String text) {
        return replace(line, from, line, to, text);
    }

    /** Replaces the text from a position to another, possibly on other lines. */
    static TextEdit replace(int fromLine, int fromColumn, int toLine, int toColumn, String text) {
        return new TextEdit(new Range(new Position(fromLine, fromColumn), new Position(toLine, toColumn)), text);
    }
}
