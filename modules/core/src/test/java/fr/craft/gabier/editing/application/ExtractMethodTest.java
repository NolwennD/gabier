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

class ExtractMethodTest {

    private static final String GREETER = "src/main/java/demo/Greeter.java";
    private static final String TOTALS = "src/main/java/demo/Totals.java";
    private static final Range PRINT = new Range(new Position(5, 8), new Position(5, 46));
    private static final Range SUM = new Range(new Position(10, 8), new Position(13, 9));
    private static final Range SCALE = new Range(new Position(29, 15), new Position(29, 28));

    @TempDir
    Path root;

    private FakeJavaLanguageServer server;
    private ExtractMethod extract;

    /** Writes the project of the scenarios and plays them with a fake scripted by them. */
    private void project(Scenario... scenarios) throws IOException {
        server = new FakeJavaLanguageServer(root);
        for (Scenario scenario : scenarios) {
            scenario.writeProject(root);
            server.scripted(scenario);
        }
        extract = new ExtractMethod(server, new NioFileStore());
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
    void replacesTheStatementByACallAndDeclaresTheMethodWithTheNameGivenOnDisk() throws IOException {
        project(Scenarios.EXTRACT_METHOD_THAT_USES_A_LOCAL_VARIABLE);

        ChangeReport report = extract.apply(path(TOTALS), PRINT, "printCount");

        assertThat(text(TOTALS))
                .contains("        printCount(count);\n")
                .contains("private void printCount(int count) {")
                .doesNotContain(
                        "        System.out.println(\"count: \" + count);\n        System.out.println(\"done\");");
        assertThat(report.modified()).containsExactly(path(TOTALS));
        assertThat(report.created()).isEmpty();
        assertThat(report.renamed()).isEmpty();
        assertThat(report.deleted()).isEmpty();
    }

    @Test
    void returnsTheLocalVariableTheRestOfTheMethodNeeds() throws IOException {
        project(Scenarios.EXTRACT_METHOD_THAT_ASSIGNS_A_LOCAL_VARIABLE);

        extract.apply(path(TOTALS), SUM, "sumOf");

        assertThat(text(TOTALS))
                .contains("        int sum = sumOf(values);\n")
                .contains("private int sumOf(int[] values) {");
    }

    @Test
    void replacesAnExpressionByTheCall() throws IOException {
        project(Scenarios.EXTRACT_METHOD_FROM_AN_EXPRESSION);

        extract.apply(path(TOTALS), SCALE, "scaled");

        assertThat(text(TOTALS))
                .contains("        return scaled(count);\n")
                .contains("private int scaled(int count) {");
    }

    @Test
    void givesTheMethodTheNameOfAnotherMethodOfTheClassWhenTheParameterTypesAreNotTheSame() throws IOException {
        project(Scenarios.EXTRACT_METHOD_TO_THE_NAME_OF_AN_OVERLOAD);

        extract.apply(path(TOTALS), new Range(new Position(4, 20), new Position(4, 33)), "doubled");

        assertThat(text(TOTALS))
                .contains("        int count = doubled(values);\n")
                .contains("public int doubled(int value) {")
                .contains("private int doubled(int[] values) {");
    }

    @Test
    void asksTheServerOnceForTheFileTheSelectionAndTheNameAsGiven() throws IOException {
        project(Scenarios.EXTRACT_METHOD_THAT_USES_A_LOCAL_VARIABLE);

        extract.apply(path(TOTALS), PRINT, "printCount");

        assertThat(server.asked()).containsExactly(new Request.ExtractMethod(path(TOTALS), PRINT, "printCount"));
    }

    @Test
    void writesNothingOnADryRunAndGivesTheDiff() throws IOException {
        project(Scenarios.EXTRACT_METHOD_THAT_USES_A_LOCAL_VARIABLE);
        Map<String, String> before = snapshot();

        ChangeReport report = extract.dryRun(path(TOTALS), PRINT, "printCount");

        assertThat(snapshot()).isEqualTo(before);
        assertThat(report.modified()).containsExactly(path(TOTALS));
        assertThat(report.diff())
                .contains("-        System.out.println(\"count: \" + count);")
                .contains("+        printCount(count);")
                .contains("+    private void printCount(int count) {");
    }

    @Test
    void givesTheSameReportOnADryRunAndOnTheRealRun() throws IOException {
        project(Scenarios.EXTRACT_METHOD_THAT_USES_A_LOCAL_VARIABLE);

        ChangeReport planned = extract.dryRun(path(TOTALS), PRINT, "printCount");
        ChangeReport done = extract.apply(path(TOTALS), PRINT, "printCount");

        assertThat(done).isEqualTo(planned);
    }

    private void assertRefused(Scenario scenario, LanguageServerErrorKey key, String message) throws IOException {
        project(scenario);
        Map<String, String> before = snapshot();
        var request = (Request.ExtractMethod) scenario.request();
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
                Scenarios.EXTRACT_METHOD_FROM_SOMETHING_THAT_IS_NOT_AN_EXPRESSION,
                LanguageServerErrorKey.NOT_EXTRACTABLE,
                "The selection 7:8-7:14 of {root}/" + GREETER
                        + " cannot be extracted: it is not an expression or statements that can be extracted here");
    }

    @Test
    void refusesHalfAStatement() throws IOException {
        assertRefused(
                Scenarios.EXTRACT_METHOD_FROM_HALF_A_STATEMENT,
                LanguageServerErrorKey.NOT_EXTRACTABLE,
                "The selection 5:30-6:20 of {root}/" + TOTALS
                        + " cannot be extracted: it is not an expression or statements that can be extracted here");
    }

    @Test
    void refusesABranchThatReturns() throws IOException {
        assertRefused(
                Scenarios.EXTRACT_METHOD_FROM_A_BRANCH_THAT_RETURNS,
                LanguageServerErrorKey.NOT_EXTRACTABLE,
                "The selection 18:8-20:9 of {root}/" + TOTALS
                        + " cannot be extracted: it is not an expression or statements that can be extracted here");
    }

    @Test
    void refusesANameThatIsNotAnIdentifier() throws IOException {
        assertRefused(
                Scenarios.EXTRACT_METHOD_TO_AN_INVALID_NAME,
                LanguageServerErrorKey.INVALID_NAME,
                "\"1st\" is not a valid name for the symbol at 5:8 in {root}/" + TOTALS);
    }

    @Test
    void refusesTheNameOfAMethodOfTheClassThatHasTheSameSignature() throws IOException {
        assertRefused(
                Scenarios.EXTRACT_METHOD_TO_A_NAME_IN_USE,
                LanguageServerErrorKey.INVALID_NAME,
                "\"doubled\" is not a valid name for the symbol at 29:15 in {root}/" + TOTALS);
    }

    @Test
    void reportsAServerThatIsNotReady() throws IOException {
        assertRefused(
                Scenarios.EXTRACT_METHOD_WHILE_THE_SERVER_IMPORTS,
                LanguageServerErrorKey.NOT_READY,
                "The language server is still importing the project: try again later");
    }

    @Test
    void reportsAFileOutsideTheProject() throws IOException {
        assertRefused(
                Scenarios.EXTRACT_METHOD_OUTSIDE_THE_PROJECT,
                LanguageServerErrorKey.FILE_OUTSIDE_PROJECT,
                "The file " + root.getParent().resolve("outside/Elsewhere.java")
                        + " is not in the project of the language server");
    }

    @Test
    void rejectsMissingCollaborators() {
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> new ExtractMethod(null, new NioFileStore()))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("server"));
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> new ExtractMethod(new FakeJavaLanguageServer(root), null))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("store"));
    }

    @Test
    void rejectsMissingArguments() throws IOException {
        project(Scenarios.EXTRACT_METHOD_THAT_USES_A_LOCAL_VARIABLE);

        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> extract.apply(null, PRINT, "printCount"))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("file"));
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> extract.dryRun(path(TOTALS), null, "printCount"))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("selection"));
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> extract.apply(path(TOTALS), PRINT, null))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("name"));
    }
}
