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

class ExtractVariableTest {

    private static final String GREETER = "src/main/java/demo/Greeter.java";
    private static final Range JOINED = new Range(new Position(6, 25), new Position(6, 47));

    @TempDir
    Path root;

    private FakeJavaLanguageServer server;
    private ExtractVariable extract;

    /** Writes the project of the scenarios and plays them with a fake scripted by them. */
    private void project(Scenario... scenarios) throws IOException {
        server = new FakeJavaLanguageServer(root);
        for (Scenario scenario : scenarios) {
            scenario.writeProject(root);
            server.scripted(scenario);
        }
        extract = new ExtractVariable(server, new NioFileStore());
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
    void declaresTheVariableWithTheNameGivenAndUsesItInPlaceOfTheExpressionOnDisk() throws IOException {
        project(Scenarios.EXTRACT_VARIABLE);

        ChangeReport report = extract.apply(path(GREETER), JOINED, "joined");

        assertThat(text(GREETER)).contains("""
            String joined = greeting + ", " + name;
            String message = joined;
            return message;
            """.indent(8).stripTrailing());
        assertThat(report.modified()).containsExactly(path(GREETER));
        assertThat(report.created()).isEmpty();
        assertThat(report.renamed()).isEmpty();
        assertThat(report.deleted()).isEmpty();
    }

    @Test
    void asksTheServerOnceForTheFileTheSelectionAndTheNameAsGiven() throws IOException {
        project(Scenarios.EXTRACT_VARIABLE);

        extract.apply(path(GREETER), JOINED, "joined");

        assertThat(server.asked()).containsExactly(new Request.ExtractVariable(path(GREETER), JOINED, "joined"));
    }

    @Test
    void writesNothingOnADryRunAndGivesTheDiff() throws IOException {
        project(Scenarios.EXTRACT_VARIABLE);
        Map<String, String> before = snapshot();

        ChangeReport report = extract.dryRun(path(GREETER), JOINED, "joined");

        assertThat(snapshot()).isEqualTo(before);
        assertThat(report.modified()).containsExactly(path(GREETER));
        assertThat(report.diff())
                .contains("-        String message = greeting + \", \" + name;")
                .contains("+        String joined = greeting + \", \" + name;")
                .contains("+        String message = joined;");
    }

    @Test
    void givesTheSameReportOnADryRunAndOnTheRealRun() throws IOException {
        project(Scenarios.EXTRACT_VARIABLE);

        ChangeReport planned = extract.dryRun(path(GREETER), JOINED, "joined");
        ChangeReport done = extract.apply(path(GREETER), JOINED, "joined");

        assertThat(done).isEqualTo(planned);
    }

    private void assertRefused(Scenario scenario, LanguageServerErrorKey key, String message) throws IOException {
        project(scenario);
        Map<String, String> before = snapshot();
        var request = (Request.ExtractVariable) scenario.request();
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
    void refusesASelectionThatIsNotAnExpression() throws IOException {
        assertRefused(
                Scenarios.EXTRACT_VARIABLE_FROM_SOMETHING_THAT_IS_NOT_AN_EXPRESSION,
                LanguageServerErrorKey.NOT_AN_EXPRESSION,
                "The selection 7:8-7:14 of {root}/" + GREETER + " is not an expression");
    }

    @Test
    void refusesANameThatIsNotAnIdentifier() throws IOException {
        assertRefused(
                Scenarios.EXTRACT_VARIABLE_TO_AN_INVALID_NAME,
                LanguageServerErrorKey.INVALID_NAME,
                "\"1st\" is not a valid name for the symbol at 6:25 in {root}/" + GREETER);
    }

    @Test
    void reportsAServerThatIsNotReady() throws IOException {
        assertRefused(
                Scenarios.EXTRACT_VARIABLE_WHILE_THE_SERVER_IMPORTS,
                LanguageServerErrorKey.NOT_READY,
                "The language server is still importing the project: try again later");
    }

    @Test
    void reportsAFileOutsideTheProject() throws IOException {
        assertRefused(
                Scenarios.EXTRACT_VARIABLE_OUTSIDE_THE_PROJECT,
                LanguageServerErrorKey.FILE_OUTSIDE_PROJECT,
                "The file " + root.getParent().resolve("outside/Elsewhere.java")
                        + " is not in the project of the language server");
    }

    @Test
    void rejectsMissingCollaborators() {
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> new ExtractVariable(null, new NioFileStore()))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("server"));
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> new ExtractVariable(new FakeJavaLanguageServer(root), null))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("store"));
    }

    @Test
    void rejectsMissingArguments() throws IOException {
        project(Scenarios.EXTRACT_VARIABLE);

        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> extract.apply(null, JOINED, "joined"))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("file"));
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> extract.dryRun(path(GREETER), null, "joined"))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("selection"));
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> extract.apply(path(GREETER), JOINED, null))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("name"));
    }
}
