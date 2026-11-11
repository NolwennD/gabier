package eu.gabier.cli.infrastructure.primary;

import static org.assertj.core.api.Assertions.assertThat;

import eu.gabier.protocol.DiagnosedFiles;
import eu.gabier.protocol.FileDiagnostics;
import eu.gabier.protocol.FileDiagnostics.Diagnosis;
import eu.gabier.protocol.FileDiagnostics.Severity;
import eu.gabier.protocol.JsonRpc;
import eu.gabier.protocol.Problem;
import eu.gabier.shared.error.domain.ErrorStatus;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * {@code diagnostics --watch} against a daemon of the tests that tells what changed when the test says so, on a real
 * socket: the command runs in a thread while the test reads its output as it comes.
 */
class WatchingDiagnosticsTest extends CliTest {

    private static final String BROKEN = "/project/src/Broken.java";
    private static final String CLEAN = "/project/src/Clean.java";

    private static DiagnosedFiles broken(String message) {
        return new DiagnosedFiles(
                List.of(new FileDiagnostics(BROKEN, List.of(new Diagnosis(Severity.ERROR, 4, 15, message)))));
    }

    private static DiagnosedFiles clean(String... files) {
        return new DiagnosedFiles(List.of(files).stream()
                .map(file -> new FileDiagnostics(file, List.of()))
                .toList());
    }

    private static Problem problem(String key, String detail) {
        return new Problem("urn:gabier:error:" + key, "A problem", detail, key, Map.of(), ErrorStatus.BAD_REQUEST);
    }

    private static String reply(DiagnosedFiles files) {
        return JsonRpc.success("1", files.toJson());
    }

    private CompletableFuture<Integer> running(String... args) {
        return CompletableFuture.supplyAsync(() -> runWithSocket(args));
    }

    private void awaitOutput(String text) throws InterruptedException {
        var deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (!out().contains(text) && System.nanoTime() < deadline) {
            Thread.sleep(5);
        }
        assertThat(out()).contains(text);
    }

    private int exitCode(CompletableFuture<Integer> running) throws Exception {
        return running.get(10, TimeUnit.SECONDS);
    }

    @Test
    void printsTheProblemsAsTheyAreAndThenTheFilesThatChangedEachLineAsSoonAsItComes() throws Exception {
        try (var daemon = new ScriptedStreamingDaemon(socket(), reply(broken("Type mismatch")))) {
            var running = running("diagnostics", "--watch");

            awaitOutput("/project/src/Broken.java:5:16: error: Type mismatch");
            assertThat(out().lines()).hasSize(1);
            daemon.says(JsonRpc.problems(clean(BROKEN)));
            awaitOutput("/project/src/Broken.java: no problems");
            daemon.says(JsonRpc.problems(new DiagnosedFiles(
                    List.of(new FileDiagnostics(CLEAN, List.of(new Diagnosis(Severity.WARNING, 0, 0, "unused")))))));
            awaitOutput("/project/src/Clean.java:1:1: warning: unused");
            daemon.hangsUp();

            assertThat(exitCode(running)).isEqualTo(1);
            assertThat(out().lines())
                    .containsExactly(
                            "/project/src/Broken.java:5:16: error: Type mismatch",
                            "/project/src/Broken.java: no problems",
                            "/project/src/Clean.java:1:1: warning: unused");
            assertThat(daemon.requests()).containsExactly("""
                {"jsonrpc":"2.0","id":1,"method":"watch-all"}\
                """);
        }
    }

    @Test
    void saysNoProblemsWhenTheProjectHasNone() throws Exception {
        try (var daemon = new ScriptedStreamingDaemon(socket(), reply(clean()))) {
            var running = running("diagnostics", "--watch");

            awaitOutput("no problems");
            daemon.hangsUp();

            exitCode(running);
            assertThat(out().lines()).containsExactly("no problems");
        }
    }

    @Test
    void followsTheProjectToo() throws Exception {
        try (var daemon = new ScriptedStreamingDaemon(socket(), reply(clean()))) {
            var running = running("diagnostics", "--all", "--watch");

            awaitOutput("no problems");
            daemon.hangsUp();

            exitCode(running);
            assertThat(daemon.requests()).hasSize(1);
            assertThat(daemon.requests().getFirst()).contains("\"method\":\"watch-all\"");
        }
    }

    @Test
    void followsTheFilesItIsGivenAndShowsThemAsTyped() throws Exception {
        var absolute = Path.of("src/Broken.java").toAbsolutePath().toString();
        var told = new DiagnosedFiles(List.of(
                new FileDiagnostics(absolute, List.of(new Diagnosis(Severity.ERROR, 0, 0, "bad"))),
                new FileDiagnostics(Path.of("src/Main.java").toAbsolutePath().toString(), List.of())));
        try (var daemon = new ScriptedStreamingDaemon(socket(), reply(told))) {
            var running = running("diagnostics", "--watch", "src/Broken.java", "src/Main.java");

            awaitOutput("src/Main.java: no problems");
            daemon.hangsUp();

            exitCode(running);
            assertThat(out().lines()).containsExactly("src/Broken.java:1:1: error: bad", "src/Main.java: no problems");
            assertThat(daemon.requests().getFirst())
                    .contains("\"method\":\"watch\"")
                    .contains("\"files\"");
        }
    }

    @Test
    void printsOneJsonObjectPerLineWithJson() throws Exception {
        try (var daemon = new ScriptedStreamingDaemon(socket(), reply(broken("bad")))) {
            var running = running("--json", "diagnostics", "--watch");

            awaitOutput("\"files\"");
            daemon.says(JsonRpc.problems(clean(BROKEN)));
            daemon.says(JsonRpc.notice("a notice"));
            awaitOutput("{\"notice\":\"a notice\"}");
            daemon.hangsUp();

            exitCode(running);
            assertThat(out().lines())
                    .containsExactly(broken("bad").toJson(), clean(BROKEN).toJson(), "{\"notice\":\"a notice\"}");
        }
    }

    @Test
    void tellsWhatTheDaemonSaysToThePersonWhoReads() throws Exception {
        try (var daemon = new ScriptedStreamingDaemon(socket(), reply(clean()))) {
            var running = running("diagnostics", "--watch");

            daemon.says(JsonRpc.notice("The system cannot follow the files\u001b[31m"));
            awaitOutput("gabier: notice: ");
            daemon.hangsUp();

            exitCode(running);
            assertThat(out())
                    .contains("gabier: notice: The system cannot follow the files")
                    .doesNotContain("\u001b");
        }
    }

    @Test
    void ignoresANotificationOfALaterVersionOfTheDaemon() throws Exception {
        try (var daemon = new ScriptedStreamingDaemon(socket(), reply(clean()))) {
            var running = running("diagnostics", "--watch");

            awaitOutput("no problems");
            daemon.says(JsonRpc.notification("heartbeat", "{}"));
            daemon.says(JsonRpc.problems(clean(BROKEN)));
            awaitOutput("/project/src/Broken.java: no problems");
            daemon.hangsUp();

            exitCode(running);
            assertThat(out().lines()).containsExactly("no problems", "/project/src/Broken.java: no problems");
        }
    }

    @Test
    void endsWithTheProblemTheDaemonEndedTheSubscriptionFor() throws Exception {
        try (var daemon = new ScriptedStreamingDaemon(socket(), reply(clean()))) {
            var running = running("diagnostics", "--watch");

            awaitOutput("no problems");
            daemon.says(JsonRpc.ended(problem("language-server-not-ready", "jdtls is not ready")));

            assertThat(exitCode(running)).isEqualTo(2);
            assertThat(err()).contains("jdtls is not ready").contains("language-server-not-ready");
            assertThat(daemon.clientGone()).isTrue();
        }
    }

    @Test
    void endsWithAProblemAndNoStackTraceWhenTheDaemonGoesAway() throws Exception {
        try (var daemon = new ScriptedStreamingDaemon(socket(), reply(clean()))) {
            var running = running("diagnostics", "--watch");

            awaitOutput("no problems");
            daemon.hangsUp();

            assertThat(exitCode(running)).isEqualTo(1);
            assertThat(err())
                    .contains("the daemon stopped: nothing is followed any more")
                    .contains("daemon-connection-failed")
                    .doesNotContain("\tat ");
        }
    }

    @Test
    void endsWithTheRefusalOfTheDaemonWhenItRefusesTheRequest() throws Exception {
        var refusal = JsonRpc.failure("1", problem("language-server-file-missing", "no such file"));
        try (var daemon = new ScriptedStreamingDaemon(socket(), refusal)) {
            var running = running("diagnostics", "--watch", "src/Missing.java");

            assertThat(exitCode(running)).isEqualTo(2);
            assertThat(out()).isEmpty();
            assertThat(err()).contains("no such file");
            assertThat(daemon.clientGone()).isTrue();
        }
    }

    @Test
    void refusesAFileNameWithALineBreakWithoutAskingTheDaemon() throws IOException {
        try (var daemon = new ScriptedStreamingDaemon(socket(), reply(clean()))) {
            assertThat(runWithSocket("diagnostics", "--watch", "a\nb.java")).isEqualTo(2);

            assertThat(daemon.requests()).isEmpty();
        }
    }
}
