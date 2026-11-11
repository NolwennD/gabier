package eu.gabier.editing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import eu.gabier.editing.domain.ChangeReport;
import eu.gabier.editing.domain.EditingErrorKey;
import eu.gabier.editing.domain.EditingException;
import eu.gabier.editing.domain.LanguageServerErrorKey;
import eu.gabier.editing.domain.LanguageServerException;
import eu.gabier.editing.fixtures.FakeJavaLanguageServer;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CreatePackageInfoTest {

    private static final String DEMO = "src/main/java/demo/package-info.java";

    @TempDir
    Path root;

    private FakeJavaLanguageServer server;
    private CreatePackageInfo create;

    /** Writes the project of the scenarios and plays them with a fake scripted by them. */
    private void project(Scenario... scenarios) throws IOException {
        server = new FakeJavaLanguageServer(root);
        for (Scenario scenario : scenarios) {
            scenario.writeProject(root);
            server.scripted(scenario);
        }
        create = new CreatePackageInfo(server, new NioFileStore());
    }

    private Path path(String relative) {
        return root.resolve(relative);
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
    void writesThePackageLineOfItsFolderUnderTheNullMarkedAnnotationAndNothingElse() throws IOException {
        project(Scenarios.CREATE_A_PACKAGE_INFO);

        ChangeReport report = create.apply(path(DEMO));

        assertThat(path(DEMO)).content().isEqualTo("""
            @org.jspecify.annotations.NullMarked
            package demo;
            """);
        assertThat(report.created()).containsExactly(path(DEMO));
        assertThat(report.modified()).isEmpty();
        assertThat(report.deleted()).isEmpty();
    }

    @Test
    void givesAFileOfASubpackageTheNameOfThatPackage() throws IOException {
        project(Scenarios.CREATE_A_PACKAGE_INFO_IN_A_SUBPACKAGE);

        create.apply(path("src/main/java/demo/app/package-info.java"));

        assertThat(path("src/main/java/demo/app/package-info.java")).content().endsWith("package demo.app;\n");
    }

    @Test
    void asksTheServerOnceForTheFileAsGiven() throws IOException {
        project(Scenarios.CREATE_A_PACKAGE_INFO);

        create.apply(path(DEMO));

        assertThat(server.asked()).containsExactly(new Request.CreatePackageInfo(path(DEMO)));
    }

    @Test
    void writesNothingOnADryRunAndShowsTheFileAndItsDiff() throws IOException {
        project(Scenarios.CREATE_A_PACKAGE_INFO);
        Map<String, String> before = snapshot();

        ChangeReport report = create.dryRun(path(DEMO));

        assertThat(snapshot()).isEqualTo(before);
        assertThat(report.created()).containsExactly(path(DEMO));
        assertThat(report.diff())
                .contains("--- /dev/null")
                .contains("+++ " + path(DEMO))
                .contains("+@org.jspecify.annotations.NullMarked")
                .contains("+package demo;");
    }

    @Test
    void makesTheFolderOfAFileInANewPackageButNotOnADryRun() throws IOException {
        project(Scenarios.CREATE_A_PACKAGE_INFO_IN_A_NEW_PACKAGE);
        Path fresh = path("src/main/java/fresh/package-info.java");
        Map<String, String> before = snapshot();

        create.dryRun(fresh);

        assertThat(snapshot()).isEqualTo(before);
        assertThat(fresh.getParent()).doesNotExist();

        ChangeReport report = create.apply(fresh);

        assertThat(report.created()).containsExactly(fresh);
        assertThat(fresh).content().endsWith("package fresh;\n");
    }

    @Test
    void refusesAFileThatExistsAndLeavesItAsItWas() throws IOException {
        project(Scenarios.CREATE_A_PACKAGE_INFO);
        Files.writeString(path(DEMO), "package demo;\n");
        Map<String, String> before = snapshot();

        assertThatExceptionOfType(EditingException.class)
                .isThrownBy(() -> create.apply(path(DEMO)))
                .satisfies(exception -> assertThat(exception.key()).isEqualTo(EditingErrorKey.FILE_EXISTS));
        assertThatExceptionOfType(EditingException.class)
                .isThrownBy(() -> create.dryRun(path(DEMO)))
                .satisfies(exception -> assertThat(exception.key()).isEqualTo(EditingErrorKey.FILE_EXISTS));
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void refusesAFileNotNamedPackageInfoBeforeAskingTheServer() throws IOException {
        project(Scenarios.CREATE_A_PACKAGE_INFO);
        Map<String, String> before = snapshot();

        assertThatExceptionOfType(EditingException.class)
                .isThrownBy(() -> create.apply(path("src/main/java/demo/Widget.java")))
                .satisfies(exception -> assertThat(exception.key()).isEqualTo(EditingErrorKey.NOT_A_PACKAGE_INFO));
        assertThatExceptionOfType(EditingException.class)
                .isThrownBy(() -> create.dryRun(path("src/main/java/demo/info.java")))
                .satisfies(exception -> assertThat(exception.key()).isEqualTo(EditingErrorKey.NOT_A_PACKAGE_INFO));
        assertThat(server.asked()).isEmpty();
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void refusesAFileInTheDefaultPackageAsTheServerDoes() throws IOException {
        project(Scenarios.CREATE_A_PACKAGE_INFO_IN_THE_DEFAULT_PACKAGE);
        Map<String, String> before = snapshot();

        assertThatExceptionOfType(LanguageServerException.class)
                .isThrownBy(() -> create.apply(path("src/main/java/package-info.java")))
                .satisfies(exception -> assertThat(exception.key()).isEqualTo(LanguageServerErrorKey.NOT_CREATABLE));
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void refusesAFileOutsideTheSourceFoldersAsTheServerDoes() throws IOException {
        project(Scenarios.CREATE_A_PACKAGE_INFO_OUTSIDE_THE_SOURCE_FOLDERS);
        Map<String, String> before = snapshot();

        assertThatExceptionOfType(LanguageServerException.class)
                .isThrownBy(() -> create.dryRun(path("docs/package-info.java")))
                .satisfies(exception -> assertThat(exception.key()).isEqualTo(LanguageServerErrorKey.NOT_CREATABLE));
        assertThat(snapshot()).isEqualTo(before);
        assertThat(path("docs")).doesNotExist();
    }

    @Test
    void refusesAFileOutsideTheProjectAsTheServerDoes() throws IOException {
        project(Scenarios.CREATE_A_PACKAGE_INFO_OUTSIDE_THE_PROJECT);
        Map<String, String> before = snapshot();

        assertThatExceptionOfType(LanguageServerException.class)
                .isThrownBy(() -> create.apply(path("../outside/package-info.java")))
                .satisfies(exception ->
                        assertThat(exception.key()).isEqualTo(LanguageServerErrorKey.FILE_OUTSIDE_PROJECT));
        assertThat(snapshot()).isEqualTo(before);
        assertThat(path("../outside")).doesNotExist();
    }

    @Test
    void refusesWhileTheServerImportsTheProject() throws IOException {
        project(Scenarios.CREATE_A_PACKAGE_INFO_WHILE_THE_SERVER_IMPORTS);

        assertThatExceptionOfType(LanguageServerException.class)
                .isThrownBy(() -> create.dryRun(path(DEMO)))
                .satisfies(exception -> assertThat(exception.key()).isEqualTo(LanguageServerErrorKey.NOT_READY));
    }

    @Test
    void requiresTheFileTheServerAndTheStore() throws IOException {
        project(Scenarios.CREATE_A_PACKAGE_INFO);

        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> create.apply(null))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("file"));
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> new CreatePackageInfo(null, new NioFileStore()))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("server"));
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> new CreatePackageInfo(server, null))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("store"));
    }
}
