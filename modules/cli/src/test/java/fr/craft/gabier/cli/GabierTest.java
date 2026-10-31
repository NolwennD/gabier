package fr.craft.gabier.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.craft.gabier.protocol.ChangedFiles;
import fr.craft.gabier.protocol.FileDiagnostics;
import fr.craft.gabier.protocol.Json;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GabierTest {

    private static final String USAGE_DETAIL = "Unsupported arguments; run gabier help for the commands, "
            + "and gabier help <command> for the usage of one";

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
            run("--bogus");

            assertThat(err()).isEqualTo(usageText("--bogus"));
        }

        @Test
        void unknownArgumentPrintsNothingOnTheStandardOutput() {
            run("--bogus");

            assertThat(out()).isEmpty();
        }

        @Test
        void unknownArgumentExitsWithStatusTwo() {
            assertThat(run("--bogus")).isEqualTo(2);
        }

        @Test
        void noArgumentIsAnUnknownUsage() {
            assertThat(run()).isEqualTo(2);
            assertThat(out()).isEmpty();
            assertThat(err()).isEqualTo(usageText(""));
        }

        @Test
        void versionFollowedByAnotherArgumentIsAnUnknownUsage() {
            assertThat(run("--version", "--bogus")).isEqualTo(2);
            assertThat(out()).isEmpty();
            assertThat(err()).isEqualTo(usageText("--version --bogus"));
        }

        @Test
        void unknownArgumentFollowedByVersionIsAnUnknownUsage() {
            assertThat(run("--bogus", "--version")).isEqualTo(2);
            assertThat(out()).isEmpty();
            assertThat(err()).isEqualTo(usageText("--bogus --version"));
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

        @Test
        void aRenameThatNamesNeitherAPositionNorAnIdentifierCompletelyIsAnUnknownUsage() {
            for (var arguments : List.of(
                    List.of("rename"),
                    List.of("rename", "A.java", "3", "5"),
                    List.of("rename", "A.java", "3", "5", "name", "more"),
                    List.of("rename", "--dry-run", "A.java", "3", "name"),
                    List.of("rename", "--token", "A.java", "name"),
                    List.of("rename", "--token", "A.java", "id", "name", "more"),
                    List.of("rename", "--token", "A.java", "3", "5", "name"))) {
                out.reset();
                err.reset();

                assertThat(run(arguments.toArray(String[]::new)))
                        .as(arguments.toString())
                        .isEqualTo(2);
                assertThat(out()).isEmpty();
                assertThat(err()).isEqualTo(usageText(String.join(" ", arguments)));
            }
        }

        @Test
        void aLineOrAColumnThatIsNotANumberFromOneIsAnUnknownUsage() {
            for (var position : List.of(
                    List.of("0", "5"),
                    List.of("3", "0"),
                    List.of("-1", "5"),
                    List.of("three", "5"),
                    List.of("3", "five"),
                    List.of("3", "1.5"))) {
                out.reset();
                err.reset();

                assertThat(run("rename", "A.java", position.get(0), position.get(1), "name"))
                        .as(position.toString())
                        .isEqualTo(2);
                assertThat(out()).isEmpty();
                assertThat(err())
                        .isEqualTo(usageText("rename A.java " + position.get(0) + " " + position.get(1) + " name"));
            }
        }

        @Test
        void organizeImportsWithoutExactlyOneFileIsAnUnknownUsage() {
            for (var arguments : List.of(
                    List.of("organize-imports"),
                    List.of("organize-imports", "--dry-run"),
                    List.of("organize-imports", "A.java", "B.java"),
                    List.of("organize-imports", "--dry-run", "A.java", "B.java"),
                    List.of("organize-imports", "--token", "A.java"))) {
                out.reset();
                err.reset();

                assertThat(run(arguments.toArray(String[]::new)))
                        .as(arguments.toString())
                        .isEqualTo(2);
                assertThat(out()).isEmpty();
                assertThat(err()).isEqualTo(usageText(String.join(" ", arguments)));
            }
        }

        @Test
        void extractVariableWithoutAFileTwoPositionsAndANameIsAnUnknownUsage() {
            for (var arguments : List.of(
                    List.of("extract-variable"),
                    List.of("extract-variable", "--dry-run"),
                    List.of("extract-variable", "A.java", "3:4", "3:9"),
                    List.of("extract-variable", "A.java", "3:4", "3:9", "name", "more"),
                    List.of("extract-variable", "--token", "A.java", "3:4", "3:9", "name"))) {
                out.reset();
                err.reset();

                assertThat(run(arguments.toArray(String[]::new)))
                        .as(arguments.toString())
                        .isEqualTo(2);
                assertThat(out()).isEmpty();
                assertThat(err()).isEqualTo(usageText(String.join(" ", arguments)));
            }
        }

        @Test
        void extractVariableWithAPositionThatIsNotLineColonColumnFromOneIsAnUnknownUsage() {
            for (var positions : List.of(
                    List.of("3", "3:9"),
                    List.of("3:4", "9"),
                    List.of("3:4:5", "3:9"),
                    List.of("0:4", "3:9"),
                    List.of("3:0", "3:9"),
                    List.of("3:4", "-3:9"),
                    List.of("three:4", "3:9"),
                    List.of("3:4", "3:nine"),
                    List.of("3:4", "3:1.5"))) {
                out.reset();
                err.reset();

                assertThat(run("extract-variable", "A.java", positions.get(0), positions.get(1), "name"))
                        .as(positions.toString())
                        .isEqualTo(2);
                assertThat(out()).isEmpty();
                assertThat(err())
                        .isEqualTo(usageText(
                                "extract-variable A.java " + positions.get(0) + " " + positions.get(1) + " name"));
            }
        }

        @Test
        void theDryRunAndTokenOptionsOfARenameAreNotOptionsOfAnotherCommand() {
            assertThat(run("diagnostics", "--dry-run", "A.java")).isEqualTo(2);
            assertThat(err()).isEqualTo(usageText("diagnostics --dry-run A.java"));
        }
    }

    @Nested
    class Help {

        private static final List<String> COMMANDS =
                List.of("diagnostics", "rename", "organize-imports", "extract-variable", "status", "stop", "help");

        private static Map<String, Object> object(Object json) {
            var members = new LinkedHashMap<String, Object>();
            ((Map<?, ?>) json).forEach((name, value) -> members.put((String) name, value));

            return members;
        }

        private static List<Map<String, Object>> objects(Object json) {
            return ((List<?>) json).stream().map(Help::object).toList();
        }

        private static List<String> strings(Object json) {
            return ((List<?>) json).stream().map(String.class::cast).toList();
        }

        @Test
        void helpHelpDashDashHelpAndDashHListTheCommandsOnTheStandardOutputWithExitZero() {
            for (var spelling : List.of("help", "--help", "-h")) {
                out.reset();
                err.reset();

                assertThat(run(spelling)).as(spelling).isZero();

                assertThat(err()).as(spelling).isEmpty();
                assertThat(out().lines().map(String::strip).map(line -> line.split(" ")[0]))
                        .as(spelling)
                        .containsAll(COMMANDS);
            }
        }

        @Test
        void theListGivesOneLineAndTheProtocolVersionPointerToHelpOfACommand() {
            run("help");

            assertThat(out())
                    .contains("gabier help <command>")
                    .contains("diagnostics ")
                    .contains("the errors and warnings of a Java file");
        }

        @Test
        void theListAsJsonIsAnObjectWithEveryCommand() {
            assertThat(run("--json", "help")).isZero();

            assertThat(out().lines()).hasSize(1);
            assertThat(err()).isEmpty();
            var commands = objects(object(Json.parse(out())).get("commands"));
            assertThat(commands)
                    .extracting(command -> (String) command.get("name"))
                    .containsExactlyElementsOf(COMMANDS);
            assertThat(commands)
                    .allSatisfy(command -> assertThat(command)
                            .containsOnlyKeys("name", "summary", "usage")
                            .allSatisfy(
                                    (key, value) -> assertThat((String) value).isNotBlank()));
        }

        @Test
        void dashDashHelpAsJsonIsTheSameObject() {
            run("help", "--json");
            var help = out();
            out.reset();

            run("--json", "-h");

            assertThat(out()).isEqualTo(help);
        }

        @Test
        void helpOfACommandGivesTheUsageAnExampleAndTheRefusals() {
            assertThat(run("help", "rename")).isZero();

            assertThat(err()).isEmpty();
            assertThat(out())
                    .contains("gabier [--json] rename [--dry-run] <file> <line> <column> <new-name>")
                    .contains("gabier [--json] rename [--dry-run] --token <file> <identifier> <new-name>")
                    .contains("Example")
                    .contains("$ gabier rename --dry-run src/main/java/demo/Greeter.java 6 19 welcome")
                    .contains("Refusals")
                    .contains("language-server-invalid-name");
        }

        @Test
        void helpOfEveryCommandIsAvailable() {
            for (var command : COMMANDS) {
                out.reset();

                assertThat(run("help", command)).as(command).isZero();

                assertThat(out()).as(command).contains("gabier [--json] " + command);
            }
        }

        @Test
        void helpOfACommandAsJsonIsAnObjectWithItsFields() {
            assertThat(run("help", "--json", "extract-variable")).isZero();

            var help = object(Json.parse(out()));
            assertThat(help).containsOnlyKeys("name", "summary", "usage", "example", "refusals");
            assertThat(help.get("name")).isEqualTo("extract-variable");
            assertThat((String) help.get("usage")).contains("extract-variable [--dry-run] <file>");
            assertThat((String) help.get("example")).startsWith("$ gabier extract-variable");
            assertThat(strings(help.get("refusals")))
                    .anySatisfy(refusal -> assertThat(refusal).startsWith("language-server-not-an-expression"));
        }

        @Test
        void helpOfAnUnknownCommandIsAUsageErrorThatListsTheKnownOnes() {
            assertThat(run("help", "frobnicate")).isEqualTo(2);

            assertThat(out()).isEmpty();
            assertThat(err())
                    .startsWith("Usage error: Unknown command; the commands are diagnostics, rename, organize-imports,"
                            + " extract-variable, status, stop, help (usage-error; command=frobnicate)");
        }

        @Test
        void helpOfAnUnknownCommandAsJsonIsTheProblemObject() {
            assertThat(run("--json", "help", "frobnicate")).isEqualTo(2);

            assertThat(err())
                    .startsWith("{\"type\":\"urn:gabier:error:usage-error\"")
                    .contains("\"parameters\":{\"command\":\"frobnicate\"}");
        }

        @Test
        void helpOfTwoCommandsIsAUsageError() {
            assertThat(run("help", "rename", "stop")).isEqualTo(2);

            assertThat(err()).isEqualTo(usageText("help rename stop"));
        }

        @Test
        void dashDashHelpFollowedByAnotherArgumentIsAUsageError() {
            assertThat(run("--help", "rename")).isEqualTo(2);

            assertThat(err()).isEqualTo(usageText("--help rename"));
        }

        @Test
        void helpNeverStartsADaemonNorOpensTheSocket() throws IOException {
            var marker = directory.resolve("started");
            var bin = directory.resolve("bin");
            FakeDaemonScripts.install(bin, "echo started > '" + marker + "'");
            var socket = directory.resolve("gabier.sock");
            var environment = Map.of("GABIER_SOCKET", socket.toString(), "PATH", bin.toString());

            assertThat(run(environment, "help")).isZero();
            assertThat(run(environment, "help", "diagnostics")).isZero();
            assertThat(run(environment, "--help")).isZero();
            assertThat(run(environment, "-h")).isZero();

            assertThat(marker).doesNotExist();
            assertThat(socket).doesNotExist();
        }
    }

    @Nested
    class JsonOption {

        @Test
        void aUsageErrorIsTheProblemObjectOnTheStandardError() {
            assertThat(run("--json", "--bogus")).isEqualTo(2);

            assertThat(err()).isEqualTo(usageJson("--bogus"));
        }

        @Test
        void aUsageErrorPrintsNothingOnTheStandardOutput() {
            run("--json", "--bogus");

            assertThat(out()).isEmpty();
        }

        @Test
        void canComeAfterTheOtherArguments() {
            assertThat(run("--bogus", "--json")).isEqualTo(2);

            assertThat(err()).isEqualTo(usageJson("--bogus"));
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
    }

    @Nested
    class Renaming {

        private static final String BY_POSITION = """
            {"jsonrpc":"2.0","id":1,"method":"rename","params":{"column":"17","dryRun":"%s","file":"%s",\
            "line":"5","newName":"welcome"}}\
            """;
        private static final String BY_TOKEN = """
            {"jsonrpc":"2.0","id":1,"method":"rename-token","params":{"dryRun":"%s","file":"%s",\
            "newName":"welcome","token":"greet"}}\
            """;
        private static final ChangedFiles RENAMED_METHOD = new ChangedFiles(
                List.of(),
                List.of(),
                List.of(),
                List.of(Path.of("/project/Greeter.java"), Path.of("/project/Main.java")),
                """
                --- /project/Greeter.java
                +++ /project/Greeter.java
                @@ -1 +1 @@
                -    public String greet(String name) {
                +    public String welcome(String name) {
                --- /project/Main.java
                +++ /project/Main.java
                @@ -1 +1 @@
                -        System.out.println(greeter.greet("world"));
                +        System.out.println(greeter.welcome("world"));
                """);
        private static final ChangedFiles RENAMED_TYPE = new ChangedFiles(
                List.of(Path.of("/project/New.java")),
                List.of(new ChangedFiles.Moved(Path.of("/project/Greeter.java"), Path.of("/project/Welcomer.java"))),
                List.of(Path.of("/project/Gone.java")),
                List.of(Path.of("/project/Main.java")),
                "--- /project/Main.java\n+++ /project/Main.java\n");
        private static final Problem NO_SYMBOL = new Problem(
                "urn:gabier:error:language-server-no-such-symbol",
                "No such symbol",
                "There is no symbol at 4:0 of /project/Greeter.java",
                "language-server-no-such-symbol",
                Map.of("file", "/project/Greeter.java"),
                ErrorStatus.BAD_REQUEST);

        private Path socket() {
            return directory.resolve("gabier.sock");
        }

        private int runWithSocket(String... args) {
            return run(Map.of("GABIER_SOCKET", socket().toString()), args);
        }

        @Test
        void asksTheDaemonToRenameTheSymbolAtAPositionGivenFromOneAndSendsItFromZero() throws IOException {
            try (var daemon = new ScriptedDaemon(socket(), JsonRpc.success("1", RENAMED_METHOD.toJson()))) {
                assertThat(runWithSocket("rename", "/project/Greeter.java", "6", "18", "welcome"))
                        .isZero();

                assertThat(daemon.requests())
                        .containsExactly(BY_POSITION.formatted(false, Path.of("/project/Greeter.java")));
            }
        }

        @Test
        void asksTheDaemonToRenameTheSymbolAnIdentifierNamesWithTheTokenOption() throws IOException {
            try (var daemon = new ScriptedDaemon(socket(), JsonRpc.success("1", RENAMED_METHOD.toJson()))) {
                assertThat(runWithSocket("rename", "--token", "/project/Greeter.java", "greet", "welcome"))
                        .isZero();

                assertThat(daemon.requests())
                        .containsExactly(BY_TOKEN.formatted(false, Path.of("/project/Greeter.java")));
            }
        }

        @Test
        void asksForADryRunWithTheDryRunOptionWhereverItIs() throws IOException {
            for (var arguments : List.of(
                    List.of("rename", "--dry-run", "/project/Greeter.java", "6", "18", "welcome"),
                    List.of("rename", "/project/Greeter.java", "6", "18", "welcome", "--dry-run"))) {
                try (var daemon = new ScriptedDaemon(socket(), JsonRpc.success("1", RENAMED_METHOD.toJson()))) {
                    assertThat(runWithSocket(arguments.toArray(String[]::new))).isZero();

                    assertThat(daemon.requests())
                            .containsExactly(BY_POSITION.formatted(true, Path.of("/project/Greeter.java")));
                }
                Files.deleteIfExists(socket());
            }
        }

        @Test
        void asksForADryRunByTokenWithTheTwoOptionsInAnyOrder() throws IOException {
            try (var daemon = new ScriptedDaemon(socket(), JsonRpc.success("1", RENAMED_METHOD.toJson()))) {
                assertThat(runWithSocket("rename", "--token", "--dry-run", "/project/Greeter.java", "greet", "welcome"))
                        .isZero();

                assertThat(daemon.requests())
                        .containsExactly(BY_TOKEN.formatted(true, Path.of("/project/Greeter.java")));
            }
        }

        @Test
        void sendsTheAbsolutePathOfARelativeFile() throws IOException {
            try (var daemon = new ScriptedDaemon(socket(), JsonRpc.success("1", RENAMED_METHOD.toJson()))) {
                assertThat(runWithSocket("rename", "src/Greeter.java", "6", "18", "welcome"))
                        .isZero();

                assertThat(daemon.requests())
                        .containsExactly(BY_POSITION.formatted(
                                false, Path.of("src/Greeter.java").toAbsolutePath()));
            }
        }

        @Test
        void listsTheFilesThatChangedThenShowsTheDiff() throws IOException {
            try (var daemon = new ScriptedDaemon(socket(), JsonRpc.success("1", RENAMED_METHOD.toJson()))) {
                assertThat(runWithSocket("rename", "/project/Greeter.java", "6", "18", "welcome"))
                        .isZero();

                assertThat(out().lines())
                        .containsExactly(
                                "modified /project/Greeter.java",
                                "modified /project/Main.java",
                                "",
                                "--- /project/Greeter.java",
                                "+++ /project/Greeter.java",
                                "@@ -1 +1 @@",
                                "-    public String greet(String name) {",
                                "+    public String welcome(String name) {",
                                "--- /project/Main.java",
                                "+++ /project/Main.java",
                                "@@ -1 +1 @@",
                                "-        System.out.println(greeter.greet(\"world\"));",
                                "+        System.out.println(greeter.welcome(\"world\"));");
                assertThat(err()).isEmpty();
            }
        }

        @Test
        void listsTheFilesCreatedMovedAndDeletedToo() throws IOException {
            try (var daemon = new ScriptedDaemon(socket(), JsonRpc.success("1", RENAMED_TYPE.toJson()))) {
                assertThat(runWithSocket("rename", "/project/Greeter.java", "3", "14", "Welcomer"))
                        .isZero();

                assertThat(out().lines())
                        .containsExactly(
                                "modified /project/Main.java",
                                "created /project/New.java",
                                "renamed /project/Greeter.java -> /project/Welcomer.java",
                                "deleted /project/Gone.java",
                                "",
                                "--- /project/Main.java",
                                "+++ /project/Main.java");
            }
        }

        @Test
        void saysThatNothingWasWrittenForADryRun() throws IOException {
            try (var daemon = new ScriptedDaemon(socket(), JsonRpc.success("1", RENAMED_METHOD.toJson()))) {
                assertThat(runWithSocket("rename", "--dry-run", "/project/Greeter.java", "6", "18", "welcome"))
                        .isZero();

                assertThat(out().lines()).startsWith("dry run, nothing written", "modified /project/Greeter.java");
            }
        }

        @Test
        void printsNothingWhenNothingChanged() throws IOException {
            var nothing = new ChangedFiles(List.of(), List.of(), List.of(), List.of(), "");
            try (var daemon = new ScriptedDaemon(socket(), JsonRpc.success("1", nothing.toJson()))) {
                assertThat(runWithSocket("rename", "/project/Greeter.java", "6", "18", "welcome"))
                        .isZero();

                assertThat(out()).isEmpty();
            }
        }

        @Test
        void showsWhatATerminalWouldObeyInAFileNameOrADiffAsEscapes() throws IOException {
            var hostile = new ChangedFiles(
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(Path.of("/project/a\u001b[2Jb.java")),
                    "--- /project/a\u001b[2Jb.java\n+forged\u001b[2J\n");
            try (var daemon = new ScriptedDaemon(socket(), JsonRpc.success("1", hostile.toJson()))) {
                assertThat(runWithSocket("rename", "/project/Greeter.java", "6", "18", "welcome"))
                        .isZero();

                assertThat(out().lines())
                        .containsExactly(
                                "modified /project/a\\u001b[2Jb.java",
                                "",
                                "--- /project/a\\u001b[2Jb.java",
                                "+forged\\u001b[2J");
            }
        }

        @Test
        void givesTheResultAsJsonWithTheJsonOption() throws IOException {
            try (var daemon = new ScriptedDaemon(socket(), JsonRpc.success("1", RENAMED_METHOD.toJson()))) {
                assertThat(runWithSocket("--json", "rename", "--token", "/project/Greeter.java", "greet", "welcome"))
                        .isZero();

                assertThat(out()).isEqualTo(RENAMED_METHOD.toJson() + System.lineSeparator());
                assertThat(err()).isEmpty();
            }
        }

        @Test
        void reportsARefusalOfTheDaemonAsItsProblemObjectWithTheExitCodeOfItsCategory() throws IOException {
            try (var daemon = new ScriptedDaemon(socket(), JsonRpc.failure("1", NO_SYMBOL))) {
                assertThat(runWithSocket("rename", "/project/Greeter.java", "5", "1", "anything"))
                        .isEqualTo(2);

                assertThat(out()).isEmpty();
                assertThat(err()).isEqualTo(NO_SYMBOL.toText() + System.lineSeparator());
            }
        }

        @Test
        void reportsARefusalOfTheDaemonAsJsonWithTheJsonOption() throws IOException {
            try (var daemon = new ScriptedDaemon(socket(), JsonRpc.failure("1", NO_SYMBOL))) {
                assertThat(runWithSocket("--json", "rename", "/project/Greeter.java", "5", "1", "anything"))
                        .isEqualTo(2);

                assertThat(out()).isEmpty();
                assertThat(err()).isEqualTo(NO_SYMBOL.toJson() + System.lineSeparator());
            }
        }

        @Test
        void reportsAResultThatIsNotAListOfChangedFilesAsAnInternalError() throws IOException {
            try (var daemon = new ScriptedDaemon(socket(), JsonRpc.success("1", "{}"))) {
                assertThat(runWithSocket("rename", "/project/Greeter.java", "6", "18", "welcome"))
                        .isEqualTo(1);

                assertThat(out()).isEmpty();
                assertThat(err()).contains("(invalid-response)");
            }
        }
    }

    @Nested
    class OrganizingImports {

        private static final String REQUEST = """
            {"jsonrpc":"2.0","id":1,"method":"organize-imports","params":{"dryRun":"%s","file":"%s"}}\
            """;
        private static final ChangedFiles ORGANIZED =
                new ChangedFiles(List.of(), List.of(), List.of(), List.of(Path.of("/project/Untidy.java")), """
                    --- /project/Untidy.java
                    +++ /project/Untidy.java
                    @@ -3,2 +3,2 @@
                    -import java.util.Set;
                    +import java.util.ArrayList;
                    """);
        private static final ChangedFiles NOTHING = new ChangedFiles(List.of(), List.of(), List.of(), List.of(), "");
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

        @Test
        void asksTheDaemonToOrganizeTheImportsOfTheFile() throws IOException {
            try (var daemon = new ScriptedDaemon(socket(), JsonRpc.success("1", ORGANIZED.toJson()))) {
                assertThat(runWithSocket("organize-imports", "/project/Untidy.java"))
                        .isZero();

                assertThat(daemon.requests())
                        .containsExactly(REQUEST.formatted(false, Path.of("/project/Untidy.java")));
            }
        }

        @Test
        void sendsTheAbsolutePathOfARelativeFile() throws IOException {
            try (var daemon = new ScriptedDaemon(socket(), JsonRpc.success("1", ORGANIZED.toJson()))) {
                assertThat(runWithSocket("organize-imports", "src/Untidy.java")).isZero();

                assertThat(daemon.requests())
                        .containsExactly(REQUEST.formatted(
                                false, Path.of("src/Untidy.java").toAbsolutePath()));
            }
        }

        @Test
        void asksForADryRunWithTheDryRunOptionWhereverItIs() throws IOException {
            for (var arguments : List.of(
                    List.of("organize-imports", "--dry-run", "/project/Untidy.java"),
                    List.of("organize-imports", "/project/Untidy.java", "--dry-run"))) {
                try (var daemon = new ScriptedDaemon(socket(), JsonRpc.success("1", ORGANIZED.toJson()))) {
                    assertThat(runWithSocket(arguments.toArray(String[]::new))).isZero();

                    assertThat(daemon.requests())
                            .containsExactly(REQUEST.formatted(true, Path.of("/project/Untidy.java")));
                }
                Files.deleteIfExists(socket());
            }
        }

        @Test
        void listsTheFileThatChangedThenShowsTheDiff() throws IOException {
            try (var daemon = new ScriptedDaemon(socket(), JsonRpc.success("1", ORGANIZED.toJson()))) {
                assertThat(runWithSocket("organize-imports", "/project/Untidy.java"))
                        .isZero();

                assertThat(out().lines())
                        .containsExactly(
                                "modified /project/Untidy.java",
                                "",
                                "--- /project/Untidy.java",
                                "+++ /project/Untidy.java",
                                "@@ -3,2 +3,2 @@",
                                "-import java.util.Set;",
                                "+import java.util.ArrayList;");
                assertThat(err()).isEmpty();
            }
        }

        @Test
        void saysThatNothingWasWrittenForADryRun() throws IOException {
            try (var daemon = new ScriptedDaemon(socket(), JsonRpc.success("1", ORGANIZED.toJson()))) {
                assertThat(runWithSocket("organize-imports", "--dry-run", "/project/Untidy.java"))
                        .isZero();

                assertThat(out().lines()).startsWith("dry run, nothing written", "modified /project/Untidy.java");
            }
        }

        @Test
        void saysThatThereIsNothingToChangeForAFileWhoseImportsAreInOrder() throws IOException {
            try (var daemon = new ScriptedDaemon(socket(), JsonRpc.success("1", NOTHING.toJson()))) {
                assertThat(runWithSocket("organize-imports", "/project/Tidy.java"))
                        .isZero();

                assertThat(out().lines()).containsExactly("nothing to change");
                assertThat(err()).isEmpty();
            }
        }

        @Test
        void saysBothThatItIsADryRunAndThatThereIsNothingToChange() throws IOException {
            try (var daemon = new ScriptedDaemon(socket(), JsonRpc.success("1", NOTHING.toJson()))) {
                assertThat(runWithSocket("organize-imports", "--dry-run", "/project/Tidy.java"))
                        .isZero();

                assertThat(out().lines()).containsExactly("dry run, nothing written", "nothing to change");
            }
        }

        @Test
        void showsWhatATerminalWouldObeyInAFileNameOrADiffAsEscapes() throws IOException {
            var hostile = new ChangedFiles(
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(Path.of("/project/a\u001b[2Jb.java")),
                    "--- /project/a\u001b[2Jb.java\n+forged\u001b[2J\n");
            try (var daemon = new ScriptedDaemon(socket(), JsonRpc.success("1", hostile.toJson()))) {
                assertThat(runWithSocket("organize-imports", "/project/Untidy.java"))
                        .isZero();

                assertThat(out().lines())
                        .containsExactly(
                                "modified /project/a\\u001b[2Jb.java",
                                "",
                                "--- /project/a\\u001b[2Jb.java",
                                "+forged\\u001b[2J");
            }
        }

        @Test
        void givesTheResultAsJsonWithTheJsonOptionWhateverItHolds() throws IOException {
            for (var result : List.of(ORGANIZED, NOTHING)) {
                try (var daemon = new ScriptedDaemon(socket(), JsonRpc.success("1", result.toJson()))) {
                    out.reset();

                    assertThat(runWithSocket("--json", "organize-imports", "/project/Untidy.java"))
                            .isZero();

                    assertThat(out()).isEqualTo(result.toJson() + System.lineSeparator());
                    assertThat(err()).isEmpty();
                }
                Files.deleteIfExists(socket());
            }
        }

        @Test
        void reportsARefusalOfTheDaemonAsItsProblemObjectWithTheExitCodeOfItsCategory() throws IOException {
            try (var daemon = new ScriptedDaemon(socket(), JsonRpc.failure("1", OUTSIDE))) {
                assertThat(runWithSocket("organize-imports", "/elsewhere/A.java"))
                        .isEqualTo(2);

                assertThat(out()).isEmpty();
                assertThat(err()).isEqualTo(OUTSIDE.toText() + System.lineSeparator());
            }
        }

        @Test
        void reportsARefusalOfTheDaemonAsJsonWithTheJsonOption() throws IOException {
            try (var daemon = new ScriptedDaemon(socket(), JsonRpc.failure("1", OUTSIDE))) {
                assertThat(runWithSocket("--json", "organize-imports", "/elsewhere/A.java"))
                        .isEqualTo(2);

                assertThat(out()).isEmpty();
                assertThat(err()).isEqualTo(OUTSIDE.toJson() + System.lineSeparator());
            }
        }

        @Test
        void reportsAResultThatIsNotAListOfChangedFilesAsAnInternalError() throws IOException {
            try (var daemon = new ScriptedDaemon(socket(), JsonRpc.success("1", "{}"))) {
                assertThat(runWithSocket("organize-imports", "/project/Untidy.java"))
                        .isEqualTo(1);

                assertThat(out()).isEmpty();
                assertThat(err()).contains("(invalid-response)");
            }
        }
    }

    @Nested
    class ExtractingAVariable {

        private static final String REQUEST = """
            {"jsonrpc":"2.0","id":1,"method":"extract-variable","params":{"dryRun":"%s","endColumn":"%s","endLine":"%s",\
            "file":"%s","name":"%s","startColumn":"%s","startLine":"%s"}}\
            """;
        private static final ChangedFiles EXTRACTED =
                new ChangedFiles(List.of(), List.of(), List.of(), List.of(Path.of("/project/Greeter.java")), """
                    --- /project/Greeter.java
                    +++ /project/Greeter.java
                    @@ -7,2 +7,3 @@
                    -        String message = greeting + ", " + name;
                    +        String joined = greeting + ", " + name;
                    +        String message = joined;
                    """);
        private static final Problem NOT_AN_EXPRESSION = new Problem(
                "urn:gabier:error:language-server-not-an-expression",
                "Not an expression",
                "The selection 8:9-8:15 of /project/Greeter.java is not an expression",
                "language-server-not-an-expression",
                Map.of("file", "/project/Greeter.java"),
                ErrorStatus.BAD_REQUEST);

        private Path socket() {
            return directory.resolve("gabier.sock");
        }

        private int runWithSocket(String... args) {
            return run(Map.of("GABIER_SOCKET", socket().toString()), args);
        }

        @Test
        void asksTheDaemonToExtractTheSelectionCountedFromOneAsItIsShownAndSendsItFromZero() throws IOException {
            try (var daemon = new ScriptedDaemon(socket(), JsonRpc.success("1", EXTRACTED.toJson()))) {
                assertThat(runWithSocket("extract-variable", "/project/Greeter.java", "7:26", "7:48", "joined"))
                        .isZero();

                assertThat(daemon.requests())
                        .containsExactly(
                                REQUEST.formatted(false, 47, 6, Path.of("/project/Greeter.java"), "joined", 25, 6));
            }
        }

        @Test
        void takesASelectionOverSeveralLines() throws IOException {
            try (var daemon = new ScriptedDaemon(socket(), JsonRpc.success("1", EXTRACTED.toJson()))) {
                assertThat(runWithSocket("extract-variable", "/project/Greeter.java", "7:26", "9:3", "joined"))
                        .isZero();

                assertThat(daemon.requests())
                        .containsExactly(
                                REQUEST.formatted(false, 2, 8, Path.of("/project/Greeter.java"), "joined", 25, 6));
            }
        }

        @Test
        void sendsTheAbsolutePathOfARelativeFile() throws IOException {
            try (var daemon = new ScriptedDaemon(socket(), JsonRpc.success("1", EXTRACTED.toJson()))) {
                assertThat(runWithSocket("extract-variable", "src/Greeter.java", "7:26", "7:48", "joined"))
                        .isZero();

                assertThat(daemon.requests())
                        .containsExactly(REQUEST.formatted(
                                false, 47, 6, Path.of("src/Greeter.java").toAbsolutePath(), "joined", 25, 6));
            }
        }

        @Test
        void asksForADryRunWithTheDryRunOptionWhereverItIs() throws IOException {
            for (var arguments : List.of(
                    List.of("extract-variable", "--dry-run", "/project/Greeter.java", "7:26", "7:48", "joined"),
                    List.of("extract-variable", "/project/Greeter.java", "7:26", "7:48", "joined", "--dry-run"))) {
                try (var daemon = new ScriptedDaemon(socket(), JsonRpc.success("1", EXTRACTED.toJson()))) {
                    assertThat(runWithSocket(arguments.toArray(String[]::new))).isZero();

                    assertThat(daemon.requests())
                            .containsExactly(
                                    REQUEST.formatted(true, 47, 6, Path.of("/project/Greeter.java"), "joined", 25, 6));
                }
                Files.deleteIfExists(socket());
            }
        }

        @Test
        void listsTheFileThatChangedThenShowsTheDiff() throws IOException {
            try (var daemon = new ScriptedDaemon(socket(), JsonRpc.success("1", EXTRACTED.toJson()))) {
                assertThat(runWithSocket("extract-variable", "/project/Greeter.java", "7:26", "7:48", "joined"))
                        .isZero();

                assertThat(out().lines())
                        .containsExactly(
                                "modified /project/Greeter.java",
                                "",
                                "--- /project/Greeter.java",
                                "+++ /project/Greeter.java",
                                "@@ -7,2 +7,3 @@",
                                "-        String message = greeting + \", \" + name;",
                                "+        String joined = greeting + \", \" + name;",
                                "+        String message = joined;");
                assertThat(err()).isEmpty();
            }
        }

        @Test
        void saysThatNothingWasWrittenForADryRun() throws IOException {
            try (var daemon = new ScriptedDaemon(socket(), JsonRpc.success("1", EXTRACTED.toJson()))) {
                assertThat(runWithSocket(
                                "extract-variable", "--dry-run", "/project/Greeter.java", "7:26", "7:48", "joined"))
                        .isZero();

                assertThat(out().lines()).startsWith("dry run, nothing written", "modified /project/Greeter.java");
            }
        }

        @Test
        void givesTheResultAsJsonWithTheJsonOption() throws IOException {
            try (var daemon = new ScriptedDaemon(socket(), JsonRpc.success("1", EXTRACTED.toJson()))) {
                assertThat(runWithSocket(
                                "--json", "extract-variable", "/project/Greeter.java", "7:26", "7:48", "joined"))
                        .isZero();

                assertThat(out()).isEqualTo(EXTRACTED.toJson() + System.lineSeparator());
                assertThat(err()).isEmpty();
            }
        }

        @Test
        void showsWhatATerminalWouldObeyInAFileNameOrADiffAsEscapes() throws IOException {
            var hostile = new ChangedFiles(
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(Path.of("/project/a\u001b[2Jb.java")),
                    "--- /project/a\u001b[2Jb.java\n+forged\u001b[2J\n");
            try (var daemon = new ScriptedDaemon(socket(), JsonRpc.success("1", hostile.toJson()))) {
                assertThat(runWithSocket("extract-variable", "/project/Greeter.java", "7:26", "7:48", "joined"))
                        .isZero();

                assertThat(out().lines())
                        .containsExactly(
                                "modified /project/a\\u001b[2Jb.java",
                                "",
                                "--- /project/a\\u001b[2Jb.java",
                                "+forged\\u001b[2J");
            }
        }

        @Test
        void reportsARefusalOfTheDaemonAsItsProblemObjectWithTheExitCodeOfItsCategory() throws IOException {
            try (var daemon = new ScriptedDaemon(socket(), JsonRpc.failure("1", NOT_AN_EXPRESSION))) {
                assertThat(runWithSocket("extract-variable", "/project/Greeter.java", "8:9", "8:15", "joined"))
                        .isEqualTo(2);

                assertThat(out()).isEmpty();
                assertThat(err()).isEqualTo(NOT_AN_EXPRESSION.toText() + System.lineSeparator());
            }
        }

        @Test
        void reportsARefusalOfTheDaemonAsJsonWithTheJsonOption() throws IOException {
            try (var daemon = new ScriptedDaemon(socket(), JsonRpc.failure("1", NOT_AN_EXPRESSION))) {
                assertThat(runWithSocket(
                                "--json", "extract-variable", "/project/Greeter.java", "8:9", "8:15", "joined"))
                        .isEqualTo(2);

                assertThat(out()).isEmpty();
                assertThat(err()).isEqualTo(NOT_AN_EXPRESSION.toJson() + System.lineSeparator());
            }
        }

        @Test
        void reportsAResultThatIsNotAListOfChangedFilesAsAnInternalError() throws IOException {
            try (var daemon = new ScriptedDaemon(socket(), JsonRpc.success("1", "{}"))) {
                assertThat(runWithSocket("extract-variable", "/project/Greeter.java", "7:26", "7:48", "joined"))
                        .isEqualTo(1);

                assertThat(out()).isEmpty();
                assertThat(err()).contains("(invalid-response)");
            }
        }
    }

    @Nested
    class StartingTheDaemon {

        private Path socket() {
            return directory.resolve("gabier.sock");
        }

        private Path bin() {
            return directory.resolve("bin");
        }

        private Path log() {
            return directory.resolve("gabier.sock.log");
        }

        private Map<String, String> environment() {
            return Map.of("GABIER_SOCKET", socket().toString(), "PATH", bin().toString());
        }

        @AfterEach
        void endTheDaemon() throws IOException {
            FakeDaemonScripts.end(log());
        }

        @Test
        void diagnosticsStartsTheDaemonOfThePathAndPrintsTheAnswer() throws IOException {
            FakeDaemonScripts.installListening(bin());

            assertThat(run(environment(), "diagnostics", "/project/Main.java")).isZero();

            assertThat(out()).isEmpty();
            assertThat(err()).isEmpty();
            assertThat(Files.readString(log())).startsWith("listening ");
        }

        @Test
        void renameStartsTheDaemonOfThePathAndPrintsTheAnswer() throws IOException {
            FakeDaemonScripts.installListening(bin());

            assertThat(run(environment(), "rename", "--dry-run", "/project/Greeter.java", "6", "18", "welcome"))
                    .isZero();

            assertThat(out()).isEqualTo("dry run, nothing written" + System.lineSeparator());
            assertThat(Files.readString(log())).startsWith("listening ");
        }

        @Test
        void aSecondCommandUsesTheDaemonThatIsRunningInsteadOfStartingAnother() throws IOException {
            FakeDaemonScripts.installListening(bin());

            assertThat(run(environment(), "diagnostics", "/project/Main.java")).isZero();
            assertThat(run(environment(), "diagnostics", "/project/Other.java")).isZero();

            assertThat(Files.readAllLines(log())).hasSize(1);
        }

        @Test
        void theDaemonServesTheProjectOfTheEnvironmentAndTheCurrentFolder() throws IOException {
            FakeDaemonScripts.installListening(bin());
            var environment = new HashMap<>(environment());
            environment.put("GABIER_PROJECT", "/work/demo");

            assertThat(run(environment, "diagnostics", "/project/Main.java")).isZero();

            assertThat(Files.readString(log()))
                    .contains(" project=/work/demo ")
                    .contains("cwd=" + Path.of("").toAbsolutePath().toRealPath() + " ");
        }

        @Test
        void aDaemonThatCannotBeFoundIsAProblemObjectWithExitCodeOneAndNothingOnTheStandardOutput() throws IOException {
            Files.createDirectories(bin());

            assertThat(run(environment(), "diagnostics", "/project/Main.java")).isEqualTo(1);

            assertThat(out()).isEmpty();
            assertThat(err())
                    .startsWith("Daemon start failed: The daemon did not start: gabier-daemon was not found")
                    .contains("(daemon-start-failed; socket=" + socket() + ")");
        }

        @Test
        void aDaemonThatDoesNotStartIsAProblemObjectAsJsonWithTheJsonOption() throws IOException {
            FakeDaemonScripts.install(bin(), "echo no good; exit 4");

            assertThat(run(environment(), "--json", "rename", "/project/Greeter.java", "6", "18", "welcome"))
                    .isEqualTo(1);

            assertThat(err())
                    .startsWith("{\"type\":\"urn:gabier:error:daemon-start-failed\",")
                    .contains("\"exitCode\":\"4\"")
                    .contains("\"log\":\"" + log() + "\"");
            assertThat(Files.readString(log())).isEqualTo("no good\n");
        }

        @Test
        void aUsageErrorStartsNoDaemon() throws IOException {
            var marker = directory.resolve("started");
            FakeDaemonScripts.install(bin(), "echo started > '" + marker + "'");

            assertThat(run(environment(), "rename", "/project/Greeter.java")).isEqualTo(2);
            assertThat(run(environment(), "diagnostics")).isEqualTo(2);

            assertThat(marker).doesNotExist();
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
        void statusAndStopStartNoDaemonEvenWhenOneIsOnThePath() throws Exception {
            var marker = directory.resolve("started");
            FakeDaemonScripts.install(directory.resolve("bin"), "echo started > '" + marker + "'");
            var environment = Map.of(
                    "GABIER_SOCKET",
                    socket().toString(),
                    "PATH",
                    directory.resolve("bin").toString());

            assertThat(runWithoutHanging(environment, "status")).isEqualTo(2);
            assertThat(runWithoutHanging(environment, "stop")).isEqualTo(2);

            assertThat(err()).contains("(daemon-not-running; ");
            assertThat(marker).doesNotExist();
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
