package fr.craft.gabier.editing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import fr.craft.gabier.editing.domain.ChangeReport;
import fr.craft.gabier.editing.domain.LanguageServerErrorKey;
import fr.craft.gabier.editing.domain.LanguageServerException;
import fr.craft.gabier.editing.fixtures.FakeJavaLanguageServer;
import fr.craft.gabier.editing.fixtures.Scenario;
import fr.craft.gabier.editing.fixtures.Scenarios;
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

class OrganizeImportsTest {

    private static final String UNTIDY = "src/main/java/demo/Untidy.java";
    private static final String TIDY = "src/main/java/demo/Tidy.java";

    /** What the project keeps: the static imports, a blank line, then every other import in one block. */
    private static final String ORGANIZED_IMPORTS = """
        import static java.util.Map.entry;
        import static java.util.stream.Collectors.toList;

        import java.io.File;
        import java.util.ArrayList;
        import java.util.Collections;
        import java.util.List;
        import java.util.Map;
        import javax.swing.JButton;
        import org.w3c.dom.Node;
        """;

    @TempDir
    Path root;

    private FakeJavaLanguageServer server;
    private OrganizeImports organize;

    /** Writes the project of the scenarios and plays them with a fake scripted by them. */
    private void project(Scenario... scenarios) throws IOException {
        server = new FakeJavaLanguageServer(root);
        for (Scenario scenario : scenarios) {
            scenario.writeProject(root);
            server.scripted(scenario);
        }
        organize = new OrganizeImports(server, new NioFileStore());
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
    void addsTheMissingImportsRemovesTheUnusedOnesAndSortsTheRestOnDisk() throws IOException {
        project(Scenarios.ORGANIZE_IMPORTS);

        ChangeReport report = organize.apply(path(UNTIDY));

        assertThat(text(UNTIDY)).contains(ORGANIZED_IMPORTS).doesNotContain("import java.util.Set;");
        assertThat(report.modified()).containsExactly(path(UNTIDY));
        assertThat(report.created()).isEmpty();
        assertThat(report.renamed()).isEmpty();
        assertThat(report.deleted()).isEmpty();
    }

    @Test
    void asksTheServerOnceForTheFileAsGiven() throws IOException {
        project(Scenarios.ORGANIZE_IMPORTS);

        organize.apply(path(UNTIDY));

        assertThat(server.asked()).hasSize(1);
        assertThat(server.asked().getFirst().file()).isEqualTo(path(UNTIDY));
    }

    @Test
    void writesNothingOnADryRunAndGivesTheDiff() throws IOException {
        project(Scenarios.ORGANIZE_IMPORTS);
        Map<String, String> before = snapshot();

        ChangeReport report = organize.dryRun(path(UNTIDY));

        assertThat(snapshot()).isEqualTo(before);
        assertThat(report.modified()).containsExactly(path(UNTIDY));
        assertThat(report.diff())
                .contains("-import java.util.Set;")
                .contains("+import java.util.ArrayList;")
                .contains("+import java.util.Collections;");
    }

    @Test
    void givesTheSameReportOnADryRunAndOnTheRealRun() throws IOException {
        project(Scenarios.ORGANIZE_IMPORTS);

        ChangeReport planned = organize.dryRun(path(UNTIDY));
        ChangeReport done = organize.apply(path(UNTIDY));

        assertThat(done).isEqualTo(planned);
    }

    @Test
    void changesNothingForAFileWhoseImportsAreInOrder() throws IOException {
        project(Scenarios.ORGANIZE_IMPORTS_IN_ORDER);
        Map<String, String> before = snapshot();

        ChangeReport applied = organize.apply(path(TIDY));
        ChangeReport planned = organize.dryRun(path(TIDY));

        assertThat(snapshot()).isEqualTo(before);
        assertThat(applied).isEqualTo(new ChangeReport(List.of(), List.of(), List.of(), List.of(), ""));
        assertThat(planned).isEqualTo(applied);
    }

    @Test
    void keepsTheExplicitImportsOfAFileThatImportsManyTypesOfOnePackage() throws IOException {
        project(Scenarios.ORGANIZE_IMPORTS_KEEPS_EXPLICIT_IMPORTS);
        Map<String, String> before = snapshot();

        ChangeReport report = organize.apply(path("src/main/java/demo/Wide.java"));

        assertThat(report.modified()).isEmpty();
        assertThat(snapshot()).isEqualTo(before);
    }

    private void assertRefused(Scenario scenario, LanguageServerErrorKey key, String message) throws IOException {
        project(scenario);
        Map<String, String> before = snapshot();
        Path file = root.resolve(scenario.request().file()).normalize();

        assertThatExceptionOfType(LanguageServerException.class)
                .isThrownBy(() -> organize.apply(file))
                .satisfies(exception -> assertThat(exception.key()).isEqualTo(key))
                .withMessage(message.replace("{root}", root.toString()));
        assertThatExceptionOfType(LanguageServerException.class)
                .isThrownBy(() -> organize.dryRun(file))
                .satisfies(exception -> assertThat(exception.key()).isEqualTo(key));

        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void reportsAServerThatIsNotReady() throws IOException {
        assertRefused(
                Scenarios.ORGANIZE_IMPORTS_WHILE_THE_SERVER_IMPORTS,
                LanguageServerErrorKey.NOT_READY,
                "The language server is still importing the project: try again later");
    }

    @Test
    void reportsAFileOutsideTheProject() throws IOException {
        assertRefused(
                Scenarios.ORGANIZE_IMPORTS_OUTSIDE_THE_PROJECT,
                LanguageServerErrorKey.FILE_OUTSIDE_PROJECT,
                "The file " + root.getParent().resolve("outside/Elsewhere.java")
                        + " is not in the project of the language server");
    }

    @Test
    void rejectsMissingCollaborators() {
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> new OrganizeImports(null, new NioFileStore()))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("server"));
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> new OrganizeImports(new FakeJavaLanguageServer(root), null))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("store"));
    }

    @Test
    void rejectsAMissingFile() throws IOException {
        project(Scenarios.ORGANIZE_IMPORTS);

        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> organize.apply(null))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("file"));
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> organize.dryRun(null))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("file"));
    }
}
