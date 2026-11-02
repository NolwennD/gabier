package eu.gabier.editing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import eu.gabier.editing.domain.ChangeReport;
import eu.gabier.editing.domain.EditingErrorKey;
import eu.gabier.editing.domain.EditingException;
import eu.gabier.editing.domain.LanguageServerErrorKey;
import eu.gabier.editing.domain.LanguageServerException;
import eu.gabier.editing.domain.TypeKind;
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

class CreateTypeTest {

    private static final String WIDGET = "src/main/java/demo/Widget.java";

    @TempDir
    Path root;

    private FakeJavaLanguageServer server;
    private CreateType create;

    /** Writes the project of the scenarios and plays them with a fake scripted by them. */
    private void project(Scenario... scenarios) throws IOException {
        server = new FakeJavaLanguageServer(root);
        for (Scenario scenario : scenarios) {
            scenario.writeProject(root);
            server.scripted(scenario);
        }
        create = new CreateType(server, new NioFileStore());
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
    void createsAClassInThePackageOfItsFolderWithNoComment() throws IOException {
        project(Scenarios.CREATE_A_CLASS);

        ChangeReport report = create.apply(path(WIDGET), TypeKind.CLASS);

        assertThat(text(WIDGET)).isEqualTo("""
            package demo;

            public class Widget {

            }
            """);
        assertThat(report.created()).containsExactly(path(WIDGET));
        assertThat(report.modified()).isEmpty();
        assertThat(report.deleted()).isEmpty();
    }

    @Test
    void createsAnInterface() throws IOException {
        project(Scenarios.CREATE_AN_INTERFACE);

        create.apply(path("src/main/java/demo/Renderer.java"), TypeKind.INTERFACE);

        assertThat(text("src/main/java/demo/Renderer.java")).isEqualTo("""
            package demo;

            public interface Renderer {

            }
            """);
    }

    @Test
    void createsARecord() throws IOException {
        project(Scenarios.CREATE_A_RECORD);

        create.apply(path("src/main/java/demo/Point.java"), TypeKind.RECORD);

        assertThat(text("src/main/java/demo/Point.java")).isEqualTo("""
            package demo;

            public record Point() {
            }
            """);
    }

    @Test
    void createsAnEnum() throws IOException {
        project(Scenarios.CREATE_AN_ENUM);

        create.apply(path("src/main/java/demo/Color.java"), TypeKind.ENUM);

        assertThat(text("src/main/java/demo/Color.java")).isEqualTo("""
            package demo;

            public enum Color {

            }
            """);
    }

    @Test
    void givesAFileOfTheDefaultPackageNoPackageLine() throws IOException {
        project(Scenarios.CREATE_A_TYPE_IN_THE_DEFAULT_PACKAGE);

        create.apply(path("src/main/java/Launcher.java"), TypeKind.CLASS);

        assertThat(text("src/main/java/Launcher.java")).isEqualTo("""
            public class Launcher {

            }
            """);
    }

    @Test
    void givesAFileOfASubpackageTheNameOfThatPackage() throws IOException {
        project(Scenarios.CREATE_A_TYPE_IN_A_SUBPACKAGE);

        create.apply(path("src/main/java/demo/app/Console.java"), TypeKind.CLASS);

        assertThat(text("src/main/java/demo/app/Console.java")).startsWith("package demo.app;\n");
    }

    @Test
    void asksTheServerOnceForTheFileAndTheKindAsGiven() throws IOException {
        project(Scenarios.CREATE_A_CLASS);

        create.apply(path(WIDGET), TypeKind.CLASS);

        assertThat(server.asked()).containsExactly(new Request.CreateType(path(WIDGET), TypeKind.CLASS));
    }

    @Test
    void writesNothingOnADryRunAndShowsTheFileAndItsDiff() throws IOException {
        project(Scenarios.CREATE_A_CLASS);
        Map<String, String> before = snapshot();

        ChangeReport report = create.dryRun(path(WIDGET), TypeKind.CLASS);

        assertThat(snapshot()).isEqualTo(before);
        assertThat(report.created()).containsExactly(path(WIDGET));
        assertThat(report.diff())
                .contains("--- /dev/null")
                .contains("+++ " + path(WIDGET))
                .contains("+package demo;")
                .contains("+public class Widget {");
    }

    @Test
    void makesTheFolderOfAFileInANewPackage() throws IOException {
        project(Scenarios.CREATE_A_TYPE_IN_A_NEW_PACKAGE);
        Path gadget = path("src/main/java/fresh/Gadget.java");

        ChangeReport report = create.apply(gadget, TypeKind.CLASS);

        assertThat(report.created()).containsExactly(gadget);
        assertThat(gadget).content().isEqualTo("package fresh;\n\npublic class Gadget {\n\n}\n");
    }

    @Test
    void makesNoFolderOnADryRunAndShowsTheFileAndItsPackage() throws IOException {
        project(Scenarios.CREATE_A_TYPE_IN_A_NEW_PACKAGE);
        Map<String, String> before = snapshot();
        Path gadget = path("src/main/java/fresh/Gadget.java");

        ChangeReport report = create.dryRun(gadget, TypeKind.CLASS);

        assertThat(report.created()).containsExactly(gadget);
        assertThat(report.diff()).contains("+package fresh;");
        assertThat(snapshot()).isEqualTo(before);
        assertThat(path("src/main/java/fresh")).doesNotExist();
    }

    @Test
    void refusesAFileUnderAFileAndMakesNoFolder() throws IOException {
        project(Scenarios.CREATE_A_TYPE_IN_A_NEW_PACKAGE);
        Files.writeString(path("src/main/java/fresh"), "in the way");
        Map<String, String> before = snapshot();

        assertThatExceptionOfType(EditingException.class)
                .isThrownBy(() -> create.apply(path("src/main/java/fresh/Gadget.java"), TypeKind.CLASS))
                .satisfies(exception -> assertThat(exception.key()).isEqualTo(EditingErrorKey.FOLDER_MISSING));
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void refusesAFileThatExistsAndLeavesItAsItWas() throws IOException {
        project(Scenarios.CREATE_A_CLASS);
        Files.writeString(path(WIDGET), "package demo;\n\nclass Widget { int size; }\n");
        Map<String, String> before = snapshot();

        assertThatExceptionOfType(EditingException.class)
                .isThrownBy(() -> create.apply(path(WIDGET), TypeKind.CLASS))
                .satisfies(exception -> assertThat(exception.key()).isEqualTo(EditingErrorKey.FILE_EXISTS));
        assertThatExceptionOfType(EditingException.class)
                .isThrownBy(() -> create.dryRun(path(WIDGET), TypeKind.CLASS))
                .satisfies(exception -> assertThat(exception.key()).isEqualTo(EditingErrorKey.FILE_EXISTS));
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void refusesAFileWhoseNameIsNotATypeNameBeforeAskingTheServer() throws IOException {
        project(Scenarios.CREATE_A_CLASS);
        Map<String, String> before = snapshot();

        assertThatExceptionOfType(EditingException.class)
                .isThrownBy(() -> create.apply(path("src/main/java/demo/widget-impl.java"), TypeKind.CLASS))
                .satisfies(exception -> assertThat(exception.key()).isEqualTo(EditingErrorKey.NOT_A_TYPE_NAME));
        assertThatExceptionOfType(EditingException.class)
                .isThrownBy(() -> create.dryRun(path("src/main/java/demo/Widget.txt"), TypeKind.CLASS))
                .satisfies(exception -> assertThat(exception.key()).isEqualTo(EditingErrorKey.NOT_A_TYPE_NAME));
        assertThat(server.asked()).isEmpty();
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void refusesAFileOutsideTheProjectAsTheServerDoes() throws IOException {
        project(Scenarios.CREATE_A_TYPE_OUTSIDE_THE_PROJECT);
        Map<String, String> before = snapshot();

        assertThatExceptionOfType(LanguageServerException.class)
                .isThrownBy(() -> create.apply(path("../outside/Elsewhere.java"), TypeKind.CLASS))
                .satisfies(exception ->
                        assertThat(exception.key()).isEqualTo(LanguageServerErrorKey.FILE_OUTSIDE_PROJECT));
        assertThat(snapshot()).isEqualTo(before);
        assertThat(path("../outside")).doesNotExist();
    }

    @Test
    void refusesWhileTheServerImportsTheProject() throws IOException {
        project(Scenarios.CREATE_A_TYPE_WHILE_THE_SERVER_IMPORTS);

        assertThatExceptionOfType(LanguageServerException.class)
                .isThrownBy(() -> create.dryRun(path(WIDGET), TypeKind.CLASS))
                .satisfies(exception -> assertThat(exception.key()).isEqualTo(LanguageServerErrorKey.NOT_READY));
    }

    @Test
    void requiresTheFileAndTheKind() throws IOException {
        project(Scenarios.CREATE_A_CLASS);

        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> create.apply(null, TypeKind.CLASS))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("file"));
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> create.dryRun(path(WIDGET), null))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("kind"));
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> new CreateType(null, new NioFileStore()))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("server"));
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> new CreateType(server, null))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("store"));
    }
}
