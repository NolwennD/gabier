package eu.gabier.cli.infrastructure.primary.mcp;

import static eu.gabier.cli.infrastructure.primary.mcp.McpExchange.INITIALIZE;
import static eu.gabier.cli.infrastructure.primary.mcp.McpExchange.assertError;
import static eu.gabier.cli.infrastructure.primary.mcp.McpExchange.at;
import static eu.gabier.cli.infrastructure.primary.mcp.McpExchange.exchange;
import static eu.gabier.cli.infrastructure.primary.mcp.McpExchange.modernCall;
import static eu.gabier.cli.infrastructure.primary.mcp.McpExchange.notification;
import static eu.gabier.cli.infrastructure.primary.mcp.McpExchange.number;
import static eu.gabier.cli.infrastructure.primary.mcp.McpExchange.object;
import static eu.gabier.cli.infrastructure.primary.mcp.McpExchange.request;
import static org.assertj.core.api.Assertions.assertThat;

import eu.gabier.cli.domain.DaemonRefusal;
import eu.gabier.protocol.ChangedFiles;
import eu.gabier.protocol.CodeActions;
import eu.gabier.protocol.CodeActions.Action;
import eu.gabier.protocol.DaemonStatus;
import eu.gabier.protocol.DiagnosedFiles;
import eu.gabier.protocol.FileDiagnostics;
import eu.gabier.protocol.FileDiagnostics.Diagnosis;
import eu.gabier.protocol.FileDiagnostics.Severity;
import eu.gabier.protocol.FoundReferences;
import eu.gabier.protocol.FoundReferences.Place;
import eu.gabier.protocol.JdtlsState;
import eu.gabier.protocol.Json;
import eu.gabier.protocol.SelectedTests;
import eu.gabier.shared.error.domain.ErrorStatus;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** An agent calls a tool: the operation runs as the command line runs it, and the answer is the text and the result. */
class CallingToolsTest {

    private static final ChangedFiles RENAMED = new ChangedFiles(
            List.of(),
            List.of(),
            List.of(),
            List.of(Path.of("/work/A.java")),
            "--- a/A.java\n+++ b/A.java\n@@ -1 +1 @@");
    private static final ChangedFiles NOTHING = new ChangedFiles(List.of(), List.of(), List.of(), List.of(), "");

    private static Object content(Object answer) {
        return at(answer, "result", "content", 0, "text");
    }

    private static DaemonRefusal refusal(String key) {
        return new DaemonRefusal(
                "urn:gabier:error:" + key,
                "Not extractable",
                "The selection cannot be extracted",
                key,
                Map.of("file", "A.java"),
                ErrorStatus.BAD_REQUEST);
    }

    @Nested
    class Succeeding {

        @Test
        void givesTheTextOfTheResultInContentAndTheResultOfTheDaemonInStructuredContent() {
            var diagnostics = new DiagnosedFiles(List.of(new FileDiagnostics(
                    "/work/src/Broken.java", List.of(new Diagnosis(Severity.ERROR, 4, 15, "Type mismatch")))));
            var daemon = new StubDaemon().answering(diagnostics.toJson());

            var answer = exchange(daemon, modernCall("diagnostics", "{\"files\":[\"/work/src/Broken.java\"]}"))
                    .getFirst();

            assertThat(at(answer, "result", "content"))
                    .isEqualTo(List.of(Map.of("type", "text", "text", content(answer))));
            assertThat(content(answer)).isEqualTo("/work/src/Broken.java:5:16: error: Type mismatch");
            assertThat(at(answer, "result", "structuredContent")).isEqualTo(Json.parse(diagnostics.toJson()));
            assertThat(at(answer, "result", "isError")).isEqualTo(false);
            assertThat(daemon.asked())
                    .containsExactly(
                            new StubDaemon.Asked("diagnostics", Map.of("files", "/work/src/Broken.java"), false));
        }

        @Test
        void saysThereAreNoProblemsForAFileWithNoneWhereTheCommandLinePrintsNothing() {
            var clean = new DiagnosedFiles(List.of(new FileDiagnostics("/work/src/Clean.java", List.of())));
            var daemon = new StubDaemon().answering(clean.toJson());

            var answer = exchange(daemon, modernCall("diagnostics", "{\"files\":[\"/work/src/Clean.java\"]}"))
                    .getFirst();

            assertThat(content(answer)).isEqualTo("no problems");
            assertThat(at(answer, "result", "structuredContent")).isEqualTo(Json.parse(clean.toJson()));
            assertThat(at(answer, "result", "isError")).isEqualTo(false);
        }

        @Test
        void saysTheResultIsCompleteAndWhoTheServerIsInTheModernEra() {
            var daemon = new StubDaemon().answering(NOTHING.toJson());

            var result = at(
                    exchange(daemon, modernCall("organize-imports", "{\"file\":\"/work/A.java\",\"dryRun\":false}"))
                            .getFirst(),
                    "result");

            assertThat(at(result, "resultType")).isEqualTo("complete");
            assertThat(at(result, "_meta", "io.modelcontextprotocol/serverInfo", "name"))
                    .isEqualTo("gabier");
        }

        @Test
        void answersTheEarlierEraWithTheSameResultAndNoMetadataOfTheModernOne() {
            var daemon = new StubDaemon().answering(RENAMED.toJson());

            var answers = exchange(
                    daemon, INITIALIZE, notification("notifications/initialized"), request("2", "tools/call", """
                        {"name":"inline","arguments":{"file":"/work/A.java","line":2,"column":3,"dryRun":false}}\
                        """));

            var result = at(answers.get(1), "result");
            assertThat(at(result, "isError")).isEqualTo(false);
            assertThat(content(answers.get(1)))
                    .isEqualTo(String.join(
                            "\n", "modified /work/A.java", "", "--- a/A.java", "+++ b/A.java", "@@ -1 +1 @@"));
            assertThat(at(result, "structuredContent")).isEqualTo(Json.parse(RENAMED.toJson()));
            assertThat(at(result, "resultType")).isNull();
            assertThat(at(result, "_meta")).isNull();
        }

        @Test
        void sendsThePositionsFromZeroAndSaysADryRun() {
            var daemon = new StubDaemon().answering(RENAMED.toJson());

            var answer = exchange(daemon, modernCall("rename", """
                {"file":"/work/A.java","line":6,"column":19,"newName":"welcome","dryRun":true}\
                """)).getFirst();

            assertThat((String) content(answer))
                    .startsWith("dry run, nothing written\nmodified /work/A.java\n\n--- a/A.java");
            assertThat(daemon.asked())
                    .containsExactly(new StubDaemon.Asked(
                            "rename",
                            Map.of(
                                    "file", "/work/A.java",
                                    "line", "5",
                                    "column", "18",
                                    "newName", "welcome",
                                    "dryRun", "true"),
                            false));
        }

        @Test
        void sendsAListOfFilesOnePerLine() {
            var daemon = new StubDaemon().answering(RENAMED.toJson());

            exchange(daemon, modernCall("move", """
                {"files":["/work/src/Invoice.java","/work/Bill.java"],"package":"demo.archive","dryRun":false}\
                """));

            assertThat(daemon.asked())
                    .containsExactly(new StubDaemon.Asked(
                            "move",
                            Map.of(
                                    "files", "/work/src/Invoice.java\n/work/Bill.java",
                                    "package", "demo.archive",
                                    "dryRun", "false"),
                            false));
        }

        @Test
        void refusesAListOfFilesThatHoldsSomethingElseThanTexts() {
            var daemon = new StubDaemon();

            var answer = exchange(daemon, modernCall("move", "{\"files\":[7],\"package\":\"demo\",\"dryRun\":false}"))
                    .getFirst();

            assertThat(at(answer, "result", "structuredContent", "detail"))
                    .isEqualTo("The value files of the operation move must be a list of texts");
            assertThat(daemon.asked()).isEmpty();
        }

        @Test
        void refusesOneFileWhereAListOfFilesIsAsked() {
            var daemon = new StubDaemon();

            var answer = exchange(
                            daemon,
                            modernCall("move", "{\"files\":\"/work/A.java\",\"package\":\"demo\",\"dryRun\":false}"))
                    .getFirst();

            assertThat(at(answer, "result", "structuredContent", "detail"))
                    .isEqualTo("The value files of the operation move must be a list of texts");
            assertThat(daemon.asked()).isEmpty();
        }

        @Test
        void refusesAFileThatIsNotAPathAsItIsNotAnAbsolutePath() {
            var daemon = new StubDaemon();

            var answer = exchange(
                            daemon,
                            modernCall("organize-imports", "{\"file\":\"/work/A\\u0000.java\",\"dryRun\":false}"))
                    .getFirst();

            assertThat(at(answer, "result", "structuredContent", "detail"))
                    .isEqualTo(
                            "The value file of the operation organize-imports must be an absolute path: the folder the"
                                    + " call is made from is not known");
            assertThat(daemon.asked()).isEmpty();
        }

        @Test
        void takesAValueThatIsNullAsAValueThatIsNotGiven() {
            var daemon = new StubDaemon().answering(NOTHING.toJson());

            var answer = exchange(daemon, modernCall("organize-imports", "{\"file\":\"/work/A.java\",\"dryRun\":null}"))
                    .getFirst();

            assertThat(at(answer, "result", "isError")).isEqualTo(true);
            assertThat(at(answer, "result", "structuredContent", "detail"))
                    .isEqualTo("The operation organize-imports needs the value dryRun");
            assertThat(daemon.asked()).isEmpty();
        }

        @Test
        void warnsBeforeTheFilesAndSaysNothingToChangeWhenTheChangeIsEmpty() {
            var daemon = new StubDaemon()
                    .answering(
                            NOTHING.withWarnings(List.of("two files disagree")).toJson());

            var answer = exchange(
                            daemon,
                            modernCall("cleanup", "{\"file\":\"/work/A.java\",\"cleanUps\":[\"x\"],\"dryRun\":true}"))
                    .getFirst();

            assertThat(content(answer))
                    .isEqualTo("dry run, nothing written\nwarning: two files disagree\nnothing to change");
        }

        @Test
        void listsTheReferences() {
            var found = new FoundReferences(
                    List.of(new Place("/work/A.java", 2, 4, "    void run() {")),
                    List.of(new Place("/work/B.java", 9, 8, "        run();")));
            var daemon = new StubDaemon().answering(found.toJson());

            var answer = exchange(daemon, modernCall("references", """
                {"file":"/work/A.java","line":3,"column":5,"includeDeclaration":true}\
                """)).getFirst();

            assertThat(content(answer))
                    .isEqualTo("declaration:\n/work/A.java:3:5: void run() {\nreferences:\n/work/B.java:10:9: run();");
            assertThat(at(answer, "result", "structuredContent")).isEqualTo(Json.parse(found.toJson()));
        }

        @Test
        void listsTheTestsAChangeAffects() {
            var selected = new SelectedTests(List.of("demo.LedgerTest", "demo.app.BankTest"));
            var daemon = new StubDaemon().answering(selected.toJson());

            var answer = exchange(daemon, modernCall("affected-tests", """
                {"files":["/work/Ledger.java"]}\
                """)).getFirst();

            assertThat(content(answer)).isEqualTo("demo.LedgerTest\ndemo.app.BankTest");
            assertThat(at(answer, "result", "structuredContent")).isEqualTo(Json.parse(selected.toJson()));
        }

        @Test
        void listsTheTestsOfWhatChangedSinceTheLastAnswerWithNoFileToName() {
            var selected = new SelectedTests(List.of("demo.LedgerTest"));
            var daemon = new StubDaemon().answering(selected.toJson());

            var answer =
                    exchange(daemon, modernCall("affected-tests-changed", "{}")).getFirst();

            assertThat(content(answer)).isEqualTo("demo.LedgerTest");
            assertThat(at(answer, "result", "structuredContent")).isEqualTo(Json.parse(selected.toJson()));
        }

        @Test
        void listsTheActionsOfARange() {
            var offered = new CodeActions(List.of(new Action("quickfix", "Import 'List' (java.util)")));
            var daemon = new StubDaemon().answering(offered.toJson());

            var answer = exchange(daemon, modernCall("actions", """
                {"file":"/work/A.java","startLine":4,"startColumn":5,"endLine":4,"endColumn":9}\
                """)).getFirst();

            assertThat(content(answer)).isEqualTo("1 quickfix Import 'List' (java.util)");
        }

        @Test
        void tellsTheStatusOfADaemonThatRunsAndStartsNone() {
            var status = new DaemonStatus("0.1", 4242, Duration.ofSeconds(65), Path.of("/work/demo"), JdtlsState.READY);
            var daemon = new StubDaemon().answering(status.toJson());

            var answer = exchange(daemon, modernCall("status", "{}")).getFirst();

            assertThat((String) content(answer))
                    .startsWith("gabier-daemon 0.1, pid 4242, up 65s, project /work/demo, jdtls ready\nimports:\n  ");
            assertThat(at(answer, "result", "structuredContent")).isEqualTo(Json.parse(status.toJson()));
            assertThat(daemon.asked()).containsExactly(new StubDaemon.Asked("status", Map.of(), true));
        }

        @Test
        void takesNoArgumentsAsAnEmptyObject() {
            var status = new DaemonStatus("0.1", 4242, Duration.ofSeconds(65), Path.of("/work/demo"), JdtlsState.READY);
            var daemon = new StubDaemon().answering(status.toJson());

            var answer = exchange(daemon, modernCall("\"name\":\"status\"")).getFirst();

            assertThat(at(answer, "result", "isError")).isEqualTo(false);
        }
    }

    @Nested
    class Refusing {

        @Test
        void tellsTheProblemOfARefusalOfTheDaemonAsAFailedCallWithThePointerToTheHelp() {
            var daemon = new StubDaemon().failing(refusal("language-server-not-extractable"));

            var answer = exchange(daemon, modernCall("extract-method", """
                {"file":"/work/A.java","startLine":1,"startColumn":1,"endLine":1,"endColumn":2,\
                "name":"m","dryRun":false}\
                """)).getFirst();

            var result = at(answer, "result");
            assertThat(at(result, "isError")).isEqualTo(true);
            assertThat(content(answer)).isEqualTo("""
                Not extractable: The selection cannot be extracted (language-server-not-extractable; file=A.java)
                Usage and other ways: gabier help command-of-extract-method\
                """);
            var problem = at(result, "structuredContent");
            assertThat(at(problem, "key")).isEqualTo("language-server-not-extractable");
            assertThat(at(problem, "title")).isEqualTo("Not extractable");
            assertThat(at(problem, "detail")).isEqualTo("The selection cannot be extracted");
            assertThat(at(problem, "parameters")).isEqualTo(Map.of("file", "A.java"));
            assertThat(at(problem, "help")).isEqualTo("Usage and other ways: gabier help command-of-extract-method");
        }

        @Test
        void givesNoPointerToARefusalThatOtherWaysDoNotHelp() {
            var daemon = new StubDaemon().failing(refusal("editing-invalid-range"));

            var answer = exchange(daemon, modernCall("diagnostics", "{\"files\":[\"/work/A.java\"]}"))
                    .getFirst();

            assertThat(at(answer, "result", "isError")).isEqualTo(true);
            assertThat(content(answer))
                    .isEqualTo(
                            "Not extractable: The selection cannot be extracted (editing-invalid-range; file=A.java)");
            assertThat(at(answer, "result", "structuredContent", "key")).isEqualTo("editing-invalid-range");
            assertThat(object(at(answer, "result", "structuredContent"))).doesNotContainKey("help");
        }

        @Test
        void tellsAValueThatIsMissingUnknownOrOfAnotherKindAsAFailedCallAndAsksNoDaemon() {
            var daemon = new StubDaemon();

            var answers = exchange(
                    daemon,
                    modernCall("rename", "{\"file\":\"/work/A.java\"}"),
                    modernCall("diagnostics", "{\"files\":[\"/work/A.java\"],\"verbose\":true}"),
                    modernCall("diagnostics", "{\"files\":7}"),
                    modernCall("rename", """
                        {"file":"/work/A.java","line":0,"column":1,"newName":"a","dryRun":false}\
                        """));

            assertThat(answers).hasSize(4).allSatisfy(answer -> {
                assertThat(at(answer, "result", "isError")).isEqualTo(true);
                assertThat(at(answer, "result", "structuredContent", "key")).isEqualTo("operation-invalid-values");
                assertThat(at(answer, "error")).isNull();
            });
            assertThat(content(answers.get(0)))
                    .isEqualTo("Operation invalid values: The operation rename needs the value line"
                            + " (operation-invalid-values; operation=rename, parameter=line)");
            assertThat(at(answers.get(1), "result", "structuredContent", "parameters"))
                    .isEqualTo(Map.of("operation", "diagnostics", "parameter", "verbose"));
            assertThat(at(answers.get(2), "result", "structuredContent", "detail"))
                    .isEqualTo("The value files of the operation diagnostics must be a list of texts");
            assertThat(at(answers.get(3), "result", "structuredContent", "detail"))
                    .isEqualTo("The value line of the operation rename must be a position, from 1");
            assertThat(daemon.asked()).isEmpty();
        }

        @Test
        void refusesAnEmptyFileAsAnInvalidValueInsteadOfSendingTheFolderOfTheServer() {
            var daemon = new StubDaemon();

            var answers = exchange(
                    daemon,
                    modernCall("diagnostics", "{\"files\":[\"\"]}"),
                    modernCall("move", "{\"files\":[\"/work/A.java\",\"\"],\"package\":\"demo\",\"dryRun\":false}"));

            assertThat(answers).hasSize(2).allSatisfy(answer -> {
                assertThat(at(answer, "result", "isError")).isEqualTo(true);
                assertThat(at(answer, "result", "structuredContent", "key")).isEqualTo("operation-invalid-values");
            });
            assertThat(at(answers.get(0), "result", "structuredContent", "detail"))
                    .isEqualTo("The value files of the operation diagnostics must not have an empty element");
            assertThat(at(answers.get(1), "result", "structuredContent", "detail"))
                    .isEqualTo("The value files of the operation move must not have an empty element");
            assertThat(daemon.asked()).isEmpty();
        }

        @Test
        void tellsAnErrorOfTheProcessAsAFailedCallAndGoesOn() {
            var log = new ByteArrayOutputStream();
            var daemon = new StubDaemon().crashing(new NoClassDefFoundError("java/lang/ProcessBuilder"));

            var answers = exchange(
                    daemon,
                    new PrintStream(log, true, StandardCharsets.UTF_8),
                    modernCall("1", "diagnostics", "{\"files\":[\"/work/A.java\"]}"),
                    McpExchange.modern("2", "ping"));

            assertThat(answers).hasSize(2);
            var call = answers.stream()
                    .filter(answer -> at(answer, "id").equals(number(1)))
                    .findFirst()
                    .orElseThrow();
            assertThat(at(call, "result", "isError")).isEqualTo(true);
            assertThat(at(call, "result", "structuredContent", "key")).isEqualTo("internal-server-error");
            assertThat(log.toString(StandardCharsets.UTF_8))
                    .contains("gabier mcp: the call of diagnostics failed")
                    .contains("java/lang/ProcessBuilder");
        }

        @Test
        void tellsADaemonThatCannotBeReachedAsAFailedCallWithoutItsInternals() {
            var daemon = new StubDaemon().failing(new IllegalStateException("socket /secret/path broke"));

            var answer = exchange(daemon, modernCall("diagnostics", "{\"files\":[\"/work/A.java\"]}"))
                    .getFirst();

            assertThat(at(answer, "result", "isError")).isEqualTo(true);
            assertThat(at(answer, "result", "structuredContent", "key")).isEqualTo("internal-server-error");
            assertThat((String) content(answer)).doesNotContain("/secret/path");
        }

        @Test
        void logsAnUnexpectedFailureOnTheStandardErrorWithItsCauseAndKeepsItOutOfTheResult() {
            var log = new ByteArrayOutputStream();
            var daemon = new StubDaemon()
                    .failing(new IllegalStateException("socket broke", new IOException("disk /secret/path is full")));

            var answer = exchange(
                            daemon,
                            new PrintStream(log, true, StandardCharsets.UTF_8),
                            modernCall("diagnostics", "{\"files\":[\"/work/A.java\"]}"))
                    .getFirst();

            assertThat(log.toString(StandardCharsets.UTF_8))
                    .contains("gabier mcp: the call of diagnostics failed")
                    .contains("java.lang.IllegalStateException: socket broke")
                    .contains("Caused by: java.io.IOException: disk /secret/path is full");
            assertThat((String) content(answer)).doesNotContain("socket broke").doesNotContain("/secret/path");
            assertThat(at(answer, "result", "structuredContent", "key")).isEqualTo("internal-server-error");
        }

        @Test
        void logsNothingForAFailureThatIsARefusalTheAgentReads() {
            var log = new ByteArrayOutputStream();
            var daemon = new StubDaemon().failing(refusal("language-server-not-extractable"));

            exchange(
                    daemon,
                    new PrintStream(log, true, StandardCharsets.UTF_8),
                    modernCall("diagnostics", "{\"files\":[\"/work/A.java\"]}"),
                    modernCall("diagnostics", "{}"));

            assertThat(log.toString(StandardCharsets.UTF_8)).isEmpty();
        }

        @Test
        void goesOnToAnswerTheNextRequestAfterAFailedCall() {
            var daemon = new StubDaemon().failing(new IllegalStateException("boom"));

            var answers = exchange(
                    daemon,
                    modernCall("diagnostics", "{\"files\":[\"/work/A.java\"]}"),
                    McpExchange.modern("2", "ping"));

            assertThat(answers).hasSize(2);
            assertThat(answers)
                    .extracting(answer -> at(answer, "id"))
                    .containsExactlyInAnyOrder(McpExchange.number(1), McpExchange.number(2));
        }
    }

    @Nested
    class RefusingTheCall {

        @Test
        void saysAToolThatDoesNotExistIsAnInvalidParameter() {
            var answer = exchange(modernCall("frobnicate", "{}")).getFirst();

            assertError(answer, -32602, "Invalid params: Unknown tool: frobnicate");
        }

        @ParameterizedTest
        @ValueSource(strings = {"start", "stop"})
        void saysTheStartAndTheStopOfTheDaemonAreNoTools(String name) {
            var daemon = new StubDaemon();

            assertError(
                    exchange(daemon, modernCall(name, "{}")).getFirst(),
                    -32602,
                    "Invalid params: Unknown tool: " + name);
            assertThat(daemon.asked()).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(strings = {"\"arguments\":{}", "\"name\":7,\"arguments\":{}", "\"name\":null"})
        void refusesACallWhoseNameIsMissingOrNotText(String members) {
            assertError(exchange(modernCall(members)).getFirst(), -32602, "Invalid params: name must be a text");
        }

        @ParameterizedTest
        @ValueSource(strings = {"\"text\"", "[]", "7", "null"})
        void refusesArgumentsThatAreNotAnObject(String arguments) {
            var daemon = new StubDaemon();

            assertError(
                    exchange(daemon, modernCall("diagnostics", arguments)).getFirst(),
                    -32602,
                    "Invalid params: arguments must be an object");
            assertThat(daemon.asked()).isEmpty();
        }

        @Test
        void refusesACallBeforeAnyInitializationWhenItNamesNoVersion() {
            var answer = exchange(request("1", "tools/call", "{\"name\":\"status\"}"))
                    .getFirst();

            assertError(answer, -32602, "Invalid params: _meta has no io.modelcontextprotocol/protocolVersion");
        }

        @Test
        void refusesAVersionTheServerDoesNotServe() {
            var line = request("1", "tools/call", """
                {"name":"status","_meta":{"io.modelcontextprotocol/protocolVersion":"2024-01-01"}}\
                """);

            assertError(exchange(line).getFirst(), -32022, "Unsupported protocol version");
        }
    }
}
