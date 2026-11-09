package eu.gabier.cli.infrastructure.primary;

import static org.assertj.core.api.Assertions.assertThat;

import eu.gabier.cli.Gabier;
import eu.gabier.protocol.Problem;
import eu.gabier.shared.error.domain.ErrorStatus;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** What the cli tells on the standard error while a request waits for a jdtls that is still starting. */
class WaitingForJdtlsTest extends CliTest {

    private static final Duration QUICK = Duration.ofMillis(100);
    private static final String DIAGNOSTICS = "{\"diagnostics\":[]}";

    private static String status(String jdtls) {
        return """
            {"version":"0.1","pid":4242,"uptimeMillis":65432,"project":"/work/demo","jdtls":"%s","imports":%s}\
            """.formatted(jdtls, CliFixtures.IMPORTS);
    }

    /** A run whose notice comes after {@code delay}. */
    private int run(Duration delay, String... args) {
        return Gabier.run(
                args, Map.of("GABIER_SOCKET", socket().toString()), printStream(out), printStream(err), delay);
    }

    /** Waits, for ten seconds at most, until the cli has written its notice. */
    private void awaitNotice() throws InterruptedException {
        var deadline = System.nanoTime() + 10_000_000_000L;
        while (err().isEmpty() && System.nanoTime() < deadline) {
            Thread.sleep(20);
        }
    }

    @Test
    void aRequestThatWaitsForJdtlsToStartSaysSoOnTheStandardErrorAndTheAnswerStaysOnTheStandardOutput()
            throws Exception {
        try (var daemon = new SlowDaemon(socket(), status("starting"), DIAGNOSTICS)) {
            var run = new Thread(() -> run(QUICK, "diagnostics", "/project/Main.java"));
            run.start();

            awaitNotice();
            assertThat(out()).isEmpty();
            daemon.answer();
            run.join();

            assertThat(err().lines())
                    .hasSize(1)
                    .allSatisfy(line -> assertThat(line)
                            .startsWith("gabier: waiting for jdtls")
                            .contains("starting"));
            assertThat(out()).isEmpty();
        }
    }

    @Test
    void aQuickAnswerSaysNothing() throws IOException {
        try (var daemon = daemonAnswering(DIAGNOSTICS)) {
            assertThat(run(Duration.ofSeconds(30), "diagnostics", "/project/Main.java"))
                    .isZero();

            assertThat(err()).isEmpty();
            assertThat(daemon.requests()).hasSize(1);
        }
    }

    @Test
    void aSlowRequestToAJdtlsThatIsReadyGetsNoNotice() throws Exception {
        try (var daemon = new SlowDaemon(socket(), status("ready"), DIAGNOSTICS)) {
            var run = new Thread(() -> run(QUICK, "diagnostics", "/project/Main.java"));
            run.start();

            Thread.sleep(600);
            daemon.answer();
            run.join();

            assertThat(err()).isEmpty();
        }
    }

    @Test
    void theNoticeIsAlsoOnTheStandardErrorWithTheJsonOption() throws Exception {
        try (var daemon = new SlowDaemon(socket(), status("starting"), DIAGNOSTICS)) {
            var run = new Thread(() -> run(QUICK, "--json", "diagnostics", "/project/Main.java"));
            run.start();

            awaitNotice();
            daemon.answer();
            run.join();

            assertThat(err()).startsWith("gabier: waiting for jdtls");
            assertThat(out()).isEqualTo(DIAGNOSTICS + System.lineSeparator());
        }
    }

    @Test
    void aRefusalAfterTheNoticeIsStillTheProblemObjectWithItsHelpPointerAsTheLastLine() throws Exception {
        var refusal = new Problem(
                "urn:gabier:error:language-server-no-such-symbol",
                "No such symbol",
                "No symbol there",
                "language-server-no-such-symbol",
                Map.of(),
                ErrorStatus.BAD_REQUEST);
        try (var daemon = SlowDaemon.refusing(socket(), status("starting"), refusal)) {
            var code = new AtomicInteger();
            var run = new Thread(() -> code.set(run(QUICK, "--json", "rename", "/project/A.java", "1:1", "x")));
            run.start();

            awaitNotice();
            daemon.answer();
            run.join();

            var lines = err().lines().toList();
            assertThat(lines).hasSize(2);
            assertThat(lines.getFirst()).startsWith("gabier: waiting for jdtls");
            assertThat(lines.getLast())
                    .startsWith("{\"type\":\"urn:gabier:error:language-server-no-such-symbol\"")
                    .contains("\"help\":");
            assertThat(code.get()).isEqualTo(2);
            assertThat(out()).isEmpty();
        }
    }
}
