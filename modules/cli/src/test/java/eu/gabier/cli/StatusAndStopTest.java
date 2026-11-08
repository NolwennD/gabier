package eu.gabier.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.gabier.protocol.JsonRpc;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class StatusAndStopTest extends CliTest {

    private static final String STATUS_REQUEST = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"status\"}";
    private static final String STATUS_JSON = """
        {"version":"0.1","pid":4242,"uptimeMillis":65432,"project":"/work/demo","jdtls":"starting","imports":%s}\
        """.formatted(CliFixtures.IMPORTS);
    private static final String IMPORT_LINES = String.join(
            System.lineSeparator(),
            "imports:",
            "  java.completion.importOrder = [\"java\", \"javax\", \"org\", \"com\"] (default of jdtls)",
            "  java.sources.organizeImports.starThreshold = 99 (default of jdtls)",
            "  java.sources.organizeImports.staticStarThreshold = 99 (default of jdtls)",
            "");

    @Nested
    class Status {

        @Test
        void saysTheVersionThePidTheUptimeTheProjectAndWhereJdtlsIs() throws IOException {
            try (var daemon = daemonAnswering(STATUS_JSON)) {
                assertThat(runWithSocket("status")).isZero();

                assertThat(out())
                        .isEqualTo("gabier-daemon 0.1, pid 4242, up 65s, project /work/demo, jdtls starting"
                                + System.lineSeparator() + IMPORT_LINES);
                assertThat(err()).isEmpty();
                assertThat(daemon.requests()).containsExactly(STATUS_REQUEST);
            }
        }

        @Test
        void saysWhatJdtlsIsDoingAndForHowLongWhileItStarts() throws IOException {
            var status = """
                {"version":"0.1","pid":4242,"uptimeMillis":65432,"project":"/work/demo","jdtls":"starting","imports":%s,\
                "jdtlsProgress":{"phase":"importing","message":"Configure project :core","spentMillis":47250}}\
                """.formatted(CliFixtures.IMPORTS);
            try (var daemon = daemonAnswering(status)) {
                assertThat(runWithSocket("status")).isZero();

                assertThat(out())
                        .isEqualTo("gabier-daemon 0.1, pid 4242, up 65s, project /work/demo, jdtls starting"
                                + " (importing the project for 47s: Configure project :core)" + System.lineSeparator()
                                + IMPORT_LINES);
                assertThat(runWithSocket("--json", "status")).isZero();
                assertThat(out()).endsWith(status + System.lineSeparator());
            }
        }

        @Test
        void writesOutTheEscapesOfWhatJdtlsSaysItIsDoing() throws IOException {
            var status = """
                {"version":"0.1","pid":4242,"uptimeMillis":65432,"project":"/work/demo","jdtls":"starting","imports":%s,\
                "jdtlsProgress":{"phase":"launching","message":"x\\u001b[2J\\nforged","spentMillis":1000}}\
                """.formatted(CliFixtures.IMPORTS);
            try (var daemon = daemonAnswering(status)) {
                assertThat(runWithSocket("status")).isZero();

                assertThat(out().lines().findFirst())
                        .hasValueSatisfying(line ->
                                assertThat(line).endsWith("(launching jdtls for 1s: x\\u001b[2J\\u000aforged)"));
            }
        }

        @Test
        void saysWhichFileEachImportSettingComesFrom() throws IOException {
            var status = """
                {"version":"0.1","pid":4242,"uptimeMillis":65432,"project":"/work/demo","jdtls":"ready","imports":\
                {"importOrder":{"value":["#",""],"source":".vscode"},\
                "starThreshold":{"value":2147483647,"source":".settings"},\
                "staticStarThreshold":{"value":99,"source":"default"}}}\
                """;
            try (var daemon = daemonAnswering(status)) {
                assertThat(runWithSocket("status")).isZero();

                assertThat(out().lines())
                        .containsExactly(
                                "gabier-daemon 0.1, pid 4242, up 65s, project /work/demo, jdtls ready",
                                "imports:",
                                "  java.completion.importOrder = [\"#\", \"\"] (.vscode)",
                                "  java.sources.organizeImports.starThreshold = 2147483647 (.settings)",
                                "  java.sources.organizeImports.staticStarThreshold = 99 (default of jdtls)");
            }
        }

        @Test
        void showsTheImportOrderOfTheProjectWithItsControlCharactersEscaped() throws IOException {
            var status = """
                {"version":"0.1","pid":4242,"uptimeMillis":65432,"project":"/work/demo","jdtls":"ready","imports":\
                {"importOrder":{"value":["a\\u001b[2Jb"],"source":".vscode"},\
                "starThreshold":{"value":99,"source":"default"},\
                "staticStarThreshold":{"value":99,"source":"default"}}}\
                """;
            try (var daemon = daemonAnswering(status)) {
                assertThat(runWithSocket("status")).isZero();

                assertThat(out()).contains("[\"a\\\\u001b[2Jb\"] (.vscode)").doesNotContain("\u001b");
            }
        }

        @Test
        void showsTheProjectWithItsControlCharactersEscaped() throws IOException {
            var status = """
                {"version":"0.1","pid":4242,"uptimeMillis":65432,"project":"/work/\\u001b[2Jdemo","jdtls":"ready","imports":%s}\
                """.formatted(CliFixtures.IMPORTS);
            try (var daemon = daemonAnswering(status)) {
                assertThat(runWithSocket("status")).isZero();

                assertThat(out())
                        .isEqualTo("gabier-daemon 0.1, pid 4242, up 65s, project /work/\\u001b[2Jdemo, jdtls ready"
                                + System.lineSeparator() + IMPORT_LINES);
            }
        }

        @Test
        void saysWhyJdtlsFailedWhenTheDaemonKnows() throws IOException {
            var status = """
                {"version":"0.1","pid":4242,"uptimeMillis":65432,"project":"/work/demo","jdtls":"failed",\
                "imports":%s,"jdtlsFailure":"jdtls exited with code 137 after it was ready"}\
                """.formatted(CliFixtures.IMPORTS);
            try (var daemon = daemonAnswering(status)) {
                assertThat(runWithSocket("status")).isZero();

                assertThat(out())
                        .isEqualTo("gabier-daemon 0.1, pid 4242, up 65s, project /work/demo, jdtls failed"
                                + " (jdtls exited with code 137 after it was ready)" + System.lineSeparator()
                                + IMPORT_LINES);
            }
        }

        @Test
        void showsTheReasonOfJdtlsWithItsControlCharactersEscaped() throws IOException {
            var status = """
                {"version":"0.1","pid":4242,"uptimeMillis":65432,"project":"/work/demo","jdtls":"failed",\
                "imports":%s,"jdtlsFailure":"red\\u001b[2Jgone \\\\ back"}\
                """.formatted(CliFixtures.IMPORTS);
            try (var daemon = daemonAnswering(status)) {
                assertThat(runWithSocket("status")).isZero();

                assertThat(out())
                        .isEqualTo("gabier-daemon 0.1, pid 4242, up 65s, project /work/demo, jdtls failed"
                                + " (red\\u001b[2Jgone \\\\ back)" + System.lineSeparator() + IMPORT_LINES);
            }
        }

        @Test
        void givesTheReasonOfJdtlsExactlyAsJsonWithTheJsonOption() throws IOException {
            var status = """
                {"version":"0.1","pid":4242,"uptimeMillis":65432,"project":"/work/demo","jdtls":"failed",\
                "imports":%s,"jdtlsFailure":"red\\u001b[2Jgone \\\\ back"}\
                """.formatted(CliFixtures.IMPORTS);
            try (var daemon = daemonAnswering(status)) {
                assertThat(runWithSocket("--json", "status")).isZero();

                assertThat(out()).isEqualTo(status + System.lineSeparator());
            }
        }

        @Test
        void showsAHostileVersionWithItsControlCharactersEscaped() throws IOException {
            var status = """
                {"version":"\\u001b[2J\\u3164","pid":4242,"uptimeMillis":65432,"project":"/work/demo","jdtls":"ready","imports":%s}\
                """.formatted(CliFixtures.IMPORTS);
            try (var daemon = daemonAnswering(status)) {
                assertThat(runWithSocket("status")).isZero();

                assertThat(out())
                        .isEqualTo("gabier-daemon \\u001b[2J\\u3164, pid 4242, up 65s, project /work/demo, jdtls ready"
                                + System.lineSeparator() + IMPORT_LINES);
            }
        }

        @Test
        void givesTheResultAsJsonWithTheJsonOption() throws IOException {
            try (var daemon = daemonAnswering(STATUS_JSON)) {
                assertThat(runWithSocket("--json", "status")).isZero();

                assertThat(out()).isEqualTo(STATUS_JSON + System.lineSeparator());
                assertThat(err()).isEmpty();
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
            try (var daemon = daemonAnswering(STOP_RESULT)) {
                assertThat(runWithSocket("--json", "stop")).isZero();

                assertThat(out()).isEqualTo("{\"stopped\":true}" + System.lineSeparator());
                assertThat(daemon.requests()).hasSize(1);
            }
        }
    }
}
