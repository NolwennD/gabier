package eu.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonObject;
import eu.gabier.editing.domain.CleanUp;
import eu.gabier.editing.domain.Position;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.Stream;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.messages.Either3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

/**
 * What the jdtls adapter tells jdtls about the imports of the project, against {@link FakeJdtls}: jdtls reads no file
 * of the project for them, so before each request that writes imports the adapter reads the files again
 * ({@link ImportPreferences}) and gives jdtls the three settings, when they are not the ones it already has.
 */
@ExtendWith(FakeOnlyGuard.class)
class JdtlsImportPreferencesTest {

    private static final Duration LONG = Duration.ofSeconds(30);
    private static final Duration QUIET = Duration.ofMillis(300);
    private static final String VSCODE = ".vscode/settings.json";

    @TempDir
    Path root;

    private final FakeJdtls fake = new FakeJdtls();
    private final List<String> log = new CopyOnWriteArrayList<>();
    private Path greeter;

    @BeforeEach
    void writeTheProject() throws IOException {
        root = root.toRealPath();
        greeter = write("src/main/java/demo/Greeter.java", """
            package demo;

            import java.util.Set;

            class Greeter {
                Set<String> seen;
            }
            """);
        fake.listsProjects(root.toUri().toString());
        fake.publishesOnOpen(greeter, List.of());
        fake.codeActionAnswers(organizing());
        fake.cleanupAnswers(CompletableFuture.completedFuture(new WorkspaceEdit(Map.of())));
    }

    @AfterEach
    void leaveNothingBehind() throws Exception {
        Thread.interrupted();
        fake.close();
        if (fake.data() != null && Files.exists(fake.data())) {
            try (Stream<Path> files = Files.walk(fake.data())) {
                for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(file);
                }
            }
        }
    }

    private Path write(String relative, String text) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text);

        return file;
    }

    private JdtlsLanguageServer started() throws Exception {
        var settings = new JdtlsSettings(
                root, List.of(root), LONG, Duration.ofMillis(20), Duration.ofSeconds(5), JdtlsWorkspace.temporary());
        var server = JdtlsLanguageServer.start(fake, settings, QUIET, log::add);
        fake.initializeParams();
        server.awaitStarted();

        return server;
    }

    /** jdtls organizes the imports with one edit, which removes {@code Set}: the answer does not matter here. */
    private CompletableFuture<List<Either<Command, CodeAction>>> organizing() {
        var action = new CodeAction("Organize imports");
        action.setKind(CodeActionKind.SourceOrganizeImports);
        action.setEdit(new WorkspaceEdit(Map.of(
                greeter.toUri().toString(),
                List.of(new TextEdit(
                        new Range(new org.eclipse.lsp4j.Position(2, 0), new org.eclipse.lsp4j.Position(3, 0)), "")))));

        return CompletableFuture.completedFuture(List.of(Either.forRight(action)));
    }

    /** The configurations jdtls received, once there are {@code count}: a notification has no answer. */
    private List<Object> configurations(int count) {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (fake.configurations().size() < count && System.nanoTime() < deadline) {
            LockSupport.parkNanos(Duration.ofMillis(10).toNanos());
        }

        return fake.configurations();
    }

    private static String importSettings(Object configuration) {
        JsonObject java = ((JsonObject) configuration).getAsJsonObject("java");
        JsonObject organize = java.getAsJsonObject("sources").getAsJsonObject("organizeImports");

        return java.getAsJsonObject("completion").get("importOrder") + " " + organize.get("starThreshold") + " "
                + organize.get("staticStarThreshold");
    }

    @Test
    void givesJdtlsTheImportSettingsOfTheVsCodeFileOfTheProjectBeforeAskingForTheOrganization() throws Exception {
        write(VSCODE, """
            {
              "java.completion.importOrder": ["#", ""],
              "java.sources.organizeImports.starThreshold": 2147483647,
              "java.sources.organizeImports.staticStarThreshold": 2147483647
            }
            """);

        try (var server = started()) {
            assertThat(server.organizeImports(greeter)).isPresent();

            assertThat(configurations(1)).hasSize(1);
            assertThat(importSettings(fake.configurations().getFirst()))
                    .isEqualTo("[\"#\",\"\"] 2147483647 2147483647");
            assertThat(fake.received()).containsSubsequence("didChangeConfiguration", "codeAction Greeter.java");
        }
    }

    @Test
    void givesJdtlsTheImportSettingsOfTheEclipsePreferencesOfTheProject() throws Exception {
        write(".settings/org.eclipse.jdt.ui.prefs", """
            org.eclipse.jdt.ui.importorder=java;\\#;
            org.eclipse.jdt.ui.ondemandthreshold=4
            """);

        try (var server = started()) {
            server.organizeImports(greeter);

            assertThat(importSettings(configurations(1).getFirst())).isEqualTo("[\"java\",\"#\",\"\"] 4 99");
        }
    }

    @Test
    void givesJdtlsNothingForAProjectWhoseFilesLeaveItsDefaults() throws Exception {
        try (var server = started()) {
            server.organizeImports(greeter);
            server.organizeImports(greeter);

            assertThat(fake.configurations()).isEmpty();
            assertThat(fake.received()).doesNotContain("didChangeConfiguration");
        }
    }

    @Test
    void givesJdtlsTheSettingsAgainOnlyWhenTheFilesChangeWithNoRestart() throws Exception {
        write(VSCODE, """
            {"java.sources.organizeImports.starThreshold": 5}
            """);

        try (var server = started()) {
            server.organizeImports(greeter);
            server.organizeImports(greeter);
            assertThat(configurations(1)).hasSize(1);

            write(VSCODE, """
                {"java.sources.organizeImports.starThreshold": 6}
                """);
            server.organizeImports(greeter);

            assertThat(configurations(2)).hasSize(2);
            assertThat(importSettings(fake.configurations().getFirst()))
                    .isEqualTo("[\"java\",\"javax\",\"org\",\"com\"] 5 99");
            assertThat(importSettings(fake.configurations().getLast()))
                    .isEqualTo("[\"java\",\"javax\",\"org\",\"com\"] 6 99");
        }
    }

    @Test
    void givesJdtlsTheDefaultsExplicitlyWhenAFileNoLongerSetsWhatItSetBecauseJdtlsKeepsTheValueItHas()
            throws Exception {
        Path settings = write(VSCODE, """
            {"java.completion.importOrder": ["#", ""], "java.sources.organizeImports.starThreshold": 5}
            """);

        try (var server = started()) {
            server.organizeImports(greeter);
            Files.delete(settings);
            server.organizeImports(greeter);

            assertThat(configurations(2)).hasSize(2);
            assertThat(importSettings(fake.configurations().getFirst())).isEqualTo("[\"#\",\"\"] 5 99");
            assertThat(importSettings(fake.configurations().getLast()))
                    .isEqualTo("[\"java\",\"javax\",\"org\",\"com\"] 99 99");
        }
    }

    @Test
    void givesJdtlsTheSettingsBeforeTheCleanUpsAreSetSoThatTheCleanUpsFindThem() throws Exception {
        write(VSCODE, """
            {"java.sources.organizeImports.starThreshold": 5}
            """);

        try (var server = started()) {
            server.cleanUp(greeter, List.of(CleanUp.ADD_OVERRIDE));

            assertThat(configurations(3)).hasSize(3);
            assertThat(importSettings(fake.configurations().getFirst()))
                    .isEqualTo("[\"java\",\"javax\",\"org\",\"com\"] 5 99");
            assertThat(((JsonObject) fake.configurations().get(1))
                            .getAsJsonObject("java")
                            .has("cleanup"))
                    .isTrue();
        }
    }

    @Test
    void givesJdtlsTheSettingsBeforeAnyOtherRequestThatWritesImports() throws Exception {
        write(VSCODE, """
            {"java.sources.organizeImports.starThreshold": 5}
            """);
        fake.prepareRenameAnswers(CompletableFuture.completedFuture(Either3.forFirst(
                new Range(new org.eclipse.lsp4j.Position(4, 6), new org.eclipse.lsp4j.Position(4, 13)))));
        fake.renameAnswers(CompletableFuture.completedFuture(new WorkspaceEdit(Map.of(
                greeter.toUri().toString(),
                List.of(new TextEdit(
                        new Range(new org.eclipse.lsp4j.Position(4, 6), new org.eclipse.lsp4j.Position(4, 13)),
                        "Welcomer"))))));

        try (var server = started()) {
            server.rename(greeter, new Position(4, 6), "Welcomer");

            assertThat(importSettings(configurations(1).getFirst()))
                    .isEqualTo("[\"java\",\"javax\",\"org\",\"com\"] 5 99");
            assertThat(fake.received()).containsSubsequence("didChangeConfiguration", "prepareRename Greeter.java");
        }
    }

    @Test
    void givesJdtlsNothingForARequestThatWritesNoImports() throws Exception {
        write(VSCODE, """
            {"java.sources.organizeImports.starThreshold": 5}
            """);

        try (var server = started()) {
            server.diagnostics(greeter);

            assertThat(fake.configurations()).isEmpty();
        }
    }
}
