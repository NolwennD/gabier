package fr.craft.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.craft.gabier.editing.domain.ChangeSet;
import fr.craft.gabier.editing.domain.CleanUp;
import fr.craft.gabier.editing.domain.FileChange;
import fr.craft.gabier.editing.domain.LanguageServerErrorKey;
import fr.craft.gabier.editing.domain.LanguageServerException;
import fr.craft.gabier.editing.domain.Position;
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
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@code cleanUp} of the jdtls adapter against {@link FakeJdtls}: the setting {@code java.cleanup.actions} is set to the
 * identifiers asked for, in the order given, before {@code java/cleanup} is asked for the file, and set back to nothing
 * after; the answer, one edit of the whole text, is given in the terms of the domain.
 */
class JdtlsCleanUpTest {

    private static final Duration LONG = Duration.ofSeconds(30);
    private static final Duration QUIET = Duration.ofMillis(300);

    @TempDir
    Path root;

    private final FakeJdtls fake = new FakeJdtls();
    private final List<String> log = new CopyOnWriteArrayList<>();
    private Path banner;

    @BeforeEach
    void writeTheProject() throws IOException {
        root = root.toRealPath();
        banner = root.resolve("src/main/java/demo/Banner.java");
        Files.createDirectories(banner.getParent());
        Files.writeString(banner, "package demo;\n\npublic class Banner {}\n");
        fake.listsProjects(root.toUri().toString());
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

    private JdtlsLanguageServer start(Duration timeout) {
        var settings = new JdtlsSettings(
                root, List.of(root), timeout, Duration.ofMillis(20), Duration.ofSeconds(5), JdtlsWorkspace.temporary());
        var server = JdtlsLanguageServer.start(fake, settings, QUIET, log::add);
        fake.initializeParams();
        return server;
    }

    private JdtlsLanguageServer started() throws Exception {
        var server = start(LONG);
        server.awaitStarted();
        return server;
    }

    private static org.eclipse.lsp4j.Position at(int line, int column) {
        return new org.eclipse.lsp4j.Position(line, column);
    }

    /** What jdtls answers: one edit of the whole text of the file. */
    private CompletableFuture<WorkspaceEdit> rewrites(String text) {
        var edit = new TextEdit(new Range(at(0, 0), at(3, 0)), text);

        return CompletableFuture.completedFuture(
                new WorkspaceEdit(Map.of(banner.toUri().toString(), List.of(edit))));
    }

    /**
     * The configurations jdtls received, once there are {@code count}: a notification has no answer, so the last one
     * may still be on its way when the request that follows it has been answered.
     */
    private List<Object> configurations(int count) {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (fake.configurations().size() < count && System.nanoTime() < deadline) {
            LockSupport.parkNanos(Duration.ofMillis(10).toNanos());
        }

        return fake.configurations();
    }

    /** The settings as they arrive at jdtls: JSON. */
    private static JsonObject javaOf(Object settings) {
        return ((JsonObject) settings).getAsJsonObject("java");
    }

    private static List<String> actionsOf(Object settings) {
        return javaOf(settings).getAsJsonObject("cleanup").getAsJsonArray("actions").asList().stream()
                .map(JsonElement::getAsString)
                .toList();
    }

    @Test
    void setsTheCleanUpsInTheOrderGivenThenAsksForTheFileAndAnswersInTheTermsOfTheDomain() throws Exception {
        fake.cleanupAnswers(rewrites("package demo;\n\npublic final class Banner {}\n"));

        try (var server = started()) {
            var changes =
                    server.cleanUp(banner, List.of(CleanUp.STRING_CONCAT_TO_TEXT_BLOCK, CleanUp.ADD_FINAL_MODIFIER));

            assertThat(changes)
                    .contains(new ChangeSet(List.of(new FileChange.EditFile(
                            banner,
                            List.of(new fr.craft.gabier.editing.domain.TextEdit(
                                    new fr.craft.gabier.editing.domain.Range(new Position(0, 0), new Position(3, 0)),
                                    "package demo;\n\npublic final class Banner {}\n"))))));
            assertThat(fake.cleanupRequests()).hasSize(1);
            assertThat(fake.cleanupRequests().getFirst().getUri())
                    .isEqualTo(banner.toUri().toString());
            assertThat(configurations(2)).hasSize(2);
            assertThat(actionsOf(fake.configurations().getFirst()))
                    .containsExactly("stringConcatToTextBlock", "addFinalModifier");
            assertThat(configurations(2)).hasSize(2);
            assertThat(fake.received())
                    .containsSubsequence("didChangeConfiguration", "cleanup Banner.java", "didChangeConfiguration");
        }
    }

    @Test
    void setsTheCleanUpsBackToNothingAfterTheRequestSoThatNoSaveRunsThem() throws Exception {
        fake.cleanupAnswers(rewrites("x"));

        try (var server = started()) {
            server.cleanUp(banner, List.of(CleanUp.ADD_OVERRIDE));

            assertThat(actionsOf(configurations(2).getLast())).isEmpty();
        }
    }

    @Test
    void sendsTheOtherPreferencesAlongWithTheCleanUpsSoThatNoneFallsBackToItsDefault() throws Exception {
        fake.cleanupAnswers(rewrites("x"));

        try (var server = started()) {
            server.cleanUp(banner, List.of(CleanUp.ADD_OVERRIDE));

            for (Object settings : configurations(2)) {
                assertThat(javaOf(settings).keySet())
                        .contains("sources", "completion", "templates", "references", "cleanup");
            }
        }
    }

    @Test
    void answersNothingWhenJdtlsAnswersAnEmptyListOfEdits() throws Exception {
        fake.cleanupAnswers(CompletableFuture.completedFuture(
                new WorkspaceEdit(Map.of(banner.toUri().toString(), List.of()))));

        try (var server = started()) {
            assertThat(server.cleanUp(banner, List.of(CleanUp.STRING_CONCAT_TO_TEXT_BLOCK)))
                    .isEmpty();
        }
    }

    @Test
    void answersNothingWhenJdtlsAnswersNothing() throws Exception {
        fake.cleanupAnswers(CompletableFuture.completedFuture(null));

        try (var server = started()) {
            assertThat(server.cleanUp(banner, List.of(CleanUp.STRING_CONCAT_TO_TEXT_BLOCK)))
                    .isEmpty();
        }
    }

    @Test
    void answersNothingWhenJdtlsDeclinesTheRequest() throws Exception {
        fake.cleanupAnswers(CompletableFuture.failedFuture(new ResponseErrorException(
                new ResponseError(ResponseErrorCode.InvalidRequest, "nothing to clean up", null))));

        try (var server = started()) {
            assertThat(server.cleanUp(banner, List.of(CleanUp.STRING_CONCAT_TO_TEXT_BLOCK)))
                    .isEmpty();
        }
    }

    @Test
    void takesAPathThatIsNotNormalizedAsTheFileItNames() throws Exception {
        fake.cleanupAnswers(rewrites("x"));
        var spelled = root.resolve("src/main/../main/java/demo/./Banner.java");

        try (var server = started()) {
            server.cleanUp(spelled, List.of(CleanUp.ADD_OVERRIDE));

            assertThat(fake.cleanupRequests().getFirst().getUri())
                    .isEqualTo(banner.toUri().toString());
        }
    }

    @Test
    void refusesAFileOutsideTheProjectWithoutTouchingTheSettingsOrAskingJdtls() throws Exception {
        Path outside = root.resolveSibling("outside").resolve("Elsewhere.java");

        try (var server = started()) {
            assertThatThrownBy(() -> server.cleanUp(outside, List.of(CleanUp.ADD_OVERRIDE)))
                    .isInstanceOfSatisfying(LanguageServerException.class, refusal -> {
                        assertThat(refusal.key()).isEqualTo(LanguageServerErrorKey.FILE_OUTSIDE_PROJECT);
                        assertThat(refusal.parameters()).containsEntry("file", outside.toString());
                    });
            assertThat(fake.cleanupRequests()).isEmpty();
            assertThat(fake.configurations()).isEmpty();
        }
    }

    @Test
    void refusesAFileThatDoesNotExistAndAFolder() throws Exception {
        Path missing = root.resolve("src/main/java/demo/Missing.java");
        Path folder = root.resolve("src/main/java/demo");

        try (var server = started()) {
            assertThatThrownBy(() -> server.cleanUp(missing, List.of(CleanUp.ADD_OVERRIDE)))
                    .isInstanceOfSatisfying(
                            LanguageServerException.class,
                            refusal -> assertThat(refusal.key()).isEqualTo(LanguageServerErrorKey.FILE_MISSING));
            assertThatThrownBy(() -> server.cleanUp(folder, List.of(CleanUp.ADD_OVERRIDE)))
                    .isInstanceOfSatisfying(
                            LanguageServerException.class,
                            refusal -> assertThat(refusal.key()).isEqualTo(LanguageServerErrorKey.NOT_A_FILE));
            assertThat(fake.cleanupRequests()).isEmpty();
        }
    }

    @Test
    void refusesAsNotReadyWhileJdtlsIsStillImporting() throws Exception {
        fake.neverAnswersInitialize();

        try (var server = start(LONG)) {
            assertThatThrownBy(() -> server.cleanUp(banner, List.of(CleanUp.ADD_OVERRIDE)))
                    .isInstanceOfSatisfying(
                            LanguageServerException.class,
                            refusal -> assertThat(refusal.key()).isEqualTo(LanguageServerErrorKey.NOT_READY));
            assertThat(fake.configurations()).isEmpty();
        }
    }

    @Test
    void saysWhatWentWrongWhenJdtlsFailsAndStillSetsTheCleanUpsBackToNothing() throws Exception {
        fake.cleanupAnswers(CompletableFuture.failedFuture(new IllegalStateException("the model is broken")));

        try (var server = started()) {
            assertThatThrownBy(() -> server.cleanUp(banner, List.of(CleanUp.ADD_OVERRIDE)))
                    .isInstanceOf(JdtlsException.class)
                    .hasMessageContaining("waiting for the clean-ups of " + banner)
                    .hasMessageContaining("Internal error");

            assertThat(actionsOf(configurations(2).getLast())).isEmpty();
        }
    }

    @Test
    void failsWhenJdtlsDoesNotAnswerInTime() throws Exception {
        // No answer is scripted.
        try (var server = start(Duration.ofSeconds(1))) {
            server.awaitStarted();

            assertThatThrownBy(() -> server.cleanUp(banner, List.of(CleanUp.ADD_OVERRIDE)))
                    .isInstanceOf(JdtlsException.class)
                    .hasMessageContaining("Timed out")
                    .hasMessageContaining("the clean-ups of " + banner);
        }
    }

    @Test
    void keepsTheInterruptionOfTheThreadThatWaits() throws Exception {
        fake.cleanupAnswers(rewrites("x"));

        try (var server = started()) {
            Thread.currentThread().interrupt();

            assertThatThrownBy(() -> server.cleanUp(banner, List.of(CleanUp.ADD_OVERRIDE)))
                    .isInstanceOf(JdtlsException.class)
                    .hasMessageContaining("Interrupted while waiting for the clean-ups of " + banner);
            assertThat(Thread.interrupted()).isTrue();
        }
    }
}
