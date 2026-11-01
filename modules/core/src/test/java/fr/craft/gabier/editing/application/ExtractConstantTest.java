package fr.craft.gabier.editing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import fr.craft.gabier.editing.domain.ChangeReport;
import fr.craft.gabier.editing.domain.LanguageServerErrorKey;
import fr.craft.gabier.editing.domain.LanguageServerException;
import fr.craft.gabier.editing.domain.Position;
import fr.craft.gabier.editing.domain.Range;
import fr.craft.gabier.editing.fixtures.FakeJavaLanguageServer;
import fr.craft.gabier.editing.fixtures.Scenario;
import fr.craft.gabier.editing.fixtures.Scenario.Request;
import fr.craft.gabier.editing.fixtures.Scenarios;
import fr.craft.gabier.editing.infrastructure.secondary.NioFileStore;
import fr.craft.gabier.shared.error.domain.MissingMandatoryValueException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExtractConstantTest {

    private static final String GREETER = "src/main/java/demo/Greeter.java";
    private static final String TOTALS = "src/main/java/demo/Totals.java";
    private static final Range LABEL = new Range(new Position(5, 27), new Position(5, 36));

    @TempDir
    Path root;

    private FakeJavaLanguageServer server;
    private ExtractConstant extract;

    /** Writes the project of the scenarios and plays them with a fake scripted by them. */
    private void project(Scenario... scenarios) throws IOException {
        server = new FakeJavaLanguageServer(root);
        for (Scenario scenario : scenarios) {
            scenario.writeProject(root);
            server.scripted(scenario);
        }
        extract = new ExtractConstant(server, new NioFileStore());
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
    void declaresTheConstantWithTheNameGivenAndUsesItInPlaceOfTheExpressionOnDisk() throws IOException {
        project(Scenarios.EXTRACT_CONSTANT);

        ChangeReport report = extract.apply(path(TOTALS), LABEL, "COUNT_LABEL");

        assertThat(text(TOTALS))
                .contains("    private static final String COUNT_LABEL = \"count: \";\n")
                .contains("        System.out.println(COUNT_LABEL + count);\n")
                .doesNotContain("System.out.println(\"count: \" + count);");
        assertThat(report.modified()).containsExactly(path(TOTALS));
        assertThat(report.created()).isEmpty();
        assertThat(report.renamed()).isEmpty();
        assertThat(report.deleted()).isEmpty();
    }

    @Test
    void asksTheServerOnceForTheFileTheSelectionAndTheNameAsGiven() throws IOException {
        project(Scenarios.EXTRACT_CONSTANT);

        extract.apply(path(TOTALS), LABEL, "COUNT_LABEL");

        assertThat(server.asked()).containsExactly(new Request.ExtractConstant(path(TOTALS), LABEL, "COUNT_LABEL"));
    }

    @Test
    void writesNothingOnADryRunAndGivesTheDiff() throws IOException {
        project(Scenarios.EXTRACT_CONSTANT);
        Map<String, String> before = snapshot();

        ChangeReport report = extract.dryRun(path(TOTALS), LABEL, "COUNT_LABEL");

        assertThat(snapshot()).isEqualTo(before);
        assertThat(report.modified()).containsExactly(path(TOTALS));
        assertThat(report.diff())
                .contains("+    private static final String COUNT_LABEL = \"count: \";")
                .contains("+        System.out.println(COUNT_LABEL + count);")
                .contains("-        System.out.println(\"count: \" + count);");
    }

    @Test
    void givesTheSameReportOnADryRunAndOnTheRealRun() throws IOException {
        project(Scenarios.EXTRACT_CONSTANT);

        ChangeReport planned = extract.dryRun(path(TOTALS), LABEL, "COUNT_LABEL");
        ChangeReport done = extract.apply(path(TOTALS), LABEL, "COUNT_LABEL");

        assertThat(done).isEqualTo(planned);
    }

    private void assertRefused(Scenario scenario, LanguageServerErrorKey key, String message) throws IOException {
        project(scenario);
        Map<String, String> before = snapshot();
        var request = (Request.ExtractConstant) scenario.request();
        Path file = root.resolve(request.file()).normalize();

        assertThatExceptionOfType(LanguageServerException.class)
                .isThrownBy(() -> extract.apply(file, request.selection(), request.name()))
                .satisfies(exception -> assertThat(exception.key()).isEqualTo(key))
                .withMessage(message.replace("{root}", root.toString()));
        assertThatExceptionOfType(LanguageServerException.class)
                .isThrownBy(() -> extract.dryRun(file, request.selection(), request.name()))
                .satisfies(exception -> assertThat(exception.key()).isEqualTo(key));

        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void refusesAKeyword() throws IOException {
        assertRefused(
                Scenarios.EXTRACT_CONSTANT_FROM_SOMETHING_THAT_IS_NOT_AN_EXPRESSION,
                LanguageServerErrorKey.NOT_EXTRACTABLE,
                "The selection 7:8-7:14 of {root}/" + GREETER
                        + " cannot be extracted: it is not an expression or statements that can be extracted here");
    }

    @Test
    void refusesAnExpressionThatReadsALocalVariable() throws IOException {
        assertRefused(
                Scenarios.EXTRACT_CONSTANT_THAT_USES_A_LOCAL_VARIABLE,
                LanguageServerErrorKey.NOT_EXTRACTABLE,
                "The selection 5:27-5:44 of {root}/" + TOTALS
                        + " cannot be extracted: it is not an expression or statements that can be extracted here");
    }

    @Test
    void refusesANameThatIsNotAnIdentifier() throws IOException {
        assertRefused(
                Scenarios.EXTRACT_CONSTANT_TO_AN_INVALID_NAME,
                LanguageServerErrorKey.INVALID_NAME,
                "\"1ST\" is not a valid name for the symbol at 5:27 in {root}/" + TOTALS);
    }

    @Test
    void reportsAServerThatIsNotReady() throws IOException {
        assertRefused(
                Scenarios.EXTRACT_CONSTANT_WHILE_THE_SERVER_IMPORTS,
                LanguageServerErrorKey.NOT_READY,
                "The language server is still importing the project: try again later");
    }

    @Test
    void reportsAFileOutsideTheProject() throws IOException {
        assertRefused(
                Scenarios.EXTRACT_CONSTANT_OUTSIDE_THE_PROJECT,
                LanguageServerErrorKey.FILE_OUTSIDE_PROJECT,
                "The file " + root.getParent().resolve("outside/Elsewhere.java")
                        + " is not in the project of the language server");
    }

    @Test
    void rejectsMissingCollaborators() {
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> new ExtractConstant(null, new NioFileStore()))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("server"));
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> new ExtractConstant(new FakeJavaLanguageServer(root), null))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("store"));
    }

    @Test
    void rejectsMissingArguments() throws IOException {
        project(Scenarios.EXTRACT_CONSTANT);

        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> extract.apply(null, LABEL, "COUNT_LABEL"))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("file"));
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> extract.dryRun(path(TOTALS), null, "COUNT_LABEL"))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("selection"));
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> extract.apply(path(TOTALS), LABEL, null))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("name"));
    }
}
