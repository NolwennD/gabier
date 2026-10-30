package fr.craft.gabier.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.craft.gabier.protocol.JsonRpc;
import fr.craft.gabier.protocol.Problem;
import fr.craft.gabier.shared.error.domain.ErrorStatus;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GabierTest {

    private static final String USAGE_DETAIL =
            "Unsupported arguments; usage: gabier [--json] (--version | status | stop)";

    @TempDir
    Path directory;

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    private static String usageText(String arguments) {
        return "Usage error: " + USAGE_DETAIL + " (usage-error; arguments=" + arguments + ")" + System.lineSeparator();
    }

    private static String usageJson(String arguments) {
        return "{\"type\":\"urn:gabier:error:usage-error\",\"title\":\"Usage error\",\"detail\":\"" + USAGE_DETAIL
                + "\",\"key\":\"usage-error\",\"parameters\":{\"arguments\":\"" + arguments + "\"}}"
                + System.lineSeparator();
    }

    private int run(String... args) {
        return run(Map.of(), args);
    }

    private int run(Map<String, String> environment, String... args) {
        return Gabier.run(args, environment, printStream(out), printStream(err));
    }

    private static PrintStream printStream(ByteArrayOutputStream stream) {
        return new PrintStream(stream, true, StandardCharsets.UTF_8);
    }

    private String out() {
        return out.toString(StandardCharsets.UTF_8);
    }

    private String err() {
        return err.toString(StandardCharsets.UTF_8);
    }

    @Nested
    class Version {

        @Test
        void printsTheNameAndTheProtocolVersionOnTheStandardOutput() {
            run("--version");

            assertThat(out()).isEqualTo("gabier 0.1" + System.lineSeparator());
        }

        @Test
        void printsNothingOnTheStandardError() {
            run("--version");

            assertThat(err()).isEmpty();
        }

        @Test
        void exitsWithStatusZero() {
            assertThat(run("--version")).isZero();
        }
    }

    @Nested
    class UnknownUsage {

        @Test
        void unknownArgumentPrintsTheUsageOnTheStandardError() {
            run("--help");

            assertThat(err()).isEqualTo(usageText("--help"));
        }

        @Test
        void unknownArgumentPrintsNothingOnTheStandardOutput() {
            run("--help");

            assertThat(out()).isEmpty();
        }

        @Test
        void unknownArgumentExitsWithStatusTwo() {
            assertThat(run("--help")).isEqualTo(2);
        }

        @Test
        void noArgumentIsAnUnknownUsage() {
            assertThat(run()).isEqualTo(2);
            assertThat(out()).isEmpty();
            assertThat(err()).isEqualTo(usageText(""));
        }

        @Test
        void versionFollowedByAnotherArgumentIsAnUnknownUsage() {
            assertThat(run("--version", "--help")).isEqualTo(2);
            assertThat(out()).isEmpty();
            assertThat(err()).isEqualTo(usageText("--version --help"));
        }

        @Test
        void unknownArgumentFollowedByVersionIsAnUnknownUsage() {
            assertThat(run("--help", "--version")).isEqualTo(2);
            assertThat(out()).isEmpty();
            assertThat(err()).isEqualTo(usageText("--help --version"));
        }

        @Test
        void aCommandFollowedByAnotherArgumentIsAnUnknownUsage() {
            assertThat(run("status", "stop")).isEqualTo(2);
            assertThat(out()).isEmpty();
            assertThat(err()).isEqualTo(usageText("status stop"));
        }
    }

    @Nested
    class JsonOption {

        @Test
        void aUsageErrorIsTheProblemObjectOnTheStandardError() {
            assertThat(run("--json", "--help")).isEqualTo(2);

            assertThat(err()).isEqualTo(usageJson("--help"));
        }

        @Test
        void aUsageErrorPrintsNothingOnTheStandardOutput() {
            run("--json", "--help");

            assertThat(out()).isEmpty();
        }

        @Test
        void canComeAfterTheOtherArguments() {
            assertThat(run("--help", "--json")).isEqualTo(2);

            assertThat(err()).isEqualTo(usageJson("--help"));
        }

        @Test
        void aloneIsAUsageErrorWithNoArgument() {
            assertThat(run("--json")).isEqualTo(2);

            assertThat(err()).isEqualTo(usageJson(""));
        }

        @Test
        void doesNotChangeTheVersion() {
            assertThat(run("--json", "--version")).isZero();

            assertThat(out()).isEqualTo("gabier 0.1" + System.lineSeparator());
            assertThat(err()).isEmpty();
        }

        @Test
        void afterTheVersionDoesNotChangeItEither() {
            assertThat(run("--version", "--json")).isZero();

            assertThat(out()).isEqualTo("gabier 0.1" + System.lineSeparator());
        }
    }

    @Nested
    class Failure {

        private final RuntimeException bug = new IllegalStateException("secret /home/someone/file");

        @Test
        void anUnexpectedExceptionIsAnInternalErrorWithItsOwnExitCode() {
            assertThat(Gabier.fail(bug, false, printStream(err))).isEqualTo(1);
        }

        @Test
        void anUnexpectedExceptionIsReportedAsText() {
            Gabier.fail(bug, false, printStream(err));

            assertThat(err())
                    .isEqualTo("Internal server error: An internal error occurred, and it is not described here "
                            + "(internal-server-error)" + System.lineSeparator());
        }

        @Test
        void anUnexpectedExceptionIsReportedAsJson() {
            Gabier.fail(bug, true, printStream(err));

            assertThat(err())
                    .isEqualTo("{\"type\":\"urn:gabier:error:internal-server-error\","
                            + "\"title\":\"Internal server error\","
                            + "\"detail\":\"An internal error occurred, and it is not described here\","
                            + "\"key\":\"internal-server-error\",\"parameters\":{}}" + System.lineSeparator());
        }

        @Test
        void doesNotLeakTheMessageNorTheStackTrace() {
            Gabier.fail(bug, false, printStream(err));
            Gabier.fail(bug, true, printStream(err));

            assertThat(err())
                    .doesNotContain("secret")
                    .doesNotContain("IllegalStateException")
                    .doesNotContain("\tat ");
        }
    }

    @Nested
    class WithADaemon {

        private static final String STATUS_REQUEST = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"status\"}";
        private static final String STATUS_JSON = "{\"version\":\"0.1\",\"pid\":4242,\"uptimeMillis\":65432}";
        private static final Problem REFUSAL = new Problem(
                "urn:gabier:error:some-key",
                "Some key",
                "It went wrong",
                "some-key",
                Map.of("a", "1"),
                ErrorStatus.BAD_REQUEST);

        private Path socket() {
            return directory.resolve("gabier.sock");
        }

        private int runWithSocket(String... args) {
            return run(Map.of("GABIER_SOCKET", socket().toString()), args);
        }

        @Nested
        class Status {

            @Test
            void saysTheVersionThePidAndTheUptime() throws IOException {
                try (var daemon = new ScriptedDaemon(socket(), JsonRpc.success("1", STATUS_JSON))) {
                    assertThat(runWithSocket("status")).isZero();

                    assertThat(out()).isEqualTo("gabier-daemon 0.1, pid 4242, up 65s" + System.lineSeparator());
                    assertThat(err()).isEmpty();
                    assertThat(daemon.requests()).containsExactly(STATUS_REQUEST);
                }
            }

            @Test
            void givesTheResultAsJsonWithTheJsonOption() throws IOException {
                try (var daemon = new ScriptedDaemon(socket(), JsonRpc.success("1", STATUS_JSON))) {
                    assertThat(runWithSocket("--json", "status")).isZero();

                    assertThat(out()).isEqualTo(STATUS_JSON + System.lineSeparator());
                    assertThat(err()).isEmpty();
                    assertThat(daemon.requests()).hasSize(1);
                }
            }

            @Test
            void reportsARefusalOfTheDaemonAsItsProblemObjectWithTheExitCodeOfItsCategory() throws IOException {
                try (var daemon = new ScriptedDaemon(socket(), JsonRpc.failure("1", REFUSAL))) {
                    assertThat(runWithSocket("status")).isEqualTo(2);

                    assertThat(out()).isEmpty();
                    assertThat(err()).isEqualTo(REFUSAL.toText() + System.lineSeparator());
                    assertThat(daemon.requests()).hasSize(1);
                }
            }

            @Test
            void reportsARefusalOfTheDaemonAsJsonWithTheJsonOption() throws IOException {
                try (var daemon = new ScriptedDaemon(socket(), JsonRpc.failure("1", REFUSAL))) {
                    assertThat(runWithSocket("status", "--json")).isEqualTo(2);

                    assertThat(err()).isEqualTo(REFUSAL.toJson() + System.lineSeparator());
                    assertThat(daemon.requests()).hasSize(1);
                }
            }

            @Test
            void reportsAFailureOfTheDaemonAsAnInternalError() throws IOException {
                var failure = new IllegalStateException("secret");
                try (var daemon = new ScriptedDaemon(socket(), JsonRpc.failure("1", failure))) {
                    assertThat(runWithSocket("status")).isEqualTo(1);

                    assertThat(out()).isEmpty();
                    assertThat(err()).contains("(internal-server-error)").doesNotContain("secret");
                    assertThat(daemon.requests()).hasSize(1);
                }
            }

            @Test
            void reportsAnAnswerThatIsNotAResponseAsAnInternalError() throws IOException {
                try (var daemon = new ScriptedDaemon(socket(), "hello")) {
                    assertThat(runWithSocket("status")).isEqualTo(1);

                    assertThat(err()).contains("(invalid-response)");
                    assertThat(daemon.requests()).hasSize(1);
                }
            }

            @Test
            void reportsAStatusWithoutItsMembersAsAnInternalError() throws IOException {
                try (var daemon = new ScriptedDaemon(socket(), JsonRpc.success("1", "{}"))) {
                    assertThat(runWithSocket("status")).isEqualTo(1);

                    assertThat(err()).contains("(invalid-response)");
                    assertThat(daemon.requests()).hasSize(1);
                }
            }

            @Test
            void reportsADaemonThatHangsUpAsAnInternalError() throws IOException {
                try (var daemon = new ScriptedDaemon(socket(), null)) {
                    assertThat(runWithSocket("status")).isEqualTo(1);

                    assertThat(err()).contains("(daemon-connection-failed)");
                    assertThat(daemon.requests()).hasSize(1);
                }
            }
        }

        @Nested
        class Stop {

            private static final String STOP_RESULT = "{\"stopping\":true}";

            @Test
            void asksTheDaemonToStopAndWaitsUntilItHasHungUp() throws Exception {
                var mayHangUp = new CountDownLatch(1);
                try (var daemon = new ScriptedDaemon(socket(), JsonRpc.success("1", STOP_RESULT), mayHangUp)) {
                    var stopping = CompletableFuture.supplyAsync(() -> runWithSocket("stop"));

                    while (daemon.requests().isEmpty()) {
                        TimeUnit.MILLISECONDS.sleep(10);
                    }
                    // The answer is in, the connection is not closed yet: the daemon is not gone.
                    assertThatThrownBy(() -> stopping.get(300, TimeUnit.MILLISECONDS))
                            .isInstanceOf(TimeoutException.class);

                    mayHangUp.countDown();

                    assertThat(stopping.get(10, TimeUnit.SECONDS)).isZero();
                    assertThat(out()).isEqualTo("gabier-daemon stopped" + System.lineSeparator());
                    assertThat(err()).isEmpty();
                    assertThat(daemon.requests()).containsExactly("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"stop\"}");
                }
            }

            @Test
            void givesTheResultAsJsonWithTheJsonOption() throws IOException {
                try (var daemon = new ScriptedDaemon(socket(), JsonRpc.success("1", STOP_RESULT))) {
                    assertThat(runWithSocket("--json", "stop")).isZero();

                    assertThat(out()).isEqualTo("{\"stopped\":true}" + System.lineSeparator());
                    assertThat(daemon.requests()).hasSize(1);
                }
            }

            @Test
            void reportsARefusalOfTheDaemon() throws IOException {
                try (var daemon = new ScriptedDaemon(socket(), JsonRpc.failure("1", REFUSAL))) {
                    assertThat(runWithSocket("stop")).isEqualTo(2);

                    assertThat(out()).isEmpty();
                    assertThat(err()).isEqualTo(REFUSAL.toText() + System.lineSeparator());
                    assertThat(daemon.requests()).hasSize(1);
                }
            }
        }
    }

    @Nested
    class WithoutADaemon {

        private Path socket() {
            return directory.resolve("gabier.sock");
        }

        /** The run must come back: a client that waits for a daemon that is not there hangs the shell of the user. */
        private int runWithoutHanging(Map<String, String> environment, String... args) throws Exception {
            return CompletableFuture.supplyAsync(() -> run(environment, args)).get(10, TimeUnit.SECONDS);
        }

        private int runWithoutHanging(String... args) throws Exception {
            return runWithoutHanging(Map.of("GABIER_SOCKET", socket().toString()), args);
        }

        @Test
        void statusSaysNoDaemonIsRunningAndExitsWithTheCodeOfARefusal() throws Exception {
            assertThat(runWithoutHanging("status")).isEqualTo(2);

            assertThat(out()).isEmpty();
            assertThat(err())
                    .isEqualTo("Daemon not running: No daemon answers on " + socket()
                            + ": start it with gabier-daemon (daemon-not-running; socket=" + socket() + ")"
                            + System.lineSeparator());
        }

        @Test
        void stopHasNothingToStop() throws Exception {
            assertThat(runWithoutHanging("stop")).isEqualTo(2);

            assertThat(err()).contains("(daemon-not-running; ");
        }

        @Test
        void saysItAsJsonWithTheJsonOption() throws Exception {
            assertThat(runWithoutHanging("--json", "status")).isEqualTo(2);

            assertThat(err())
                    .startsWith("{\"type\":\"urn:gabier:error:daemon-not-running\",")
                    .contains("\"socket\":\"" + socket() + "\"");
        }

        @Test
        void aSocketFileThatNobodyAnswersOnIsNoDaemonEither() throws Exception {
            ServerSocketChannel.open(StandardProtocolFamily.UNIX)
                    .bind(UnixDomainSocketAddress.of(socket()))
                    .close();
            assertThat(socket()).exists();

            assertThat(runWithoutHanging("status")).isEqualTo(2);

            assertThat(err()).contains("(daemon-not-running; ");
        }

        @Test
        void refusesAnOverrideThatIsTooLongWithoutConnecting() throws Exception {
            var tooLong = "/" + "a".repeat(119);

            assertThat(runWithoutHanging(Map.of("GABIER_SOCKET", tooLong), "status"))
                    .isEqualTo(2);

            assertThat(err())
                    .contains("(socket-path-too-long; ")
                    .contains("length=120")
                    .contains("limit=107")
                    .contains("override=GABIER_SOCKET");
        }

        @Test
        void looksForTheSocketInTheRuntimeDirectoryOfTheUserWithoutAnOverride() throws Exception {
            var runtimeDirectory = directory.resolve("run");
            Files.createDirectories(runtimeDirectory.resolve("gabier"));
            var expected = runtimeDirectory.resolve("gabier/gabier.sock");
            var status = JsonRpc.success("1", "{\"version\":\"0.1\",\"pid\":1,\"uptimeMillis\":0}");
            try (var daemon = new ScriptedDaemon(expected, status)) {
                var code = run(Map.of("XDG_RUNTIME_DIR", runtimeDirectory.toString()), "status");

                assertThat(code).isZero();
                assertThat(daemon.requests()).hasSize(1);
            }
        }
    }
}
