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

class MoveTypeToFileTest {

    private static final String SHAPES = "src/main/java/demo/Shapes.java";
    private static final String DRAWING = "src/main/java/demo/app/Drawing.java";
    private static final String CIRCLE = "src/main/java/demo/Circle.java";
    private static final String SQUARE = "src/main/java/demo/Square.java";
    private static final String LABEL = "src/main/java/demo/Label.java";
    private static final Position CIRCLE_NAME = new Position(5, 24);
    private static final Position SQUARE_NAME = new Position(11, 17);
    private static final Position LABEL_NAME = new Position(17, 17);

    @TempDir
    Path root;

    private FakeJavaLanguageServer server;
    private MoveTypeToFile move;

    /** Writes the project of the scenarios and plays them with a fake scripted by them. */
    private void project(Scenario... scenarios) throws IOException {
        server = new FakeJavaLanguageServer(root);
        for (Scenario scenario : scenarios) {
            scenario.writeProject(root);
            server.scripted(scenario);
        }
        move = new MoveTypeToFile(server, new NioFileStore());
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
    void createsTheFileOfAStaticNestedTypeInItsPackageAndTakesTheTypeOutOfItsOuterClass() throws IOException {
        project(Scenarios.MOVE_A_STATIC_NESTED_TYPE);

        ChangeReport report = move.apply(path(SHAPES), CIRCLE_NAME);

        assertThat(text(CIRCLE))
                .startsWith("package demo;\n\npublic class Circle {\n")
                .endsWith("    }\n}");
        assertThat(text(SHAPES)).doesNotContain("class Circle").contains("public class Square {");
        assertThat(report.created()).containsExactly(path(CIRCLE));
        assertThat(report.modified()).containsExactlyInAnyOrder(path(SHAPES), path(DRAWING));
        assertThat(report.renamed()).isEmpty();
        assertThat(report.deleted()).isEmpty();
    }

    @Test
    void updatesTheReferencesAndTheImportsOfTheFilesThatUseTheMovedType() throws IOException {
        project(Scenarios.MOVE_A_STATIC_NESTED_TYPE);

        move.apply(path(SHAPES), CIRCLE_NAME);

        assertThat(text(DRAWING))
                .contains("import demo.Circle;\nimport demo.Shapes;\n")
                .contains("        Circle circle = shapes.circle();\n")
                .doesNotContain("Shapes.Circle");
    }

    @Test
    void movesAnInnerTypeThatDoesNotUseTheOuterInstanceWithoutAFieldOrAConstructor() throws IOException {
        project(Scenarios.MOVE_AN_INNER_TYPE_THAT_DOES_NOT_USE_THE_OUTER_INSTANCE);

        ChangeReport report = move.apply(path(SHAPES), SQUARE_NAME);

        assertThat(text(SQUARE)).isEqualTo("""
            package demo;

            public class Square {
                public double area(double side) {
                    return side * side;
                }
            }\
            """);
        assertThat(text(SHAPES)).doesNotContain("class Square");
        assertThat(report.created()).containsExactly(path(SQUARE));
        assertThat(report.modified()).containsExactly(path(SHAPES));
    }

    @Test
    void givesAnInnerTypeThatUsesTheOuterInstanceAFieldAndAConstructorParameterOfTheOuterType() throws IOException {
        project(Scenarios.MOVE_AN_INNER_TYPE_THAT_USES_THE_OUTER_INSTANCE);

        move.apply(path(SHAPES), LABEL_NAME);

        assertThat(text(LABEL))
                .contains("    private final Shapes shapes;\n")
                .contains("    Label(Shapes shapes) {\n        this.shapes = shapes;\n    }\n")
                .contains("return \"unit: \" + this.shapes.unit;");
        assertThat(text(SHAPES)).doesNotContain("class Label").contains("    final String unit = \"cm\";");
    }

    @Test
    void movesTheTypeThatEnclosesAPositionInItsMethod() throws IOException {
        project(Scenarios.MOVE_THE_TYPE_THAT_ENCLOSES_A_POSITION_IN_ITS_METHOD);

        move.apply(path(SHAPES), new Position(7, 15));

        assertThat(text(CIRCLE)).contains("public class Circle {");
        assertThat(text(SHAPES)).doesNotContain("class Circle");
    }

    @Test
    void asksTheServerOnceForTheFileAndThePositionAsGiven() throws IOException {
        project(Scenarios.MOVE_A_STATIC_NESTED_TYPE);

        move.apply(path(SHAPES), CIRCLE_NAME);

        assertThat(server.asked()).containsExactly(new Request.MoveTypeToFile(path(SHAPES), CIRCLE_NAME));
    }

    @Test
    void writesNothingOnADryRunAndGivesTheDiffWithTheNewFile() throws IOException {
        project(Scenarios.MOVE_A_STATIC_NESTED_TYPE);
        Map<String, String> before = snapshot();

        ChangeReport report = move.dryRun(path(SHAPES), CIRCLE_NAME);

        assertThat(snapshot()).isEqualTo(before);
        assertThat(path(CIRCLE)).doesNotExist();
        assertThat(report.created()).containsExactly(path(CIRCLE));
        assertThat(report.diff())
                .containsOnlyOnce("+++ " + path(CIRCLE))
                .containsOnlyOnce("--- /dev/null\n+++ " + path(CIRCLE) + "\n@@ -0,0 +1,")
                .contains("+public class Circle {")
                .doesNotContain("--- " + path(CIRCLE))
                .contains("-    public static class Circle {")
                .contains("+import demo.Circle;");
    }

    @Test
    void givesTheSameReportOnADryRunAndOnTheRealRun() throws IOException {
        project(Scenarios.MOVE_A_STATIC_NESTED_TYPE);

        ChangeReport planned = move.dryRun(path(SHAPES), CIRCLE_NAME);
        ChangeReport done = move.apply(path(SHAPES), CIRCLE_NAME);

        assertThat(done).isEqualTo(planned);
    }

    private void assertRefused(Scenario scenario, LanguageServerErrorKey key, String message) throws IOException {
        project(scenario);
        Map<String, String> before = snapshot();
        var request = (Request.MoveTypeToFile) scenario.request();
        Path file = root.resolve(request.file()).normalize();

        assertThatExceptionOfType(LanguageServerException.class)
                .isThrownBy(() -> move.apply(file, request.position()))
                .satisfies(exception -> assertThat(exception.key()).isEqualTo(key))
                .withMessage(message.replace("{root}", root.toString()));
        assertThatExceptionOfType(LanguageServerException.class)
                .isThrownBy(() -> move.dryRun(file, request.position()))
                .satisfies(exception -> assertThat(exception.key()).isEqualTo(key));

        assertThat(snapshot()).isEqualTo(before);
    }

    private static String notMovable(String position) {
        return "No nested type at " + position + " in {root}/" + SHAPES
                + " can be moved to a file of its own: the position is outside any type, the type is a top-level"
                + " type, or a file of its name exists in its package";
    }

    @Test
    void refusesAPositionOutsideAnyType() throws IOException {
        assertRefused(
                Scenarios.MOVE_A_TYPE_FROM_A_POSITION_OUTSIDE_ANY_TYPE,
                LanguageServerErrorKey.NOT_MOVABLE,
                notMovable("0:3"));
    }

    @Test
    void refusesATopLevelType() throws IOException {
        assertRefused(Scenarios.MOVE_A_TOP_LEVEL_TYPE, LanguageServerErrorKey.NOT_MOVABLE, notMovable("2:13"));
    }

    @Test
    void refusesATypeWhoseFileExistsAndOverwritesNothing() throws IOException {
        assertRefused(
                Scenarios.MOVE_A_TYPE_TO_A_FILE_THAT_EXISTS, LanguageServerErrorKey.NOT_MOVABLE, notMovable("23:24"));
    }

    @Test
    void reportsAServerThatIsNotReady() throws IOException {
        assertRefused(
                Scenarios.MOVE_A_TYPE_WHILE_THE_SERVER_IMPORTS,
                LanguageServerErrorKey.NOT_READY,
                "The language server is still importing the project: try again later");
    }

    @Test
    void reportsAFileOutsideTheProject() throws IOException {
        assertRefused(
                Scenarios.MOVE_A_TYPE_OUTSIDE_THE_PROJECT,
                LanguageServerErrorKey.FILE_OUTSIDE_PROJECT,
                "The file " + root.getParent().resolve("outside/Elsewhere.java")
                        + " is not in the project of the language server");
    }

    @Test
    void rejectsMissingCollaborators() {
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> new MoveTypeToFile(null, new NioFileStore()))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("server"));
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> new MoveTypeToFile(new FakeJavaLanguageServer(root), null))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("store"));
    }

    @Test
    void rejectsMissingArguments() throws IOException {
        project(Scenarios.MOVE_A_STATIC_NESTED_TYPE);

        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> move.apply(null, CIRCLE_NAME))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("file"));
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> move.dryRun(path(SHAPES), null))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("position"));
    }
}
