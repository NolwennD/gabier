package eu.gabier.cli.infrastructure.primary;

import static org.assertj.core.api.Assertions.assertThat;

import eu.gabier.cli.Gabier;
import eu.gabier.protocol.JsonRpc;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * {@code gabier start}: the daemon of the project is started when none runs and jdtls with it, and the answer comes at
 * once; with {@code --wait} it comes when jdtls is ready, or is a refusal when jdtls failed or the time given passed.
 */
class StartingJdtlsTest extends CliTest {

    private static final String START_REQUEST = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"start\"}";
    private static final String STATUS_REQUEST = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"status\"}";
    private static final Duration QUICK = Duration.ofMillis(100);
    private static final Duration LONG = Duration.ofSeconds(10);
    private static final String STARTING = status("starting", "");
    private static final String READY = status("ready", "");

    private static String status(String jdtls, String more) {
        return """
            {"version":"0.1","pid":4242,"uptimeMillis":65432,"project":"/work/demo","jdtls":"%s","imports":%s%s}\
            """.formatted(jdtls, CliFixtures.IMPORTS, more);
    }

    private static String starting(String phase, String message, int spentMillis) {
        return status(
                "starting",
                ",\"jdtlsProgress\":{\"phase\":\"%s\",\"message\":\"%s\",\"spentMillis\":%d}"
                        .formatted(phase, message, spentMillis));
    }

    private Path bin() {
        return directory.resolve("bin");
    }

    private Path log() {
        return directory.resolve("gabier.sock.log");
    }

    private Map<String, String> environmentWithTheDaemonOnThePath() {
        return Map.of("GABIER_SOCKET", socket().toString(), "PATH", bin().toString());
    }

    private int run(Duration noticeDelay, String... args) {
        return Gabier.run(
                args, Map.of("GABIER_SOCKET", socket().toString()), printStream(out), printStream(err), noticeDelay);
    }

    @AfterEach
    void endTheDaemon() throws IOException {
        FakeDaemonScripts.end(log());
    }

    @Nested
    class WithoutWaiting {

        @Test
        void startsTheDaemonWhenNoneRunsAndAnswersAtOnceThatJdtlsIsStartingWithTheSocketAndTheLog() throws IOException {
            FakeDaemonScripts.installListening(bin());

            assertThat(run(environmentWithTheDaemonOnThePath(), "start")).isZero();

            assertThat(out())
                    .isEqualTo("gabier-daemon started, pid 4242, project /work/demo, jdtls starting; socket " + socket()
                            + ", log " + log() + System.lineSeparator());
            assertThat(err()).isEmpty();
            assertThat(Files.readString(log())).startsWith("listening ");
        }

        @Test
        void givesTheSameAsJsonWithTheJsonOption() throws IOException {
            FakeDaemonScripts.installListening(bin());

            assertThat(run(environmentWithTheDaemonOnThePath(), "--json", "start"))
                    .isZero();

            assertThat(out()).isEqualTo("""
                {"daemon":"started","version":"0.1","pid":4242,"project":"/work/demo","jdtls":"starting",\
                "socket":"%s","log":"%s"}\
                """.formatted(socket(), log()) + System.lineSeparator());
            assertThat(err()).isEmpty();
        }

        @Test
        void aDaemonThatRunsGetsTheRequestAndNoneIsStarted() throws IOException {
            try (var daemon = daemonAnswering(STARTING)) {
                assertThat(runWithSocket("start")).isZero();

                assertThat(out())
                        .isEqualTo("gabier-daemon running, pid 4242, project /work/demo, jdtls starting; socket "
                                + socket() + System.lineSeparator());
                assertThat(daemon.requests()).containsExactly(START_REQUEST);
                assertThat(log()).doesNotExist();
            }
        }

        @Test
        void aJdtlsThatIsReadyIsAnsweredReady() throws IOException {
            try (var daemon = daemonAnswering(READY)) {
                assertThat(runWithSocket("start")).isZero();

                assertThat(out())
                        .isEqualTo("gabier-daemon running, pid 4242, project /work/demo, jdtls ready; socket "
                                + socket() + System.lineSeparator());
                assertThat(daemon.requests()).containsExactly(START_REQUEST);
            }
        }

        @Test
        void aRunningDaemonIsAnsweredAsJsonWithoutTheLog() throws IOException {
            try (var daemon = daemonAnswering(READY)) {
                assertThat(runWithSocket("--json", "start")).isZero();

                assertThat(out()).isEqualTo("""
                    {"daemon":"running","version":"0.1","pid":4242,"project":"/work/demo","jdtls":"ready",\
                    "socket":"%s"}\
                    """.formatted(socket()) + System.lineSeparator());
            }
        }

        @Test
        void saysWhyJdtlsFailedWhenItFailedAtOnce() throws IOException {
            var failed = status("failed", ",\"jdtlsFailure\":\"no launcher\\u001b[2J\"");
            try (var daemon = daemonAnswering(failed)) {
                assertThat(runWithSocket("start")).isZero();
                assertThat(out())
                        .isEqualTo("gabier-daemon running, pid 4242, project /work/demo, jdtls failed"
                                + " (no launcher\\u001b[2J); socket " + socket() + System.lineSeparator());

                assertThat(runWithSocket("--json", "start")).isZero();
                assertThat(out())
                        .endsWith("\"jdtls\":\"failed\",\"jdtlsFailure\":\"no launcher\\u001b[2J\"," + "\"socket\":\""
                                + socket() + "\"}" + System.lineSeparator());
            }
        }

        @Test
        void aDaemonThatCannotBeStartedIsTheRefusalOfTheOtherCommands() throws IOException {
            Files.createDirectories(bin());

            assertThat(run(environmentWithTheDaemonOnThePath(), "start")).isEqualTo(1);

            assertThat(out()).isEmpty();
            assertThat(err())
                    .startsWith("Daemon start failed: The daemon did not start: gabier-daemon was not found")
                    .contains("(daemon-start-failed; socket=" + socket() + ")");
        }

        @Test
        void aRefusalOfTheDaemonIsItsProblemObject() throws IOException {
            try (var daemon = new ScriptedDaemon(socket(), JsonRpc.failure("1", CliFixtures.REFUSAL))) {
                assertThat(runWithSocket("start")).isEqualTo(2);

                assertThat(out()).isEmpty();
                assertThat(err()).isEqualTo(CliFixtures.REFUSAL.toText() + System.lineSeparator());
            }
        }
    }

    @Nested
    class Waiting {

        @Test
        void returnsAtOnceWhenJdtlsIsReadyAndAsksNothingMore() throws IOException {
            try (var daemon = daemonAnswering(READY)) {
                assertThat(runWithSocket("start", "--wait")).isZero();

                assertThat(out()).startsWith("gabier-daemon running, pid 4242, project /work/demo, jdtls ready");
                assertThat(daemon.requests()).containsExactly(START_REQUEST);
            }
        }

        @Test
        void returnsOnceJdtlsIsReadyAndSaysNothingOnTheStandardErrorWhenItIsQuick() throws IOException {
            try (var daemon = new StartingJdtlsDaemon(socket(), STARTING, STARTING, READY)) {
                assertThat(runWithSocket("start", "--wait")).isZero();

                assertThat(out())
                        .isEqualTo("gabier-daemon running, pid 4242, project /work/demo, jdtls ready; socket "
                                + socket() + System.lineSeparator());
                assertThat(err()).isEmpty();
                assertThat(daemon.requests()).containsExactly(START_REQUEST, STATUS_REQUEST, STATUS_REQUEST);
            }
        }

        @Test
        void writesWhereJdtlsIsOnTheStandardErrorWhileItWaitsAndNeverOnTheStandardOutput() throws Exception {
            var importing = starting("importing", "Configure project :core", 47_000);
            try (var daemon = new StartingJdtlsDaemon(socket(), STARTING, importing)) {
                var code = new AtomicInteger(-1);
                var run = new Thread(() -> code.set(run(QUICK, "start", "--wait")));
                run.start();

                awaitNotice();
                assertThat(out()).isEmpty();
                daemon.thenSays(READY);
                run.join();

                assertThat(code.get()).isZero();
                assertThat(err().lines())
                        .isNotEmpty()
                        .allSatisfy(line -> assertThat(line)
                                .startsWith("gabier: waiting for jdtls (starting")
                                .contains("importing the project for 47s: Configure project :core"));
                assertThat(out())
                        .isEqualTo("gabier-daemon running, pid 4242, project /work/demo, jdtls ready; socket "
                                + socket() + System.lineSeparator());
            }
        }

        @Test
        void keepsTheStandardOutputToTheJsonResultWithTheJsonOption() throws Exception {
            try (var daemon = new StartingJdtlsDaemon(socket(), STARTING, STARTING)) {
                var run = new Thread(() -> run(QUICK, "--json", "start", "--wait"));
                run.start();

                awaitNotice();
                daemon.thenSays(READY);
                run.join();

                assertThat(err()).startsWith("gabier: waiting for jdtls");
                assertThat(out()).isEqualTo("""
                    {"daemon":"running","version":"0.1","pid":4242,"project":"/work/demo","jdtls":"ready",\
                    "socket":"%s"}\
                    """.formatted(socket()) + System.lineSeparator());
            }
        }

        /** Waits, for ten seconds at most, until the cli has written its notice. */
        private void awaitNotice() throws InterruptedException {
            var deadline = System.nanoTime() + 10_000_000_000L;
            while (err().isEmpty() && System.nanoTime() < deadline) {
                Thread.sleep(20);
            }
        }

        @Test
        void refusesWithTheKeyAndTheLastStateWhenTheTimeGivenPasses() throws IOException {
            var importing = starting("importing", "Configure project :core", 4_000);
            try (var daemon = new StartingJdtlsDaemon(socket(), STARTING, importing)) {
                var started = System.nanoTime();

                assertThat(runWithSocket("start", "--wait", "--timeout=1")).isEqualTo(1);

                assertThat(Duration.ofNanos(System.nanoTime() - started)).isBetween(Duration.ofMillis(900), LONG);
                assertThat(out()).isEmpty();
                assertThat(err())
                        .isEqualTo("Jdtls start timeout: jdtls was not ready after 1 seconds (starting,"
                                + " importing the project for 4s: Configure project :core); it keeps starting in the"
                                + " daemon: run gabier start --wait again, or look at gabier status"
                                + " (jdtls-start-timeout; jdtls=starting, progress=importing the project for 4s:"
                                + " Configure project :core, project=/work/demo, timeoutSeconds=1)"
                                + System.lineSeparator());
            }
        }

        @Test
        void theTimeoutRefusalIsAProblemObjectWithTheJsonOption() throws IOException {
            try (var daemon = new StartingJdtlsDaemon(socket(), STARTING, STARTING)) {
                assertThat(runWithSocket("--json", "start", "--wait", "--timeout=1"))
                        .isEqualTo(1);

                assertThat(out()).isEmpty();
                assertThat(err())
                        .startsWith("{\"type\":\"urn:gabier:error:jdtls-start-timeout\"")
                        .contains("\"key\":\"jdtls-start-timeout\"")
                        .contains("\"parameters\":{\"jdtls\":\"starting\",\"project\":\"/work/demo\","
                                + "\"timeoutSeconds\":\"1\"}")
                        .doesNotContain("\"help\"");
            }
        }

        @Test
        void writesOutTheEscapesOfWhatJdtlsWasDoingInTheTimeoutRefusal() throws IOException {
            try (var daemon =
                    new StartingJdtlsDaemon(socket(), STARTING, starting("launching", "x\\u001b[2Jforged", 1_000))) {
                assertThat(runWithSocket("start", "--wait", "--timeout=1")).isEqualTo(1);

                assertThat(err()).contains("x\\u001b[2Jforged").doesNotContain("\u001b");
            }
        }

        @Test
        void refusesWithTheKeyOfTheOtherRequestsAndTheReasonWhenJdtlsFails() throws IOException {
            var failed = status("failed", ",\"jdtlsFailure\":\"jdtls exited with code 137\"");
            try (var daemon = new StartingJdtlsDaemon(socket(), STARTING, STARTING, failed)) {
                assertThat(runWithSocket("start", "--wait")).isEqualTo(1);

                assertThat(out()).isEmpty();
                assertThat(err())
                        .isEqualTo("Jdtls start failed: jdtls did not start: jdtls exited with code 137"
                                + " (jdtls-start-failed; project=/work/demo)" + System.lineSeparator());
            }
        }

        @Test
        void saysSoWhenTheDaemonDoesNotKnowWhyJdtlsFailed() throws IOException {
            try (var daemon = daemonAnswering(status("failed", ""))) {
                assertThat(runWithSocket("--json", "start", "--wait")).isEqualTo(1);

                assertThat(err())
                        .contains("\"key\":\"jdtls-start-failed\"")
                        .contains("jdtls did not start (the daemon does not know why)");
            }
        }

        @Test
        void aDaemonThatGoesAwayWhileItWaitsIsAConnectionFailure() throws IOException {
            try (var daemon = new StartingJdtlsDaemon(socket(), STARTING, STARTING)) {
                daemon.stopsListeningAfterTheStart();

                assertThat(runWithSocket("start", "--wait")).isEqualTo(1);

                assertThat(out()).isEmpty();
                assertThat(err())
                        .startsWith("Daemon connection failed: The connection to the daemon failed: the daemon"
                                + " stopped answering while jdtls was starting")
                        .contains("(daemon-connection-failed)");
            }
        }

        @Test
        void aWaitThatIsInterruptedWhileItAwaitsTheStatusIsAConnectionFailureAndKeepsTheInterruption()
                throws Exception {
            try (var daemon = new StartingJdtlsDaemon(socket(), STARTING)) {
                daemon.staysSilentOnStatus();
                var code = new AtomicInteger(-1);
                var interrupted = new AtomicBoolean();
                var run = new Thread(() -> {
                    code.set(runWithSocket("start", "--wait"));
                    interrupted.set(Thread.currentThread().isInterrupted());
                });
                run.start();

                // The status is asked and not answered: the cli is blocked on the connection, or about to be.
                assertThat(daemon.awaitsAStatusRequest()).isTrue();
                run.interrupt();
                run.join();

                assertThat(code.get()).isEqualTo(1);
                assertThat(interrupted).isTrue();
                assertThat(out()).isEmpty();
                assertThat(err())
                        .startsWith("Daemon connection failed: The connection to the daemon failed: the connection"
                                + " was lost")
                        .contains("(daemon-connection-failed)");
            }
        }

        @Test
        void aRefusalOfTheDaemonToAStatusIsItsProblemObject() throws IOException {
            try (var daemon = new StartingJdtlsDaemon(socket(), STARTING, JsonRpc.failure("1", CliFixtures.REFUSAL))) {
                assertThat(runWithSocket("start", "--wait")).isEqualTo(2);

                assertThat(out()).isEmpty();
                assertThat(err()).isEqualTo(CliFixtures.REFUSAL.toText() + System.lineSeparator());
            }
        }
    }

    static Stream<List<String>> commandLinesThatAreNotUsage() {
        return Stream.of(
                List.of("start", "extra"),
                List.of("start", "--dry-run"),
                List.of("start", "--timeout=5"),
                List.of("start", "--wait", "extra"));
    }

    @ParameterizedTest
    @MethodSource("commandLinesThatAreNotUsage")
    void aCommandLineThatIsNotTheUsageIsAUsageErrorAndStartsNothing(List<String> words) throws IOException {
        var marker = directory.resolve("started");
        FakeDaemonScripts.install(bin(), "echo started > '" + marker + "'");

        assertThat(run(environmentWithTheDaemonOnThePath(), words.toArray(String[]::new)))
                .isEqualTo(2);

        assertThat(out()).isEmpty();
        assertThat(err()).startsWith("Usage error: Unsupported arguments").contains("(usage-error; arguments=");
        assertThat(marker).doesNotExist();
    }

    static Stream<String> timeoutsThatAreNotWholeSecondsFromOne() {
        return Stream.of("0", "-3", "abc", "1.5", "", "99999999999");
    }

    @ParameterizedTest
    @MethodSource("timeoutsThatAreNotWholeSecondsFromOne")
    void aTimeoutThatIsNotAWholeNumberOfSecondsFromOneIsAUsageErrorThatNamesIt(String timeout) throws IOException {
        var marker = directory.resolve("started");
        FakeDaemonScripts.install(bin(), "echo started > '" + marker + "'");

        assertThat(run(environmentWithTheDaemonOnThePath(), "start", "--wait", "--timeout=" + timeout))
                .isEqualTo(2);

        assertThat(err())
                .startsWith("Usage error: Invalid timeout \"" + timeout + "\"; expected a whole number of seconds"
                        + " from 1, as in --timeout=120")
                .contains("timeout=" + timeout + ")");
        assertThat(marker).doesNotExist();
    }

    @Test
    void theHelpOfTheCommandGivesBothFormsOfItsUsage() {
        assertThat(run("start", "--help")).isZero();

        assertThat(out()).contains("gabier [--json] start --wait [--timeout=<seconds>]");
    }
}
