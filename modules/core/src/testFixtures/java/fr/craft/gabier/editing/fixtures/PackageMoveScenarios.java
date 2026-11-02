package fr.craft.gabier.editing.fixtures;

import static fr.craft.gabier.editing.fixtures.Scenarios.edits;
import static fr.craft.gabier.editing.fixtures.Scenarios.replace;

import fr.craft.gabier.editing.domain.ChangeSet;
import fr.craft.gabier.editing.domain.FileChange.RenameFile;
import fr.craft.gabier.editing.domain.LanguageServerErrorKey;
import fr.craft.gabier.editing.domain.NotMovableReason;
import fr.craft.gabier.editing.domain.PackageName;
import fr.craft.gabier.editing.fixtures.Scenario.Answer;
import fr.craft.gabier.editing.fixtures.Scenario.Request;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * The contract scenarios of the move of files to a package ({@code gabier move}), kept apart from {@link Scenarios}
 * for their size: they are part of its catalogue ({@link Scenarios#all()}) and are replayed like the others.
 *
 * <p>The project is a package {@code demo} with an {@code Invoice} (which uses a {@code Customer} that stays), its
 * users (a {@code Receipt} of its package, a {@code Shop} of the package {@code demo.app}, an {@code InvoiceTest} in
 * the test sources), a {@code Payment}, and a package {@code demo.billing} that exists and has a {@code Statement}; a
 * resource ({@code src/main/resources/notes.txt}) and a Java file in no source folder ({@code
 * src/main/other/Unsourced.java}) for the files that cannot be moved.
 *
 * <ul>
 *   <li>jdtls is configured, not fought ({@code java/move} with the move kind {@code moveResource}: the files, a
 *       destination that is the URI of the folder of the package, and {@code updateReferences}). It rewrites the {@code
 *       package} line of each file, adds the imports of the types the moved file used from its old package, rewrites
 *       the imports of the other packages and adds an import to the users of the old package, and moves the file ({@link
 *       #MOVE_A_CLASS_TO_AN_EXISTING_PACKAGE}, {@link #MOVE_SEVERAL_CLASSES_TO_AN_EXISTING_PACKAGE}).
 *   <li>The package must exist as a folder jdtls knows. For a package that has no folder it answers no change at all,
 *       without a word, and it does not see a folder made on disk until it is told ({@code
 *       workspace/didChangeWatchedFiles}, an event for the folder). The adapter makes the folder for the time of the
 *       request, tells jdtls, and removes it, telling jdtls again; the change set that comes back moves the files into a
 *       folder that does not exist, which applying the set makes ({@link #MOVE_A_CLASS_TO_A_NEW_PACKAGE}, {@link
 *       #MOVE_A_TEST_CLASS_TO_A_NEW_PACKAGE}: the folder is made under the source folder of the file, test sources
 *       included). The replay checks that the folder is not there after the request.
 *   <li>jdtls does not refuse what gabier does: it skips a file it does not know, moves a file onto a file of its name,
 *       answers an empty move for a file already in the package, and moves a test class into the main sources. The
 *       adapter decides, before it asks: a file that is not a Java source, under no source folder, already in the
 *       package, or whose name a file of the package holds, files of two source folders ({@link
 *       #MOVE_A_FILE_THAT_IS_NOT_A_JAVA_SOURCE}, {@link #MOVE_A_FILE_OF_NO_SOURCE_FOLDER}, {@link
 *       #MOVE_A_CLASS_TO_THE_PACKAGE_IT_IS_IN}, {@link #MOVE_A_CLASS_TO_A_PACKAGE_THAT_HAS_A_FILE_OF_ITS_NAME}, {@link
 *       #MOVE_FILES_OF_TWO_SOURCE_FOLDERS}). The reason is the detail of the refusal ({@link NotMovableReason}).
 *   <li>The files of the other projects that use the moved class are rewritten too, when jdtls knows the projects are
 *       linked: a module that requires the module of the class, the import and the qualified names of its files ({@link
 *       #MOVE_A_CLASS_THAT_ANOTHER_MODULE_USES}, two projects, each with its {@code module-info.java}, the one that
 *       requires the other linked to it). jdtls sees the links of a build of several modules only if the build gives
 *       them: a Gradle build whose plugin {@code org.gradlex.extra-java-module-info} transforms the jars of its own
 *       projects (its default, {@code skipLocalJars} false) gives none, and jdtls then rewrites the module of the class
 *       only.
 *   <li>jdtls does not check what the move breaks: a member with the default access that a class left behind uses is
 *       not reported, and the move is answered all the same. That case has no scenario, as a replay compiles the
 *       project once the answer is applied; {@code gabier diagnostics} shows it afterwards.
 * </ul>
 */
public final class PackageMoveScenarios {

    private static final Path INVOICE = Path.of("src/main/java/demo/Invoice.java");
    private static final Path CUSTOMER = Path.of("src/main/java/demo/Customer.java");
    private static final Path RECEIPT = Path.of("src/main/java/demo/Receipt.java");
    private static final Path PAYMENT = Path.of("src/main/java/demo/Payment.java");
    private static final Path SHOP = Path.of("src/main/java/demo/app/Shop.java");
    private static final Path STATEMENT = Path.of("src/main/java/demo/Statement.java");
    private static final Path BILLING_STATEMENT = Path.of("src/main/java/demo/billing/Statement.java");
    private static final Path LEDGER = Path.of("src/main/java/demo/old/legacy/Ledger.java");
    private static final Path NOTE = Path.of("src/main/java/demo/kept/Note.java");
    private static final Path KEPT_PACKAGE_INFO = Path.of("src/main/java/demo/kept/package-info.java");
    private static final Path NOTES = Path.of("src/main/resources/notes.txt");
    private static final Path INVOICE_TEST = Path.of("src/test/java/demo/InvoiceTest.java");
    private static final Path UNSOURCED = Path.of("src/main/other/Unsourced.java");
    private static final Path MISSING = Path.of("src/main/java/demo/Missing.java");
    private static final Path FOLDER = Path.of("src/main/java/demo");
    private static final Path ELSEWHERE = Path.of("../outside/Elsewhere.java");

    private static final Path BILLING_INVOICE = Path.of("src/main/java/demo/billing/Invoice.java");
    private static final Path BILLING_PAYMENT = Path.of("src/main/java/demo/billing/Payment.java");
    private static final Path ARCHIVE_INVOICE = Path.of("src/main/java/demo/archive/Invoice.java");
    private static final Path BILLING_INVOICE_TEST = Path.of("src/test/java/demo/billing/InvoiceTest.java");
    private static final Path BILLING_LEDGER = Path.of("src/main/java/demo/billing/Ledger.java");
    private static final Path BILLING_NOTE = Path.of("src/main/java/demo/billing/Note.java");

    private static final PackageName DEMO = PackageName.of("demo");
    private static final PackageName BILLING = PackageName.of("demo.billing");
    private static final PackageName ARCHIVE = PackageName.of("demo.archive");

    private static final Map<Path, String> PROJECT = Map.ofEntries(
            Map.entry(INVOICE, """
                package demo;

                public class Invoice {
                    private final Customer customer = new Customer();

                    public String owner() {
                        return customer.name();
                    }
                }
                """),
            Map.entry(CUSTOMER, """
                package demo;

                public class Customer {
                    public String name() {
                        return "ada";
                    }
                }
                """),
            Map.entry(RECEIPT, """
                package demo;

                public class Receipt {
                    public String print(Invoice invoice) {
                        return invoice.owner();
                    }
                }
                """),
            Map.entry(PAYMENT, """
                package demo;

                public class Payment {
                    private final Receipt receipt = new Receipt();
                }
                """),
            Map.entry(SHOP, """
                package demo.app;

                import demo.Invoice;

                public class Shop {
                    public String owner(Invoice invoice) {
                        return invoice.owner();
                    }
                }
                """),
            Map.entry(STATEMENT, """
                package demo;

                public class Statement {
                }
                """),
            Map.entry(BILLING_STATEMENT, """
                package demo.billing;

                public class Statement {
                }
                """),
            Map.entry(LEDGER, """
                package demo.old.legacy;

                public class Ledger {
                }
                """),
            Map.entry(NOTE, """
                package demo.kept;

                public class Note {
                }
                """),
            Map.entry(KEPT_PACKAGE_INFO, """
                package demo.kept;
                """),
            Map.entry(NOTES, "not Java\n"),
            Map.entry(INVOICE_TEST, """
                package demo;

                class InvoiceTest {
                    String owner() {
                        return new Invoice().owner();
                    }
                }
                """),
            Map.entry(UNSOURCED, """
                public class Unsourced {
                }
                """));

    private static final Path MONEY = Path.of("shared/src/main/java/demo/shared/Money.java");
    private static final Path MOVED_MONEY = Path.of("shared/src/main/java/demo/shared/money/Money.java");
    private static final Path TILL = Path.of("app/src/main/java/demo/app/Till.java");
    private static final Path PURSE = Path.of("app/src/main/java/demo/app/Purse.java");

    private static final PackageName MONEY_PACKAGE = PackageName.of("demo.shared.money");

    /**
     * Two projects, each a module, as the modules of a build: {@code shared} ({@code demo.shared}, which exports its two
     * packages) and {@code app} ({@code demo.app}, which requires it and uses its {@code Money} through an import and
     * through qualified names). The package that is left keeps a class, and the one {@code Money} goes to has one
     * already, so that both exports still name a package once it is moved.
     */
    private static final Map<Path, String> MODULES = Map.ofEntries(
            Map.entry(Path.of("shared/src/main/java/module-info.java"), """
                module demo.shared {
                    exports demo.shared;
                    exports demo.shared.money;
                }
                """),
            Map.entry(MONEY, """
                package demo.shared;

                public record Money(long cents) {
                }
                """),
            Map.entry(Path.of("shared/src/main/java/demo/shared/Currency.java"), """
                package demo.shared;

                public enum Currency {
                    EURO
                }
                """),
            Map.entry(Path.of("shared/src/main/java/demo/shared/money/Coin.java"), """
                package demo.shared.money;

                public record Coin(int value) {
                }
                """),
            Map.entry(Path.of("app/src/main/java/module-info.java"), """
                module demo.app {
                    requires demo.shared;
                }
                """),
            Map.entry(TILL, """
                package demo.app;

                import demo.shared.Money;

                public class Till {
                    public Money total() {
                        return new Money(0);
                    }
                }
                """),
            Map.entry(PURSE, """
                package demo.app;

                public class Purse {
                    private final demo.shared.Money money = new demo.shared.Money(1);
                }
                """));

    /** {@code Invoice} to {@code demo.billing}, which exists. */
    public static final Scenario MOVE_A_CLASS_TO_AN_EXISTING_PACKAGE = new Scenario(
            "move-a-class-to-an-existing-package",
            PROJECT,
            new Request.MoveToPackage(List.of(INVOICE), BILLING),
            new Answer.MovedToPackage(new ChangeSet(List.of(
                    edits(INVOICE, replace(0, 13, 2, 0, "\n\nimport demo.Customer;\n\n")),
                    edits(INVOICE, replace(0, 8, 0, 12, "demo.billing")),
                    edits(RECEIPT, replace(0, 13, 2, 0, "\n\nimport demo.billing.Invoice;\n\n")),
                    edits(SHOP, replace(2, 0, 2, 20, "import demo.billing.Invoice;")),
                    edits(INVOICE_TEST, replace(0, 13, 2, 0, "\n\nimport demo.billing.Invoice;\n\n")),
                    new RenameFile(INVOICE, BILLING_INVOICE)))));

    /** {@code Invoice} and {@code Payment} to {@code demo.billing}: two files, one change set. */
    public static final Scenario MOVE_SEVERAL_CLASSES_TO_AN_EXISTING_PACKAGE = new Scenario(
            "move-several-classes-to-an-existing-package",
            PROJECT,
            new Request.MoveToPackage(List.of(INVOICE, PAYMENT), BILLING),
            new Answer.MovedToPackage(new ChangeSet(List.of(
                    edits(INVOICE, replace(0, 13, 2, 0, "\n\nimport demo.Customer;\n\n")),
                    edits(INVOICE, replace(0, 8, 0, 12, "demo.billing")),
                    edits(PAYMENT, replace(0, 13, 2, 0, "\n\nimport demo.Receipt;\n\n")),
                    edits(PAYMENT, replace(0, 8, 0, 12, "demo.billing")),
                    edits(RECEIPT, replace(0, 13, 2, 0, "\n\nimport demo.billing.Invoice;\n\n")),
                    edits(SHOP, replace(2, 0, 2, 20, "import demo.billing.Invoice;")),
                    edits(INVOICE_TEST, replace(0, 13, 2, 0, "\n\nimport demo.billing.Invoice;\n\n")),
                    new RenameFile(INVOICE, BILLING_INVOICE),
                    new RenameFile(PAYMENT, BILLING_PAYMENT)))));

    /** {@code Invoice} to {@code demo.archive}, which has no folder: the change set moves it into one. */
    public static final Scenario MOVE_A_CLASS_TO_A_NEW_PACKAGE = new Scenario(
            "move-a-class-to-a-new-package",
            PROJECT,
            new Request.MoveToPackage(List.of(INVOICE), ARCHIVE),
            new Answer.MovedToPackage(new ChangeSet(List.of(
                    edits(INVOICE, replace(0, 13, 2, 0, "\n\nimport demo.Customer;\n\n")),
                    edits(INVOICE, replace(0, 8, 0, 12, "demo.archive")),
                    edits(RECEIPT, replace(0, 13, 2, 0, "\n\nimport demo.archive.Invoice;\n\n")),
                    edits(SHOP, replace(2, 0, 2, 20, "import demo.archive.Invoice;")),
                    edits(INVOICE_TEST, replace(0, 13, 2, 0, "\n\nimport demo.archive.Invoice;\n\n")),
                    new RenameFile(INVOICE, ARCHIVE_INVOICE)))));

    /**
     * {@code InvoiceTest} to {@code demo.billing}, whose folder does not exist under the test sources: it goes there and
     * not to the folder of the main sources.
     */
    public static final Scenario MOVE_A_TEST_CLASS_TO_A_NEW_PACKAGE = new Scenario(
            "move-a-test-class-to-a-new-package",
            PROJECT,
            new Request.MoveToPackage(List.of(INVOICE_TEST), BILLING),
            new Answer.MovedToPackage(new ChangeSet(List.of(
                    edits(INVOICE_TEST, replace(0, 13, 2, 0, "\n\nimport demo.Invoice;\n\n")),
                    edits(INVOICE_TEST, replace(0, 8, 0, 12, "demo.billing")),
                    new RenameFile(INVOICE_TEST, BILLING_INVOICE_TEST)))));

    /** {@code Invoice} is in {@code demo}: the move would do nothing. */
    public static final Scenario MOVE_A_CLASS_TO_THE_PACKAGE_IT_IS_IN = new Scenario(
            "move-a-class-to-the-package-it-is-in",
            PROJECT,
            new Request.MoveToPackage(List.of(INVOICE), DEMO),
            refused(NotMovableReason.ALREADY_IN_PACKAGE));

    /** {@code demo.billing} has a {@code Statement.java}: the one of {@code demo} would overwrite it. */
    public static final Scenario MOVE_A_CLASS_TO_A_PACKAGE_THAT_HAS_A_FILE_OF_ITS_NAME = new Scenario(
            "move-a-class-to-a-package-that-has-a-file-of-its-name",
            PROJECT,
            new Request.MoveToPackage(List.of(STATEMENT), BILLING),
            refused(NotMovableReason.NAME_TAKEN));

    /** A text file among the sources: jdtls would skip it. */
    public static final Scenario MOVE_A_FILE_THAT_IS_NOT_A_JAVA_SOURCE = new Scenario(
            "move-a-file-that-is-not-a-java-source",
            PROJECT,
            new Request.MoveToPackage(List.of(NOTES), BILLING),
            refused(NotMovableReason.NOT_A_JAVA_SOURCE));

    /** A Java file in no source folder of the project ({@code src/main/other}): it has no package to leave. */
    public static final Scenario MOVE_A_FILE_OF_NO_SOURCE_FOLDER = new Scenario(
            "move-a-file-of-no-source-folder",
            PROJECT,
            new Request.MoveToPackage(List.of(UNSOURCED), BILLING),
            refused(NotMovableReason.OUTSIDE_SOURCE_FOLDERS));

    /** A main class and a test class: the package would be in two folders, and jdtls would put one in the other's. */
    public static final Scenario MOVE_FILES_OF_TWO_SOURCE_FOLDERS = new Scenario(
            "move-files-of-two-source-folders",
            PROJECT,
            new Request.MoveToPackage(List.of(INVOICE, INVOICE_TEST), BILLING),
            refused(NotMovableReason.SEVERAL_SOURCE_FOLDERS));

    /** A file under the sources that is not there. */
    public static final Scenario MOVE_A_FILE_THAT_DOES_NOT_EXIST = new Scenario(
            "move-a-file-that-does-not-exist",
            PROJECT,
            new Request.MoveToPackage(List.of(MISSING), BILLING),
            new Answer.Refused(LanguageServerErrorKey.FILE_MISSING));

    /** A folder is not a file to move. */
    public static final Scenario MOVE_A_FOLDER = new Scenario(
            "move-a-folder",
            PROJECT,
            new Request.MoveToPackage(List.of(FOLDER), BILLING),
            new Answer.Refused(LanguageServerErrorKey.NOT_A_FILE));

    /** A file that is not in the project the server was started on. */
    public static final Scenario MOVE_A_FILE_OUTSIDE_THE_PROJECT = new Scenario(
            "move-a-file-outside-the-project",
            PROJECT,
            new Request.MoveToPackage(List.of(ELSEWHERE), BILLING),
            new Answer.Refused(LanguageServerErrorKey.FILE_OUTSIDE_PROJECT));

    /**
     * {@code Ledger}, the one class of {@code demo.old.legacy}, to {@code demo.billing}: its folder and the folder of
     * {@code demo.old} are left empty.
     */
    public static final Scenario MOVE_THE_LAST_CLASS_OF_A_PACKAGE = new Scenario(
            "move-the-last-class-of-a-package",
            PROJECT,
            new Request.MoveToPackage(List.of(LEDGER), BILLING),
            new Answer.MovedToPackage(new ChangeSet(List.of(
                    edits(LEDGER, replace(0, 8, 0, 23, "demo.billing")), new RenameFile(LEDGER, BILLING_LEDGER)))));

    /** {@code Note}, the one class of {@code demo.kept}, to {@code demo.billing}: its {@code package-info.java} stays. */
    public static final Scenario MOVE_THE_LAST_CLASS_OF_A_PACKAGE_THAT_HAS_A_PACKAGE_INFO = new Scenario(
            "move-the-last-class-of-a-package-that-has-a-package-info",
            PROJECT,
            new Request.MoveToPackage(List.of(NOTE), BILLING),
            new Answer.MovedToPackage(new ChangeSet(
                    List.of(edits(NOTE, replace(0, 8, 0, 17, "demo.billing")), new RenameFile(NOTE, BILLING_NOTE)))));

    /**
     * {@code Money} of the module {@code demo.shared} to {@code demo.shared.money}: the module {@code demo.app}, a project
     * of its own that requires it, has its import and its qualified names rewritten.
     */
    public static final Scenario MOVE_A_CLASS_THAT_ANOTHER_MODULE_USES = new Scenario(
            "move-a-class-that-another-module-uses",
            MODULES,
            new Request.MoveToPackage(List.of(MONEY), MONEY_PACKAGE),
            new Answer.MovedToPackage(new ChangeSet(List.of(
                    edits(PURSE, replace(3, 48, 3, 65, "demo.shared.money.Money")),
                    edits(PURSE, replace(3, 18, 3, 35, "demo.shared.money.Money")),
                    edits(TILL, replace(2, 0, 2, 25, "import demo.shared.money.Money;")),
                    edits(MONEY, replace(0, 8, 0, 19, "demo.shared.money")),
                    new RenameFile(MONEY, MOVED_MONEY)))));

    /** The project is still being imported: the server cannot say what a move changes. */
    public static final Scenario MOVE_WHILE_THE_SERVER_IMPORTS = new Scenario(
            "move-while-the-server-imports",
            PROJECT,
            new Request.MoveToPackage(List.of(INVOICE), BILLING),
            new Answer.Refused(LanguageServerErrorKey.NOT_READY));

    private PackageMoveScenarios() {}

    /** Every scenario of the moves to a package. */
    public static List<Scenario> all() {
        return List.of(
                MOVE_A_CLASS_TO_AN_EXISTING_PACKAGE,
                MOVE_SEVERAL_CLASSES_TO_AN_EXISTING_PACKAGE,
                MOVE_A_CLASS_TO_A_NEW_PACKAGE,
                MOVE_A_TEST_CLASS_TO_A_NEW_PACKAGE,
                MOVE_THE_LAST_CLASS_OF_A_PACKAGE,
                MOVE_THE_LAST_CLASS_OF_A_PACKAGE_THAT_HAS_A_PACKAGE_INFO,
                MOVE_A_CLASS_THAT_ANOTHER_MODULE_USES,
                MOVE_A_CLASS_TO_THE_PACKAGE_IT_IS_IN,
                MOVE_A_CLASS_TO_A_PACKAGE_THAT_HAS_A_FILE_OF_ITS_NAME,
                MOVE_A_FILE_THAT_IS_NOT_A_JAVA_SOURCE,
                MOVE_A_FILE_OF_NO_SOURCE_FOLDER,
                MOVE_FILES_OF_TWO_SOURCE_FOLDERS,
                MOVE_A_FILE_THAT_DOES_NOT_EXIST,
                MOVE_A_FOLDER,
                MOVE_A_FILE_OUTSIDE_THE_PROJECT,
                MOVE_WHILE_THE_SERVER_IMPORTS);
    }

    private static Answer.Refused refused(NotMovableReason reason) {
        return new Answer.Refused(LanguageServerErrorKey.NOT_MOVABLE_TO_PACKAGE, List.of(reason.get()));
    }
}
