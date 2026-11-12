package eu.gabier.cli.infrastructure.primary;

import static org.assertj.core.api.Assertions.assertThat;

import eu.gabier.protocol.JsonRpc;
import eu.gabier.protocol.Problem;
import eu.gabier.protocol.SelectedTests;
import eu.gabier.shared.error.domain.ErrorStatus;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * {@code affected-tests --watch} against a daemon of the tests that tells what changed when the test says so, on a real
 * socket: the command runs in a thread while the test reads its output as it comes.
 */
class WatchingAffectedTestsTest extends CliTest {

    private static final String REQUEST = """
        {"jsonrpc":"2.0","id":1,"method":"watch-affected-tests"}\
        """;

    private static String telling(String... tests) {
        return JsonRpc.affectedTests(new SelectedTests(List.of(tests)));
    }

    private static String reply(String... tests) {
        return JsonRpc.success("1", new SelectedTests(List.of(tests)).toJson());
    }

    private static Problem problem(String key, String detail) {
        return new Problem("urn:gabier:error:" + key, "A problem", detail, key, Map.of(), ErrorStatus.BAD_REQUEST);
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

    private void awaitError(String text) throws InterruptedException {
        var deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (!err().contains(text) && System.nanoTime() < deadline) {
            Thread.sleep(5);
        }
        assertThat(err()).contains(text);
    }

    private int exitCode(CompletableFuture<Integer> running) throws Exception {
        return running.get(10, TimeUnit.SECONDS);
    }

    @Test
    void printsNothingWhenNoTestIsAffectedThenTheTestsOfEachChangeEachLineAsSoonAsItComes() throws Exception {
        try (var daemon = new ScriptedStreamingDaemon(socket(), reply())) {
            var running = running("affected-tests", "--watch");

            daemon.says(telling("demo.LedgerTest", "demo.app.BankTest"));
            awaitOutput("demo.app.BankTest");
            daemon.says(telling("demo.LedgerTest"));
            daemon.hangsUp();

            assertThat(exitCode(running)).isEqualTo(1);
            assertThat(out().lines()).containsExactly("demo.LedgerTest", "demo.app.BankTest", "demo.LedgerTest");
            assertThat(daemon.requests()).containsExactly(REQUEST);
        }
    }

    @Test
    void printsTheTestsOfTheChangesSoFarWhenItStarts() throws Exception {
        try (var daemon = new ScriptedStreamingDaemon(socket(), reply("demo.LedgerTest"))) {
            var running = running("affected-tests", "--watch");

            awaitOutput("demo.LedgerTest");
            daemon.hangsUp();

            exitCode(running);
            assertThat(out().lines()).containsExactly("demo.LedgerTest");
        }
    }

    @Test
    void printsOneJsonObjectPerAnswerWithJsonAndNothingWhenNoTestIsAffected() throws Exception {
        try (var daemon = new ScriptedStreamingDaemon(socket(), reply())) {
            var running = running("--json", "affected-tests", "--watch");

            daemon.says(telling("demo.LedgerTest"));
            awaitOutput("demo.LedgerTest");
            daemon.says(JsonRpc.notice("a notice"));
            awaitOutput("{\"notice\":\"a notice\"}");
            daemon.hangsUp();

            exitCode(running);
            assertThat(out().lines()).containsExactly("{\"tests\":[\"demo.LedgerTest\"]}", "{\"notice\":\"a notice\"}");
        }
    }

    @Test
    void neutralisesTheNameOfATestAsItComesFromAClassFile() throws Exception {
        try (var daemon = new ScriptedStreamingDaemon(socket(), reply())) {
            var running = running("affected-tests", "--watch");

            daemon.says(telling("demo.Evil\u001b[31mTest"));
            awaitOutput("demo.Evil");
            daemon.hangsUp();

            exitCode(running);
            assertThat(out()).doesNotContain("\u001b");
        }
    }

    @Test
    void tellsOnTheStandardErrorWhyAChangeCannotBeAnsweredAndGoesOn() throws Exception {
        try (var daemon = new ScriptedStreamingDaemon(socket(), reply())) {
            var running = running("affected-tests", "--watch");

            daemon.says(JsonRpc.refusal(problem("testing-compilation-failed", "These files do not compile: A.java")));
            awaitError("testing-compilation-failed");
            daemon.says(telling("demo.LedgerTest"));
            awaitOutput("demo.LedgerTest");
            daemon.hangsUp();

            assertThat(exitCode(running)).isEqualTo(1);
            assertThat(err()).contains("These files do not compile: A.java");
            assertThat(out().lines()).containsExactly("demo.LedgerTest");
        }
    }

    @Test
    void tellsTheRefusalAsAProblemObjectWithJson() throws Exception {
        var refused = problem("testing-compilation-failed", "These files do not compile");
        try (var daemon = new ScriptedStreamingDaemon(socket(), reply())) {
            var running = running("--json", "affected-tests", "--watch");

            daemon.says(JsonRpc.refusal(refused));
            awaitError("testing-compilation-failed");
            daemon.hangsUp();

            exitCode(running);
            assertThat(err().lines().findFirst()).contains(refused.toJson());
            assertThat(out()).isEmpty();
        }
    }

    @Test
    void tellsWhatTheDaemonSaysToThePersonWhoReads() throws Exception {
        try (var daemon = new ScriptedStreamingDaemon(socket(), reply())) {
            var running = running("affected-tests", "--watch");

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
        try (var daemon = new ScriptedStreamingDaemon(socket(), reply())) {
            var running = running("affected-tests", "--watch");

            daemon.says(JsonRpc.notification("heartbeat", "{}"));
            daemon.says(telling("demo.LedgerTest"));
            awaitOutput("demo.LedgerTest");
            daemon.hangsUp();

            exitCode(running);
            assertThat(out().lines()).containsExactly("demo.LedgerTest");
        }
    }

    @Test
    void endsWithTheProblemTheDaemonEndedTheSubscriptionFor() throws Exception {
        try (var daemon = new ScriptedStreamingDaemon(socket(), reply())) {
            var running = running("affected-tests", "--watch");

            daemon.says(JsonRpc.ended(problem("language-server-not-ready", "jdtls is not ready")));

            assertThat(exitCode(running)).isEqualTo(2);
            assertThat(err()).contains("jdtls is not ready").contains("language-server-not-ready");
            assertThat(daemon.clientGone()).isTrue();
        }
    }

    @Test
    void endsWithAProblemAndNoStackTraceWhenTheDaemonGoesAway() throws Exception {
        try (var daemon = new ScriptedStreamingDaemon(socket(), reply())) {
            var running = running("affected-tests", "--watch");

            daemon.says(telling("demo.LedgerTest"));
            awaitOutput("demo.LedgerTest");
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
        var refusal = JsonRpc.failure("1", problem("language-server-not-ready", "jdtls is not ready"));
        try (var daemon = new ScriptedStreamingDaemon(socket(), refusal)) {
            var running = running("affected-tests", "--watch");

            assertThat(exitCode(running)).isEqualTo(2);
            assertThat(out()).isEmpty();
            assertThat(err()).contains("jdtls is not ready");
            assertThat(daemon.clientGone()).isTrue();
        }
    }

    @Test
    void refusesAFileAndDryRunAsAUsageErrorWithoutAskingTheDaemon() throws IOException {
        try (var daemon = new ScriptedStreamingDaemon(socket(), reply())) {
            assertThat(runWithSocket("affected-tests", "--watch", "src/Ledger.java"))
                    .isEqualTo(2);
            assertThat(err()).contains("usage-error");
            assertThat(runWithSocket("affected-tests", "--watch", "--dry-run")).isEqualTo(2);

            assertThat(daemon.requests()).isEmpty();
        }
    }
}
