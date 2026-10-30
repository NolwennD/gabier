package fr.craft.gabier.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.craft.gabier.protocol.FileDiagnostics;
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
import java.util.HashMap;
import java.util.List;
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
            "Unsupported arguments; usage: gabier [--json] (--version | status | stop | diagnostics <file>)";

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

        @Test
        void diagnosticsWithoutAFileIsAnUnknownUsage() {
            assertThat(run("diagnostics")).isEqualTo(2);
            assertThat(out()).isEmpty();
            assertThat(err()).isEqualTo(usageText("diagnostics"));
        }

        @Test
        void diagnosticsOfTwoFilesIsAnUnknownUsage() {
            assertThat(run("diagnostics", "A.java", "B.java")).isEqualTo(2);
            assertThat(out()).isEmpty();
            assertThat(err()).isEqualTo(usageText("diagnostics A.java B.java"));
        }

        @Test
        void aFileWithoutTheDiagnosticsCommandIsAnUnknownUsage() {
            assertThat(run("A.java", "diagnostics")).isEqualTo(2);
            assertThat(err()).isEqualTo(usageText("A.java diagnostics"));
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
        void runReportsAnExceptionThatNobodyExpectedAsAnInternalErrorInsteadOfLettingItEscape() {
            Map<String, String> environment = new HashMap<>() {
                @Override
                public String get(Object key) {
                    throw bug;
                }
            };

            var exit = Gabier.run(new String[] {"status"}, environment, printStream(out), printStream(err));

            assertThat(exit).isEqualTo(1);
            assertThat(out()).isEmpty();
            assertThat(err())
                    .isEqualTo("Internal server error: An internal error occurred, and it is not described here "
                            + "(internal-server-error)" + System.lineSeparator());
        }

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
        private static final String STATUS_JSON = """
            {"version":"0.1","pid":4242,"uptimeMillis":65432,"project":"/work/demo","jdtls":"starting"}\
            """;
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
            void saysTheVersionThePidTheUptimeTheProjectAndWhereJdtlsIs() throws IOException {
                try (var daemon = new ScriptedDaemon(socket(), JsonRpc.success("1", STATUS_JSON))) {
                    assertThat(runWithSocket("status")).isZero();

                    assertThat(out())
                            .isEqualTo("gabier-daemon 0.1, pid 4242, up 65s, project /work/demo, jdtls starting"
                                    + System.lineSeparator());
                    assertThat(err()).isEmpty();
                    assertThat(daemon.requests()).containsExactly(STATUS_REQUEST);
                }
            }

            @Test
            void showsTheProjectWithItsControlCharactersEscaped() throws IOException {
                var status = """
                    {"version":"0.1","pid":4242,"uptimeMillis":65432,"project":"/work/\\u001b[2Jdemo","jdtls":"ready"}\
                    """;
                try (var daemon = new ScriptedDaemon(socket(), JsonRpc.success("1", status))) {
                    assertThat(runWithSocket("status")).isZero();

                    assertThat(out())
                            .isEqualTo("gabier-daemon 0.1, pid 4242, up 65s, project /work/\\u001b[2Jdemo, jdtls ready"
                                    + System.lineSeparator());
                }
            }

            @Test
            void saysWhyJdtlsFailedWhenTheDaemonKnows() throws IOException {
                var status = """
                    {"version":"0.1","pid":4242,"uptimeMillis":65432,"project":"/work/demo","jdtls":"failed",\
                    "jdtlsFailure":"jdtls exited with code 137 after it was ready"}\
                    """;
                try (var daemon = new ScriptedDaemon(socket(), JsonRpc.success("1", status))) {
                    assertThat(runWithSocket("status")).isZero();

                    assertThat(out())
                            .isEqualTo("gabier-daemon 0.1, pid 4242, up 65s, project /work/demo, jdtls failed"
                                    + " (jdtls exited with code 137 after it was ready)" + System.lineSeparator());
                }
            }

            @Test
            void showsTheReasonOfJdtlsWithItsControlCharactersEscaped() throws IOException {
                var status = """
                    {"version":"0.1","pid":4242,"uptimeMillis":65432,"project":"/work/demo","jdtls":"failed",\
                    "jdtlsFailure":"red\\u001b[2Jgone \\\\ back"}\
                    """;
                try (var daemon = new ScriptedDaemon(socket(), JsonRpc.success("1", status))) {
                    assertThat(runWithSocket("status")).isZero();

                    assertThat(out())
                            .isEqualTo("gabier-daemon 0.1, pid 4242, up 65s, project /work/demo, jdtls failed"
                                    + " (red\\u001b[2Jgone \\\\ back)" + System.lineSeparator());
                }
            }

            @Test
            void givesTheReasonOfJdtlsExactlyAsJsonWithTheJsonOption() throws IOException {
                var status = """
                    {"version":"0.1","pid":4242,"uptimeMillis":65432,"project":"/work/demo","jdtls":"failed",\
                    "jdtlsFailure":"red\\u001b[2Jgone \\\\ back"}\
                    """;
                try (var daemon = new ScriptedDaemon(socket(), JsonRpc.success("1", status))) {
                    assertThat(runWithSocket("--json", "status")).isZero();

                    assertThat(out()).isEqualTo(status + System.lineSeparator());
                }
            }

            @Test
            void showsAHostileVersionWithItsControlCharactersEscaped() throws IOException {
                var status = """
                    {"version":"\\u001b[2J\\u3164","pid":4242,"uptimeMillis":65432,"project":"/work/demo","jdtls":"ready"}\
                    """;
                try (var daemon = new ScriptedDaemon(socket(), JsonRpc.success("1", status))) {
                    assertThat(runWithSocket("status")).isZero();

                    assertThat(out())
                            .isEqualTo(
                                    "gabier-daemon \\u001b[2J\\u3164, pid 4242, up 65s, project /work/demo, jdtls ready"
                                            + System.lineSeparator());
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
    class Diagnostics {

        private static final String ERRORS = """
            {"diagnostics":[\
            {"severity":"error","line":4,"column":15,"message":"Type mismatch: cannot convert from String to int"},\
            {"severity":"warning","line":0,"column":0,"message":"A warning"}]}\
            """;
        private static final String CLEAN = "{\"diagnostics\":[]}";
        private static final Problem OUTSIDE = new Problem(
                "urn:gabier:error:language-server-file-outside-project",
                "File outside project",
                "The file /elsewhere/A.java is not in the project of the language server",
                "language-server-file-outside-project",
                Map.of("file", "/elsewhere/A.java"),
                ErrorStatus.BAD_REQUEST);

        private Path socket() {
            return directory.resolve("gabier.sock");
        }

        private int runWithSocket(String... args) {
            return run(Map.of("GABIER_SOCKET", socket().toString()), args);
        }

        private static String request(Path file) {
            return """
                {"jsonrpc":"2.0","id":1,"method":"diagnostics","params":{"file":"%s"}}\
                """.formatted(file);
        }

        @Test
        void printsOneLineForEachProblemWithPositionsFromOne() throws IOException {
            try (var daemon = new ScriptedDaemon(socket(), JsonRpc.success("1", ERRORS))) {
                assertThat(runWithSocket("diagnostics", "/project/Broken.java")).isZero();

                assertThat(out().lines())
                        .containsExactly(
                                "/project/Broken.java:5:16: error: Type mismatch: cannot convert from String to int",
                                "/project/Broken.java:1:1: warning: A warning");
                assertThat(err()).isEmpty();
                assertThat(daemon.requests()).containsExactly(request(Path.of("/project/Broken.java")));
            }
        }

        private static String resultOf(String message) {
            return new FileDiagnostics(
                            List.of(new FileDiagnostics.Diagnosis(FileDiagnostics.Severity.ERROR, 0, 0, message)))
                    .toJson();
        }

        @Test
        void showsAnEscapeSequenceOfAMessageAsItsEscapeSoThatItCannotClearTheTerminal() throws IOException {
            try (var daemon = new ScriptedDaemon(socket(), JsonRpc.success("1", resultOf("bad \u001b[2J token")))) {
                assertThat(runWithSocket("diagnostics", "/project/A.java")).isZero();

                assertThat(out())
                        .isEqualTo("/project/A.java:1:1: error: bad \\u001b[2J token" + System.lineSeparator());
                assertThat(daemon.requests()).hasSize(1);
            }
        }

        @Test
        void keepsOneLinePerProblemWhenAMessageHoldsALineBreakAndAForgedLine() throws IOException {
            var message = "first\n/project/B.java:1:1: error: forged";
            try (var daemon = new ScriptedDaemon(socket(), JsonRpc.success("1", resultOf(message)))) {
                assertThat(runWithSocket("diagnostics", "/project/A.java")).isZero();

                assertThat(out().lines())
                        .containsExactly("/project/A.java:1:1: error: first\\u000a/project/B.java:1:1: error: forged");
                assertThat(daemon.requests()).hasSize(1);
            }
        }

        @Test
        void showsAnEscapeSequenceOfTheFileNameAsItsEscape() throws IOException {
            try (var daemon = new ScriptedDaemon(socket(), JsonRpc.success("1", resultOf("m")))) {
                assertThat(runWithSocket("diagnostics", "/project/a\u001b[2Jb.java"))
                        .isZero();

                assertThat(out()).isEqualTo("/project/a\\u001b[2Jb.java:1:1: error: m" + System.lineSeparator());
                assertThat(daemon.requests()).hasSize(1);
            }
        }

        @Test
        void leavesTheMessageAsItIsInJson() throws IOException {
            var result = resultOf("bad \u001b[2J\nline");
            try (var daemon = new ScriptedDaemon(socket(), JsonRpc.success("1", result))) {
                assertThat(runWithSocket("--json", "diagnostics", "/project/A.java"))
                        .isZero();

                assertThat(out()).isEqualTo(result + System.lineSeparator());
                assertThat(out()).contains("bad \\u001b[2J\\nline");
            }
        }

        @Test
        void printsNothingForACleanFile() throws IOException {
            try (var daemon = new ScriptedDaemon(socket(), JsonRpc.success("1", CLEAN))) {
                assertThat(runWithSocket("diagnostics", "/project/Main.java")).isZero();

                assertThat(out()).isEmpty();
                assertThat(err()).isEmpty();
                assertThat(daemon.requests()).hasSize(1);
            }
        }

        @Test
        void asksTheDaemonForTheAbsolutePathOfARelativeFileAndShowsTheFileAsTyped() throws IOException {
            try (var daemon = new ScriptedDaemon(socket(), JsonRpc.success("1", ERRORS))) {
                assertThat(runWithSocket("diagnostics", "src/Broken.java")).isZero();

                assertThat(out()).startsWith("src/Broken.java:5:16: error: ");
                assertThat(daemon.requests())
                        .containsExactly(request(Path.of("src/Broken.java").toAbsolutePath()));
            }
        }

        @Test
        void doesNotCollapseTheDotDotOfAPathThroughALinkBecauseOnlyTheDaemonCleansPaths() throws IOException {
            var target = Files.createDirectories(directory.resolve("targets/real"));
            Files.createFile(directory.resolve("targets/Foo.java"));
            var link = Files.createSymbolicLink(
                    Files.createDirectories(directory.resolve("links")).resolve("src"), target);
            var typed = link.resolve("..").resolve("Foo.java");
            // The shell means the folder that holds the target of the link; the lexical collapse would mean the one
            // that
            // holds the link, where there is no such file.
            assertThat(typed).exists();
            assertThat(directory.resolve("links/Foo.java")).doesNotExist();
            try (var daemon = new ScriptedDaemon(socket(), JsonRpc.success("1", CLEAN))) {
                assertThat(runWithSocket("diagnostics", typed.toString())).isZero();

                assertThat(daemon.requests()).containsExactly(request(typed));
            }
        }

        @Test
        void givesTheResultAsJsonWithTheZeroBasedPositionsWithTheJsonOption() throws IOException {
            try (var daemon = new ScriptedDaemon(socket(), JsonRpc.success("1", ERRORS))) {
                assertThat(runWithSocket("--json", "diagnostics", "/project/Broken.java"))
                        .isZero();

                assertThat(out()).isEqualTo(ERRORS + System.lineSeparator());
                assertThat(daemon.requests()).hasSize(1);
            }
        }

        @Test
        void givesAnEmptyListAsJsonForACleanFile() throws IOException {
            try (var daemon = new ScriptedDaemon(socket(), JsonRpc.success("1", CLEAN))) {
                assertThat(runWithSocket("diagnostics", "/project/Main.java", "--json"))
                        .isZero();

                assertThat(out()).isEqualTo(CLEAN + System.lineSeparator());
                assertThat(daemon.requests()).hasSize(1);
            }
        }

        @Test
        void reportsARefusalOfTheDaemonAsItsProblemObjectWithTheExitCodeOfItsCategory() throws IOException {
            try (var daemon = new ScriptedDaemon(socket(), JsonRpc.failure("1", OUTSIDE))) {
                assertThat(runWithSocket("diagnostics", "/elsewhere/A.java")).isEqualTo(2);

                assertThat(out()).isEmpty();
                assertThat(err()).isEqualTo(OUTSIDE.toText() + System.lineSeparator());
                assertThat(daemon.requests()).hasSize(1);
            }
        }

        @Test
        void reportsARefusalOfTheDaemonAsJsonWithTheJsonOption() throws IOException {
            try (var daemon = new ScriptedDaemon(socket(), JsonRpc.failure("1", OUTSIDE))) {
                assertThat(runWithSocket("--json", "diagnostics", "/elsewhere/A.java"))
                        .isEqualTo(2);

                assertThat(out()).isEmpty();
                assertThat(err()).isEqualTo(OUTSIDE.toJson() + System.lineSeparator());
                assertThat(daemon.requests()).hasSize(1);
            }
        }

        @Test
        void reportsAResultThatIsNotAListOfProblemsAsAnInternalError() throws IOException {
            try (var daemon = new ScriptedDaemon(socket(), JsonRpc.success("1", "{}"))) {
                assertThat(runWithSocket("diagnostics", "/project/Main.java")).isEqualTo(1);

                assertThat(out()).isEmpty();
                assertThat(err()).contains("(invalid-response)");
                assertThat(daemon.requests()).hasSize(1);
            }
        }

        @Test
        void saysNoDaemonIsRunningWithoutHanging() throws Exception {
            var code = CompletableFuture.supplyAsync(() -> runWithSocket("diagnostics", "/project/Main.java"))
                    .get(10, TimeUnit.SECONDS);

            assertThat(code).isEqualTo(2);
            assertThat(err()).contains("(daemon-not-running; ");
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
            var status = JsonRpc.success("1", """
                {"version":"0.1","pid":1,"uptimeMillis":0,"project":"/p","jdtls":"not-started"}\
                """);
            try (var daemon = new ScriptedDaemon(expected, status)) {
                var code = run(Map.of("XDG_RUNTIME_DIR", runtimeDirectory.toString()), "status");

                assertThat(code).isZero();
                assertThat(daemon.requests()).hasSize(1);
            }
        }
    }
}
