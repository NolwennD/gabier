package eu.gabier.editing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import eu.gabier.editing.application.RenameTarget.At;
import eu.gabier.editing.application.RenameTarget.Token;
import eu.gabier.editing.domain.ChangeReport;
import eu.gabier.editing.domain.ChangeReport.Renamed;
import eu.gabier.editing.domain.Diagnostic;
import eu.gabier.editing.domain.EditingErrorKey;
import eu.gabier.editing.domain.EditingException;
import eu.gabier.editing.domain.LanguageServerErrorKey;
import eu.gabier.editing.domain.LanguageServerException;
import eu.gabier.editing.domain.Position;
import eu.gabier.editing.fixtures.FakeJavaLanguageServer;
import eu.gabier.editing.fixtures.PackageRenameScenarios;
import eu.gabier.editing.fixtures.RecordRenameScenarios;
import eu.gabier.editing.fixtures.Scenario;
import eu.gabier.editing.fixtures.Scenario.Request;
import eu.gabier.editing.fixtures.Scenarios;
import eu.gabier.editing.infrastructure.secondary.NioFileStore;
import eu.gabier.shared.error.domain.MissingMandatoryValueException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RenameTest {

    private static final String GREETER = "src/main/java/demo/Greeter.java";
    private static final String WELCOMER = "src/main/java/demo/Welcomer.java";
    private static final String MAIN = "src/main/java/demo/Main.java";

    @TempDir
    Path root;

    private FakeJavaLanguageServer server;
    private Rename rename;

    /** Writes the project of the scenario and plays it with a fake scripted by the scenario. */
    private void project(Scenario... scenarios) throws IOException {
        server = new FakeJavaLanguageServer(root);
        for (Scenario scenario : scenarios) {
            scenario.writeProject(root);
            server.scripted(scenario);
        }
        rename = new Rename(server, new NioFileStore());
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

    @Nested
    class ByPosition {

        @Test
        void editsTheFileForALocalVariable() throws IOException {
            project(Scenarios.RENAME_LOCAL_VARIABLE);

            ChangeReport report = rename.apply(new At(path(GREETER), new Position(6, 15)), "text");

            assertThat(text(GREETER)).isEqualTo("""
                package demo;

                public class Greeter {
                    private String greeting = "Hello";

                    public String greet(String name) {
                        String text = greeting + ", " + name;
                        return text;
                    }
                }
                """);
            assertThat(report.modified()).containsExactly(path(GREETER));
        }

        @Test
        void editsEveryFileThatUsesAMethod() throws IOException {
            project(Scenarios.RENAME_METHOD);

            ChangeReport report = rename.apply(new At(path(GREETER), new Position(5, 18)), "welcome");

            assertThat(text(GREETER)).contains("public String welcome(String name) {");
            assertThat(text(MAIN)).contains("System.out.println(greeter.welcome(\"world\"));");
            assertThat(report.modified()).containsExactly(path(GREETER), path(MAIN));
        }

        @Test
        void editsAFieldAndItsUse() throws IOException {
            project(Scenarios.RENAME_FIELD);

            rename.apply(new At(path(GREETER), new Position(3, 19)), "salutation");

            assertThat(text(GREETER))
                    .contains("private String salutation = \"Hello\";")
                    .contains("String message = salutation + \", \" + name;");
        }

        @Test
        void movesTheFileOfARenamedType() throws IOException {
            project(Scenarios.RENAME_TYPE);

            ChangeReport report = rename.apply(new At(path(GREETER), new Position(2, 13)), "Welcomer");

            assertThat(path(GREETER)).doesNotExist();
            assertThat(text(WELCOMER)).contains("public class Welcomer {");
            assertThat(text(MAIN)).contains("Welcomer greeter = new Welcomer();");
            assertThat(report.renamed()).containsExactly(new Renamed(path(GREETER), path(WELCOMER)));
        }

        @Test
        void asksTheServerOnceWithTheRequestAsGiven() throws IOException {
            project(Scenarios.RENAME_LOCAL_VARIABLE);

            rename.apply(new At(path(GREETER), new Position(6, 15)), "text");

            assertThat(server.asked()).containsExactly(new Request.Rename(path(GREETER), new Position(6, 15), "text"));
        }

        @Test
        void writesNothingOnADryRunAndGivesTheDiff() throws IOException {
            project(Scenarios.RENAME_METHOD);
            Map<String, String> before = snapshot();

            ChangeReport report = rename.dryRun(new At(path(GREETER), new Position(5, 18)), "welcome");

            assertThat(snapshot()).isEqualTo(before);
            assertThat(report.modified()).containsExactly(path(GREETER), path(MAIN));
            assertThat(report.diff())
                    .contains("-    public String greet(String name) {")
                    .contains("+    public String welcome(String name) {")
                    .contains("+        System.out.println(greeter.welcome(\"world\"));");
            assertThat(server.asked()).hasSize(1);
        }

        @Test
        void givesTheSameReportOnADryRunAndOnTheRealRun() throws IOException {
            project(Scenarios.RENAME_TYPE);
            At target = new At(path(GREETER), new Position(2, 13));

            ChangeReport planned = rename.dryRun(target, "Welcomer");
            ChangeReport done = rename.apply(target, "Welcomer");

            assertThat(done).isEqualTo(planned);
        }
    }

    @Nested
    class OfAPackage {

        private static final String APP = "src/main/java/demo/app/";
        private static final String SHOP = "src/main/java/demo/shop/";
        private static final String RULER = "src/main/java/legacy/tools/Ruler.java";

        private void renameApp() {
            rename.apply(new At(path(APP + "Greeter.java"), new Position(0, 13)), "demo.shop");
        }

        @Test
        void givesItsFilesTheNewPackageLineAndTheFolderOfTheNewNameAndUpdatesTheirUsers() throws IOException {
            project(PackageRenameScenarios.RENAME_A_PACKAGE);

            ChangeReport report = rename.apply(new At(path(APP + "Greeter.java"), new Position(0, 13)), "demo.shop");

            assertThat(text(SHOP + "Greeter.java")).startsWith("package demo.shop;\n");
            assertThat(text(SHOP + "Farewell.java")).startsWith("package demo.shop;\n");
            assertThat(path(APP + "Greeter.java")).doesNotExist();
            assertThat(path(APP + "Farewell.java")).doesNotExist();
            assertThat(text("src/main/java/demo/other/Main.java"))
                    .contains("import demo.shop.Farewell;\nimport demo.shop.Greeter;\n");
            assertThat(report.renamed())
                    .containsExactly(
                            new Renamed(path(APP + "Greeter.java"), path(SHOP + "Greeter.java")),
                            new Renamed(path(APP + "Farewell.java"), path(SHOP + "Farewell.java")));
            assertThat(report.created()).isEmpty();
            assertThat(report.deleted()).isEmpty();
        }

        @Test
        void leavesTheSubPackageAndThePackageOfTheOtherSourceSetWhereTheyAre() throws IOException {
            project(PackageRenameScenarios.RENAME_A_PACKAGE);

            renameApp();

            assertThat(text(APP + "sub/Helper.java"))
                    .startsWith("package demo.app.sub;\n")
                    .contains("import demo.shop.Greeter;");
            assertThat(text("src/test/java/demo/app/GreeterTest.java"))
                    .startsWith("package demo.app;\n")
                    .contains("import demo.shop.Greeter;");
            assertThat(path(APP + "sub")).isDirectory();
        }

        @Test
        void removesTheFolderTheRenameEmptiesAndItsEmptyParentsUpToTheSourceFolder() throws IOException {
            project(PackageRenameScenarios.RENAME_A_PACKAGE_UP_TO_THE_SOURCE_FOLDER);

            rename.apply(new At(path(RULER), new Position(0, 18)), "tools");

            assertThat(text("src/main/java/tools/Ruler.java")).startsWith("package tools;\n");
            assertThat(path("src/main/java/legacy")).doesNotExist();
            assertThat(path("src/main/java")).isDirectory();
        }

        @Test
        void keepsTheOldFolderThatStillHoldsAFile() throws IOException {
            project(PackageRenameScenarios.RENAME_A_PACKAGE);

            renameApp();

            assertThat(path(APP)).isDirectory();
        }

        @Test
        void writesNothingOnADryRunAndRemovesNoFolder() throws IOException {
            project(PackageRenameScenarios.RENAME_A_PACKAGE_UP_TO_THE_SOURCE_FOLDER);
            Map<String, String> before = snapshot();

            ChangeReport report = rename.dryRun(new At(path(RULER), new Position(0, 18)), "tools");

            assertThat(snapshot()).isEqualTo(before);
            assertThat(report.renamed())
                    .containsExactly(new Renamed(path(RULER), path("src/main/java/tools/Ruler.java")));
        }

        @Test
        void givesTheSameReportOnADryRunAndOnTheRealRun() throws IOException {
            project(PackageRenameScenarios.RENAME_A_PACKAGE_UP_TO_THE_SOURCE_FOLDER);
            At target = new At(path(RULER), new Position(0, 18));

            ChangeReport planned = rename.dryRun(target, "tools");
            ChangeReport done = rename.apply(target, "tools");

            assertThat(done).isEqualTo(planned);
        }

        @Test
        void removesNoFolderWhenATypeIsRenamedInPlace() throws IOException {
            project(Scenarios.RENAME_TYPE);

            rename.apply(new At(path(GREETER), new Position(2, 13)), "Welcomer");

            assertThat(path("src/main/java/demo")).isDirectory();
        }

        /** The two calls, the main sources then the test sources, applied one after the other. */
        private void renameInTheMainAndInTheTestSources() throws IOException {
            project(PackageRenameScenarios.RENAME_A_PACKAGE);
            server.scripted(PackageRenameScenarios.RENAME_THE_SAME_PACKAGE_OF_THE_TESTS)
                    .scripted(PackageRenameScenarios.DIAGNOSTICS_AFTER_RENAMING_A_PACKAGE_IN_BOTH_SOURCE_SETS)
                    .scripted(PackageRenameScenarios.ORGANIZE_IMPORTS_AFTER_RENAMING_A_PACKAGE_IN_BOTH_SOURCE_SETS);
            renameApp();
            rename.apply(new At(path("src/test/java/demo/app/GreeterTest.java"), new Position(0, 13)), "demo.shop");
        }

        private Map<Path, String> sources() throws IOException {
            Map<Path, String> sources = new TreeMap<>();
            try (Stream<Path> paths = Files.walk(root)) {
                for (Path file : paths.filter(Files::isRegularFile).toList()) {
                    sources.put(root.relativize(file), Files.readString(file));
                }
            }

            return sources;
        }

        @Test
        void givesTheProjectTheDiagnosticsScenarioStartsFromOnceThePackageIsRenamedInTheMainAndInTheTestSources()
                throws IOException {
            renameInTheMainAndInTheTestSources();

            assertThat(sources()).isEqualTo(new TreeMap<>(PackageRenameScenarios.afterTheTwoCalls()));
            assertThat(path("src/test/java/demo/app")).doesNotExist();
            assertThat(path(APP + "Greeter.java")).doesNotExist();
        }

        @Test
        void reportsNoErrorOnTheProjectOnceThePackageIsRenamedInTheMainAndInTheTestSources() throws IOException {
            renameInTheMainAndInTheTestSources();

            assertThat(new Diagnose(server).diagnose(path("src/test/java/demo/shop/GreeterTest.java")))
                    .extracting(Diagnostic::severity)
                    .containsExactly(Diagnostic.Severity.WARNING);
        }

        @Test
        void removesTheImportThatTheTwoCallsLeaveWithTheOrganizationOfTheImports() throws IOException {
            renameInTheMainAndInTheTestSources();

            new OrganizeImports(server, new NioFileStore()).apply(path("src/test/java/demo/shop/GreeterTest.java"));

            assertThat(text("src/test/java/demo/shop/GreeterTest.java")).isEqualTo("""
                package demo.shop;

                class GreeterTest {
                    String run() {
                        return new Greeter().greet();
                    }
                }
                """);
        }

        private void assertRefused(Scenario scenario, String message) throws IOException {
            project(scenario);
            Map<String, String> before = snapshot();
            Request.Rename request = (Request.Rename) scenario.request();
            At target = new At(root.resolve(request.file()).normalize(), request.position());

            assertThatExceptionOfType(LanguageServerException.class)
                    .isThrownBy(() -> rename.apply(target, request.newName()))
                    .satisfies(exception -> assertThat(exception.key()).isEqualTo(LanguageServerErrorKey.INVALID_NAME))
                    .withMessage(message.replace("{root}", root.toString()));

            assertThat(snapshot()).isEqualTo(before);
        }

        @Test
        void refusesANameThatIsNotAPackageName() throws IOException {
            assertRefused(
                    PackageRenameScenarios.RENAME_A_PACKAGE_TO_AN_INVALID_NAME,
                    "\"demo.1st\" is not a valid name for the symbol at 0:13 in {root}/" + APP + "Greeter.java");
        }

        @Test
        void saysWhichPackageHoldsNoClassWhenThePositionIsOnASegmentThatNamesOne() throws IOException {
            Scenario scenario = PackageRenameScenarios.RENAME_A_PACKAGE_THAT_HOLDS_NO_CLASS;
            project(scenario);
            Map<String, String> before = snapshot();
            Request.Rename request = (Request.Rename) scenario.request();
            At target = new At(root.resolve(request.file()).normalize(), request.position());

            assertThatExceptionOfType(LanguageServerException.class)
                    .isThrownBy(() -> rename.dryRun(target, request.newName()))
                    .satisfies(exception -> {
                        assertThat(exception.key()).isEqualTo(LanguageServerErrorKey.EMPTY_PACKAGE);
                        assertThat(exception.parameters())
                                .containsEntry("package", "legacy")
                                .containsEntry("declaredPackage", "legacy.tools");
                    });

            assertThat(snapshot()).isEqualTo(before);
        }

        @Test
        void refusesAComponentOfARecordWhereTheServerCannotRenameItAndWritesNothing() throws IOException {
            Scenario scenario = RecordRenameScenarios.RENAME_A_COMPONENT_OF_A_RECORD_WITH_A_COMPACT_CONSTRUCTOR;
            project(scenario);
            Map<String, String> before = snapshot();
            Request.Rename request = (Request.Rename) scenario.request();
            At target = new At(root.resolve(request.file()).normalize(), request.position());

            assertThatExceptionOfType(LanguageServerException.class)
                    .isThrownBy(() -> rename.apply(target, request.newName()))
                    .satisfies(exception ->
                            assertThat(exception.key()).isEqualTo(LanguageServerErrorKey.RECORD_COMPONENT));

            assertThat(snapshot()).isEqualTo(before);
        }

        @Test
        void refusesAPackageThatExists() throws IOException {
            assertRefused(
                    PackageRenameScenarios.RENAME_A_PACKAGE_TO_A_PACKAGE_THAT_EXISTS,
                    "\"demo.other\" is not a valid name for the symbol at 0:13 in {root}/" + APP + "Greeter.java");
        }
    }

    @Nested
    class ByToken {

        @Test
        void renamesTheSymbolOfTheFirstOccurrenceAsAWholeWord() throws IOException {
            project(Scenarios.RENAME_METHOD);

            rename.apply(new Token(path(GREETER), "greet"), "welcome");

            // `greeting` comes first in the file but is another word: the position is the one of the method.
            assertThat(server.asked())
                    .containsExactly(new Request.Rename(path(GREETER), new Position(5, 18), "welcome"));
            assertThat(text(GREETER)).contains("public String welcome(String name) {");
            assertThat(text(MAIN)).contains("greeter.welcome(\"world\")");
        }

        @Test
        void writesNothingOnADryRun() throws IOException {
            project(Scenarios.RENAME_LOCAL_VARIABLE);
            Map<String, String> before = snapshot();

            ChangeReport report = rename.dryRun(new Token(path(GREETER), "message"), "text");

            assertThat(snapshot()).isEqualTo(before);
            assertThat(report.modified()).containsExactly(path(GREETER));
        }

        @Test
        void refusesAnIdentifierThatIsOnlyPartOfOtherWordsWithoutAskingTheServer() throws IOException {
            project(Scenarios.RENAME_TYPE);
            Map<String, String> before = snapshot();

            assertThatExceptionOfType(EditingException.class)
                    .isThrownBy(() -> rename.apply(new Token(path(GREETER), "Greete"), "Welcomer"))
                    .satisfies(exception -> assertThat(exception.key()).isEqualTo(EditingErrorKey.TOKEN_NOT_FOUND))
                    .withMessage("\"Greete\" does not occur as a whole word in " + path(GREETER));

            assertThat(server.asked()).isEmpty();
            assertThat(snapshot()).isEqualTo(before);
        }

        @Test
        void refusesAMissingFileWithoutAskingTheServer() throws IOException {
            project(Scenarios.RENAME_TYPE);

            assertThatExceptionOfType(EditingException.class)
                    .isThrownBy(() -> rename.apply(new Token(path("src/Missing.java"), "Greeter"), "Welcomer"))
                    .satisfies(exception -> assertThat(exception.key()).isEqualTo(EditingErrorKey.FILE_MISSING));

            assertThat(server.asked()).isEmpty();
        }
    }

    @Nested
    class Refusals {

        private void assertRefused(Scenario scenario, LanguageServerErrorKey key, String message) throws IOException {
            project(scenario);
            Map<String, String> before = snapshot();
            Request.Rename request = (Request.Rename) scenario.request();
            At target = new At(root.resolve(request.file()).normalize(), request.position());

            assertThatExceptionOfType(LanguageServerException.class)
                    .isThrownBy(() -> rename.apply(target, request.newName()))
                    .satisfies(exception -> assertThat(exception.key()).isEqualTo(key))
                    .withMessage(message.replace("{root}", root.toString()));
            assertThatExceptionOfType(LanguageServerException.class)
                    .isThrownBy(() -> rename.dryRun(target, request.newName()))
                    .satisfies(exception -> assertThat(exception.key()).isEqualTo(key));

            assertThat(snapshot()).isEqualTo(before);
        }

        @Test
        void reportsAServerThatIsNotReady() throws IOException {
            assertRefused(
                    Scenarios.RENAME_WHILE_THE_SERVER_IMPORTS,
                    LanguageServerErrorKey.NOT_READY,
                    "The language server is still importing the project: try again later");
        }

        @Test
        void reportsAPositionWithoutSymbol() throws IOException {
            assertRefused(
                    Scenarios.RENAME_WHERE_THERE_IS_NO_SYMBOL,
                    LanguageServerErrorKey.NO_SUCH_SYMBOL,
                    "There is no symbol at 4:0 in {root}/" + GREETER);
        }

        @Test
        void reportsAnInvalidName() throws IOException {
            assertRefused(
                    Scenarios.RENAME_TO_AN_INVALID_NAME,
                    LanguageServerErrorKey.INVALID_NAME,
                    "\"1st\" is not a valid name for the symbol at 6:15 in {root}/" + GREETER);
        }

        @Test
        void reportsAFileOutsideTheProject() throws IOException {
            assertRefused(
                    Scenarios.RENAME_OUTSIDE_THE_PROJECT,
                    LanguageServerErrorKey.FILE_OUTSIDE_PROJECT,
                    "The file " + root.getParent().resolve("outside/Elsewhere.java")
                            + " is not in the project of the language server");
        }
    }

    @Test
    void rejectsMissingCollaborators() {
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> new Rename(null, new NioFileStore()))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("server"));
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> new Rename(new FakeJavaLanguageServer(root), null))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("store"));
    }

    @Test
    void rejectsAMissingTargetAndAMissingName() throws IOException {
        project(Scenarios.RENAME_LOCAL_VARIABLE);
        At target = new At(path(GREETER), new Position(6, 15));

        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> rename.apply(null, "text"))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("target"));
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> rename.dryRun(target, null))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("newName"));
    }
}
