package fr.craft.gabier.editing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import fr.craft.gabier.editing.domain.ChangeReport;
import fr.craft.gabier.editing.domain.ChangeReport.Renamed;
import fr.craft.gabier.editing.domain.LanguageServerErrorKey;
import fr.craft.gabier.editing.domain.LanguageServerException;
import fr.craft.gabier.editing.domain.PackageName;
import fr.craft.gabier.editing.fixtures.FakeJavaLanguageServer;
import fr.craft.gabier.editing.fixtures.PackageMoveScenarios;
import fr.craft.gabier.editing.fixtures.Scenario;
import fr.craft.gabier.editing.fixtures.Scenario.Request;
import fr.craft.gabier.editing.infrastructure.secondary.NioFileStore;
import fr.craft.gabier.shared.error.domain.MissingMandatoryValueException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MoveToPackageTest {

    private static final String MAIN = "src/main/java/demo/";
    private static final String INVOICE = MAIN + "Invoice.java";
    private static final String PAYMENT = MAIN + "Payment.java";
    private static final String RECEIPT = MAIN + "Receipt.java";
    private static final String SHOP = MAIN + "app/Shop.java";
    private static final String INVOICE_TEST = "src/test/java/demo/InvoiceTest.java";
    private static final PackageName BILLING = PackageName.of("demo.billing");
    private static final PackageName ARCHIVE = PackageName.of("demo.archive");

    @TempDir
    Path root;

    private FakeJavaLanguageServer server;
    private MoveToPackage move;

    /** Writes the project of the scenarios and plays them with a fake scripted by them. */
    private void project(Scenario... scenarios) throws IOException {
        server = new FakeJavaLanguageServer(root);
        for (Scenario scenario : scenarios) {
            scenario.writeProject(root);
            server.scripted(scenario);
        }
        move = new MoveToPackage(server, new NioFileStore());
    }

    private Path path(String relative) {
        return root.resolve(relative);
    }

    private String text(String relative) throws IOException {
        return Files.readString(path(relative));
    }

    /** Every path under the root, with the text of its files: what "nothing was written" means. */
    private Map<String, String> snapshot() throws IOException {
        Map<String, String> snapshot = new TreeMap<>();
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.toList()) {
                snapshot.put(
                        root.relativize(path).toString(),
                        Files.isDirectory(path) ? "<directory>" : Files.readString(path));
            }
        }

        return snapshot;
    }

    @Test
    void movesAClassToAnExistingPackageAndUpdatesItsPackageLineItsImportsAndItsUsers() throws IOException {
        project(PackageMoveScenarios.MOVE_A_CLASS_TO_AN_EXISTING_PACKAGE);

        ChangeReport report = move.apply(List.of(path(INVOICE)), BILLING);

        assertThat(path(INVOICE)).doesNotExist();
        assertThat(text(MAIN + "billing/Invoice.java"))
                .startsWith("package demo.billing;\n")
                .contains("import demo.Customer;\n")
                .contains("public class Invoice {");
        assertThat(text(RECEIPT)).contains("import demo.billing.Invoice;\n");
        assertThat(text(SHOP)).contains("import demo.billing.Invoice;\n").doesNotContain("import demo.Invoice;");
        assertThat(text(INVOICE_TEST)).contains("import demo.billing.Invoice;\n");
        assertThat(report.renamed()).containsExactly(new Renamed(path(INVOICE), path(MAIN + "billing/Invoice.java")));
        assertThat(report.created()).isEmpty();
        assertThat(report.deleted()).isEmpty();
    }

    @Test
    void movesSeveralClassesToAnExistingPackageInOneChange() throws IOException {
        project(PackageMoveScenarios.MOVE_SEVERAL_CLASSES_TO_AN_EXISTING_PACKAGE);

        ChangeReport report = move.apply(List.of(path(INVOICE), path(PAYMENT)), BILLING);

        assertThat(text(MAIN + "billing/Invoice.java")).startsWith("package demo.billing;\n");
        assertThat(text(MAIN + "billing/Payment.java"))
                .startsWith("package demo.billing;\n")
                .contains("import demo.Receipt;\n");
        assertThat(text(RECEIPT)).contains("import demo.billing.Invoice;\n");
        assertThat(report.renamed())
                .containsExactlyInAnyOrder(
                        new Renamed(path(INVOICE), path(MAIN + "billing/Invoice.java")),
                        new Renamed(path(PAYMENT), path(MAIN + "billing/Payment.java")));
    }

    @Test
    void makesTheFolderOfAPackageThatDoesNotExistAndMovesTheClassInto() throws IOException {
        project(PackageMoveScenarios.MOVE_A_CLASS_TO_A_NEW_PACKAGE);

        ChangeReport report = move.apply(List.of(path(INVOICE)), ARCHIVE);

        assertThat(path(MAIN + "archive")).isDirectory();
        assertThat(text(MAIN + "archive/Invoice.java")).startsWith("package demo.archive;\n");
        assertThat(text(SHOP)).contains("import demo.archive.Invoice;\n");
        assertThat(report.renamed()).containsExactly(new Renamed(path(INVOICE), path(MAIN + "archive/Invoice.java")));
    }

    @Test
    void movesATestClassInsideTheTestSourcesWhenThePackageIsNew() throws IOException {
        project(PackageMoveScenarios.MOVE_A_TEST_CLASS_TO_A_NEW_PACKAGE);

        move.apply(List.of(path(INVOICE_TEST)), BILLING);

        assertThat(path(INVOICE_TEST)).doesNotExist();
        assertThat(text("src/test/java/demo/billing/InvoiceTest.java"))
                .startsWith("package demo.billing;\n")
                .contains("import demo.Invoice;\n");
        assertThat(path(MAIN + "billing/InvoiceTest.java")).doesNotExist();
    }

    @Test
    void asksTheServerOnceForTheFilesAndThePackageAsGiven() throws IOException {
        project(PackageMoveScenarios.MOVE_SEVERAL_CLASSES_TO_AN_EXISTING_PACKAGE);

        move.apply(List.of(path(INVOICE), path(PAYMENT)), BILLING);

        assertThat(server.asked())
                .containsExactly(new Request.MoveToPackage(List.of(path(INVOICE), path(PAYMENT)), BILLING));
    }

    @Test
    void writesNothingOnADryRunAndMakesNoFolderForANewPackage() throws IOException {
        project(PackageMoveScenarios.MOVE_A_CLASS_TO_A_NEW_PACKAGE);
        Map<String, String> before = snapshot();

        ChangeReport report = move.dryRun(List.of(path(INVOICE)), ARCHIVE);

        assertThat(snapshot()).isEqualTo(before);
        assertThat(path(MAIN + "archive")).doesNotExist();
        assertThat(report.renamed()).containsExactly(new Renamed(path(INVOICE), path(MAIN + "archive/Invoice.java")));
        assertThat(report.diff())
                .contains("--- " + path(INVOICE))
                .contains("+++ " + path(MAIN + "archive/Invoice.java"))
                .contains("+import demo.archive.Invoice;");
    }

    @Test
    void givesTheSameReportOnADryRunAndOnTheRealRun() throws IOException {
        project(PackageMoveScenarios.MOVE_A_CLASS_TO_A_NEW_PACKAGE);

        ChangeReport planned = move.dryRun(List.of(path(INVOICE)), ARCHIVE);
        ChangeReport done = move.apply(List.of(path(INVOICE)), ARCHIVE);

        assertThat(done).isEqualTo(planned);
    }

    private void assertRefused(Scenario scenario, LanguageServerErrorKey key) throws IOException {
        project(scenario);
        Map<String, String> before = snapshot();
        var request = (Request.MoveToPackage) scenario.request();
        List<Path> files = request.files().stream()
                .map(file -> root.resolve(file).normalize())
                .toList();

        assertThatExceptionOfType(LanguageServerException.class)
                .isThrownBy(() -> move.apply(files, request.target()))
                .satisfies(exception -> assertThat(exception.key()).isEqualTo(key));
        assertThatExceptionOfType(LanguageServerException.class)
                .isThrownBy(() -> move.dryRun(files, request.target()))
                .satisfies(exception -> assertThat(exception.key()).isEqualTo(key));

        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void refusesAClassThatIsAlreadyInThePackageAndWritesNothing() throws IOException {
        assertRefused(
                PackageMoveScenarios.MOVE_A_CLASS_TO_THE_PACKAGE_IT_IS_IN,
                LanguageServerErrorKey.NOT_MOVABLE_TO_PACKAGE);
    }

    @Test
    void refusesAClassWhoseFileNameIsTakenInThePackageAndOverwritesNothing() throws IOException {
        assertRefused(
                PackageMoveScenarios.MOVE_A_CLASS_TO_A_PACKAGE_THAT_HAS_A_FILE_OF_ITS_NAME,
                LanguageServerErrorKey.NOT_MOVABLE_TO_PACKAGE);
    }

    @Test
    void refusesAFileThatIsNotAJavaSource() throws IOException {
        assertRefused(
                PackageMoveScenarios.MOVE_A_FILE_THAT_IS_NOT_A_JAVA_SOURCE,
                LanguageServerErrorKey.NOT_MOVABLE_TO_PACKAGE);
    }

    @Test
    void refusesFilesOfTwoSourceFolders() throws IOException {
        assertRefused(
                PackageMoveScenarios.MOVE_FILES_OF_TWO_SOURCE_FOLDERS, LanguageServerErrorKey.NOT_MOVABLE_TO_PACKAGE);
    }

    @Test
    void refusesAJavaFileOfNoSourceFolder() throws IOException {
        assertRefused(
                PackageMoveScenarios.MOVE_A_FILE_OF_NO_SOURCE_FOLDER, LanguageServerErrorKey.NOT_MOVABLE_TO_PACKAGE);
    }

    @Test
    void refusesAFileThatDoesNotExist() throws IOException {
        assertRefused(PackageMoveScenarios.MOVE_A_FILE_THAT_DOES_NOT_EXIST, LanguageServerErrorKey.FILE_MISSING);
    }

    @Test
    void refusesAFolder() throws IOException {
        assertRefused(PackageMoveScenarios.MOVE_A_FOLDER, LanguageServerErrorKey.NOT_A_FILE);
    }

    @Test
    void refusesAFileOutsideTheProject() throws IOException {
        assertRefused(
                PackageMoveScenarios.MOVE_A_FILE_OUTSIDE_THE_PROJECT, LanguageServerErrorKey.FILE_OUTSIDE_PROJECT);
    }

    @Test
    void reportsAServerThatIsNotReady() throws IOException {
        assertRefused(PackageMoveScenarios.MOVE_WHILE_THE_SERVER_IMPORTS, LanguageServerErrorKey.NOT_READY);
    }

    @Test
    void rejectsMissingCollaborators() {
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> new MoveToPackage(null, new NioFileStore()))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("server"));
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> new MoveToPackage(new FakeJavaLanguageServer(root), null))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("store"));
    }

    @Test
    void rejectsMissingArguments() throws IOException {
        project(PackageMoveScenarios.MOVE_A_CLASS_TO_AN_EXISTING_PACKAGE);

        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> move.apply(null, BILLING))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("files"));
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> move.dryRun(List.of(path(INVOICE)), null))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("target"));
    }
}
