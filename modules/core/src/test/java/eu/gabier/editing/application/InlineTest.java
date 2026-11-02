package eu.gabier.editing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import eu.gabier.editing.domain.ChangeReport;
import eu.gabier.editing.domain.LanguageServerErrorKey;
import eu.gabier.editing.domain.LanguageServerException;
import eu.gabier.editing.domain.Position;
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

class InlineTest {

    private static final String PRICES = "src/main/java/demo/Prices.java";
    private static final String CHECKOUT = "src/main/java/demo/Checkout.java";
    private static final Position VAT_VARIABLE = new Position(6, 12);
    private static final Position HANDLING_METHOD = new Position(15, 8);
    private static final Position VAT_CONSTANT = new Position(3, 21);

    @TempDir
    Path root;

    private FakeJavaLanguageServer server;
    private Inline inline;

    /** Writes the project of the scenarios and plays them with a fake scripted by them. */
    private void project(Scenario... scenarios) throws IOException {
        server = new FakeJavaLanguageServer(root);
        for (Scenario scenario : scenarios) {
            scenario.writeProject(root);
            server.scripted(scenario);
        }
        inline = new Inline(server, new NioFileStore());
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
    void replacesEveryUseOfALocalVariableByItsValueAndRemovesTheDeclaration() throws IOException {
        project(Scenarios.INLINE_A_LOCAL_VARIABLE);

        ChangeReport report = inline.apply(path(PRICES), VAT_VARIABLE);

        assertThat(text(PRICES))
                .doesNotContain("int vat")
                .contains("\"tax: \" + (net * VAT / 100)")
                .contains("return net + net * VAT / 100;");
        assertThat(report.modified()).containsExactly(path(PRICES));
        assertThat(report.created()).isEmpty();
        assertThat(report.deleted()).isEmpty();
    }

    @Test
    void inlinesTheVariableFromOneOfItsUsesToo() throws IOException {
        project(Scenarios.INLINE_A_LOCAL_VARIABLE_FROM_ONE_OF_ITS_USES);

        inline.apply(path(PRICES), new Position(8, 21));

        assertThat(text(PRICES)).doesNotContain("int vat").doesNotContain("+ vat");
    }

    @Test
    void replacesEveryCallOfAMethodOfSeveralFilesByItsBodyAndRemovesTheMethod() throws IOException {
        project(Scenarios.INLINE_A_METHOD);

        ChangeReport report = inline.apply(path(PRICES), HANDLING_METHOD);

        assertThat(text(PRICES)).doesNotContain("handling").contains("weight * 3 + 5");
        assertThat(text(CHECKOUT)).doesNotContain("handling").contains("net * 3 + 5");
        assertThat(report.modified()).containsExactlyInAnyOrder(path(PRICES), path(CHECKOUT));
    }

    @Test
    void replacesEveryUseOfAConstantOfSeveralFilesByItsValueAndRemovesTheConstant() throws IOException {
        project(Scenarios.INLINE_A_CONSTANT);

        ChangeReport report = inline.apply(path(PRICES), VAT_CONSTANT);

        assertThat(text(PRICES)).doesNotContain("VAT").contains("net * 20 / 100");
        assertThat(text(CHECKOUT)).doesNotContain("VAT").contains("net + 20 + ");
        assertThat(report.modified()).containsExactlyInAnyOrder(path(PRICES), path(CHECKOUT));
    }

    @Test
    void inlinesOnlyTheCallItIsAskedOnAndKeepsTheMethod() throws IOException {
        project(Scenarios.INLINE_ONE_CALL_OF_A_METHOD);

        inline.apply(path(PRICES), new Position(12, 15));

        assertThat(text(PRICES))
                .contains("int handling(int weight)")
                .contains("return weight * 3 + 5 + handling(weight * 2);");
    }

    @Test
    void inlinesOnlyTheUseItIsAskedOnAndKeepsTheConstant() throws IOException {
        project(Scenarios.INLINE_ONE_USE_OF_A_CONSTANT);

        inline.apply(path(PRICES), new Position(6, 24));

        assertThat(text(PRICES)).contains("static final int VAT = 20;").contains("int vat = net * 20 / 100;");
    }

    @Test
    void asksTheServerOnceForTheFileAndThePositionAsGiven() throws IOException {
        project(Scenarios.INLINE_A_CONSTANT);

        inline.apply(path(PRICES), VAT_CONSTANT);

        assertThat(server.asked()).containsExactly(new Request.Inline(path(PRICES), VAT_CONSTANT));
    }

    @Test
    void writesNothingOnADryRunAndGivesTheDiff() throws IOException {
        project(Scenarios.INLINE_A_CONSTANT);
        Map<String, String> before = snapshot();

        ChangeReport report = inline.dryRun(path(PRICES), VAT_CONSTANT);

        assertThat(snapshot()).isEqualTo(before);
        assertThat(report.modified()).containsExactlyInAnyOrder(path(PRICES), path(CHECKOUT));
        assertThat(report.diff())
                .contains("+++ " + path(PRICES))
                .contains("-    static final int VAT = 20;")
                .contains("-        return net + Prices.VAT + prices.handling(net);")
                .contains("+        return net + 20 + prices.handling(net);");
    }

    @Test
    void givesTheSameReportOnADryRunAndOnTheRealRun() throws IOException {
        project(Scenarios.INLINE_A_METHOD);

        ChangeReport planned = inline.dryRun(path(PRICES), HANDLING_METHOD);
        ChangeReport done = inline.apply(path(PRICES), HANDLING_METHOD);

        assertThat(done).isEqualTo(planned);
    }

    private void assertRefused(Scenario scenario, LanguageServerErrorKey key, String message) throws IOException {
        project(scenario);
        Map<String, String> before = snapshot();
        var request = (Request.Inline) scenario.request();
        Path file = root.resolve(request.file()).normalize();

        assertThatExceptionOfType(LanguageServerException.class)
                .isThrownBy(() -> inline.apply(file, request.position()))
                .satisfies(exception -> assertThat(exception.key()).isEqualTo(key))
                .withMessage(message.replace("{root}", root.toString()));
        assertThatExceptionOfType(LanguageServerException.class)
                .isThrownBy(() -> inline.dryRun(file, request.position()))
                .satisfies(exception -> assertThat(exception.key()).isEqualTo(key));

        assertThat(snapshot()).isEqualTo(before);
    }

    private static String notInlinable(String position) {
        return "Nothing at " + position + " in {root}/" + PRICES
                + " can be inlined: the position is not on a local variable, a method or a constant, or the server"
                + " refuses it (a variable assigned more than once, a method that is recursive or abstract, a"
                + " declaration that nothing uses)";
    }

    @Test
    void refusesAPositionThatIsOnNothingToInline() throws IOException {
        assertRefused(
                Scenarios.INLINE_WHERE_THERE_IS_NOTHING_TO_INLINE,
                LanguageServerErrorKey.NOT_INLINABLE,
                notInlinable("0:3"));
    }

    @Test
    void refusesAVariableThatIsAssignedMoreThanOnce() throws IOException {
        assertRefused(
                Scenarios.INLINE_A_VARIABLE_ASSIGNED_TWICE,
                LanguageServerErrorKey.NOT_INLINABLE,
                notInlinable("20:12"));
    }

    @Test
    void refusesARecursiveMethod() throws IOException {
        assertRefused(Scenarios.INLINE_A_RECURSIVE_METHOD, LanguageServerErrorKey.NOT_INLINABLE, notInlinable("25:8"));
    }

    @Test
    void refusesAnAbstractMethod() throws IOException {
        assertRefused(Scenarios.INLINE_AN_ABSTRACT_METHOD, LanguageServerErrorKey.NOT_INLINABLE, notInlinable("30:21"));
    }

    @Test
    void reportsAServerThatIsNotReady() throws IOException {
        assertRefused(
                Scenarios.INLINE_WHILE_THE_SERVER_IMPORTS,
                LanguageServerErrorKey.NOT_READY,
                "The language server is still importing the project: try again later");
    }

    @Test
    void reportsAFileOutsideTheProject() throws IOException {
        assertRefused(
                Scenarios.INLINE_OUTSIDE_THE_PROJECT,
                LanguageServerErrorKey.FILE_OUTSIDE_PROJECT,
                "The file " + root.getParent().resolve("outside/Elsewhere.java")
                        + " is not in the project of the language server");
    }

    @Test
    void rejectsMissingCollaborators() {
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> new Inline(null, new NioFileStore()))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("server"));
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> new Inline(new FakeJavaLanguageServer(root), null))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("store"));
    }

    @Test
    void rejectsMissingArguments() throws IOException {
        project(Scenarios.INLINE_A_CONSTANT);

        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> inline.apply(null, VAT_CONSTANT))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("file"));
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> inline.dryRun(path(PRICES), null))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("position"));
    }
}
