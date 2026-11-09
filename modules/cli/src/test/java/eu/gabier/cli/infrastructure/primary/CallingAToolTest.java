package eu.gabier.cli.infrastructure.primary;

import static eu.gabier.cli.infrastructure.primary.CliFixtures.MODIFIED;
import static org.assertj.core.api.Assertions.assertThat;

import eu.gabier.cli.Gabier;
import eu.gabier.cli.domain.Operations;
import eu.gabier.protocol.FileDiagnostics;
import eu.gabier.protocol.FileDiagnostics.Diagnosis;
import eu.gabier.protocol.FileDiagnostics.Severity;
import eu.gabier.protocol.Json;
import eu.gabier.protocol.JsonRpc;
import eu.gabier.protocol.Problem;
import eu.gabier.shared.error.domain.ErrorStatus;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * An agent calls a tool of {@code gabier mcp}: the daemon is asked what the command line asks it, and the content of
 * the answer is the text the command line prints for the same case, on the standard output and nothing else.
 */
class CallingAToolTest extends CliTest {

    private static final String MODERN_META = """
        "_meta":{"io.modelcontextprotocol/protocolVersion":"2026-07-28","io.modelcontextprotocol/clientCapabilities":{}}\
        """;

    /** The ids of the calls: each is its own, since a call that has not ended owns its id. */
    private static final AtomicInteger IDS = new AtomicInteger();

    @AfterEach
    void endTheDaemon() throws IOException {
        FakeDaemonScripts.end(directory.resolve("gabier.sock.log"));
    }

    private static String call(String name, String arguments) {
        return """
            {"jsonrpc":"2.0","id":%d,"method":"tools/call","params":{"name":"%s","arguments":%s,%s}}\
            """.formatted(IDS.incrementAndGet(), name, arguments, MODERN_META);
    }

    /** What {@code gabier mcp} wrote on the standard output for these lines, one JSON value for each answer. */
    private List<@Nullable Object> mcp(Map<String, String> environment, String... lines) {
        var input = String.join("\n", lines) + "\n";
        out.reset();
        err.reset();
        var exit = Gabier.run(
                new String[] {"mcp"},
                environment,
                new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),
                printStream(out),
                printStream(err));
        assertThat(exit).isZero();

        return out().lines().map(Json::parse).toList();
    }

    private List<@Nullable Object> mcp(String... lines) {
        return mcp(Map.of("GABIER_SOCKET", socket().toString()), lines);
    }

    @SuppressWarnings("unchecked") // The server writes JSON objects, and the tests know where.
    private static Map<String, @Nullable Object> result(@Nullable Object answer) {
        return (Map<String, @Nullable Object>) ((Map<String, @Nullable Object>) answer).get("result");
    }

    @SuppressWarnings("unchecked") // The content of a result is a list of JSON objects.
    private static String text(Map<String, @Nullable Object> result) {
        var content = (List<Map<String, @Nullable Object>>) result.get("content");

        return (String) content.getFirst().get("text");
    }

    /** What the command line printed on this stream, without the line break that ends the last line. */
    private static String printed(String stream) {
        return stream.stripTrailing();
    }

    @Test
    void diagnosticsSaysWhatTheCommandLinePrintsAndGivesTheResultOfTheDaemon() throws IOException {
        var diagnostics = new FileDiagnostics(List.of(
                new Diagnosis(Severity.ERROR, 4, 15, "Type mismatch"),
                new Diagnosis(Severity.WARNING, 0, 0, "Careful")));
        try (var daemon = daemonAnswering(diagnostics.toJson())) {
            assertThat(runWithSocket("diagnostics", "/project/Broken.java")).isZero();
            var printed = printed(out());
            out.reset();
            assertThat(runWithSocket("--json", "diagnostics", "/project/Broken.java"))
                    .isZero();
            var json = printed(out());

            var result = result(mcp(call("diagnostics", "{\"file\":\"/project/Broken.java\"}"))
                    .getFirst());

            assertThat(text(result)).isEqualTo(printed);
            assertThat(result.get("structuredContent")).isEqualTo(Json.parse(json));
            assertThat(result.get("isError")).isEqualTo(false);
            assertThat(daemon.requests()).hasSize(3);
            assertThat(daemon.requests().get(2)).isEqualTo(daemon.requests().get(0));
        }
    }

    @Test
    void renameWithADryRunSaysWhatTheCommandLinePrintsAndSendsTheSameRequest() throws IOException {
        try (var daemon = daemonAnswering(MODIFIED.toJson())) {
            assertThat(runWithSocket("rename", "--dry-run", "/project/Greeter.java", "6:19", "welcome"))
                    .isZero();
            var printed = printed(out());

            var result = result(mcp(call("rename", """
                {"file":"/project/Greeter.java","line":6,"column":19,"newName":"welcome","dryRun":true}\
                """)).getFirst());

            assertThat(text(result)).isEqualTo(printed).startsWith("dry run, nothing written");
            assertThat(result.get("structuredContent")).isEqualTo(Json.parse(MODIFIED.toJson()));
            assertThat(daemon.requests()).hasSize(2);
            assertThat(daemon.requests().get(1)).isEqualTo(daemon.requests().get(0));
        }
    }

    @Test
    void moveTakesItsFilesAsAnArrayAndSendsTheSameRequestAsTheCommandLine() throws IOException {
        try (var daemon = daemonAnswering(MODIFIED.toJson())) {
            assertThat(runWithSocket("move", "/project/Invoice.java", "/project/Payment.java", "demo.billing"))
                    .isZero();
            var printed = printed(out());

            var result = result(mcp(call("move", """
                {"files":["/project/Invoice.java","/project/Payment.java"],"package":"demo.billing","dryRun":false}\
                """)).getFirst());

            assertThat(text(result)).isEqualTo(printed);
            assertThat(daemon.requests()).hasSize(2);
            assertThat(daemon.requests().get(1)).isEqualTo(daemon.requests().get(0));
        }
    }

    @Test
    void aRefusalOfTheDaemonIsAFailedCallWithWhatTheCommandLinePrintsOnTheStandardError() throws IOException {
        var problem = new Problem(
                "urn:gabier:error:language-server-not-extractable",
                "Language server not extractable",
                "The selection cannot be extracted",
                "language-server-not-extractable",
                Map.of("file", "/project/Totals.java"),
                ErrorStatus.BAD_REQUEST);
        try (var daemon = new ScriptedDaemon(socket(), JsonRpc.failure("1", problem))) {
            assertThat(runWithSocket("extract-method", "/project/Totals.java", "6:9", "6:47", "printCount"))
                    .isEqualTo(2);
            var printed = printed(err());

            var result = result(mcp(call("extract-method", """
                {"file":"/project/Totals.java","startLine":6,"startColumn":9,"endLine":6,"endColumn":47,\
                "name":"printCount","dryRun":false}\
                """)).getFirst());

            assertThat(result.get("isError")).isEqualTo(true);
            assertThat(text(result)).isEqualTo(printed);
            assertThat(err()).isEmpty();
            assertThat(daemon.requests()).hasSize(2);
        }
    }

    @Test
    void aValueThatDoesNotFitTheOperationIsAFailedCallThatAsksNoDaemon() throws IOException {
        try (var daemon = daemonAnswering(MODIFIED.toJson())) {
            var result =
                    result(mcp(call("rename", "{\"file\":\"/project/A.java\"}")).getFirst());

            assertThat(result.get("isError")).isEqualTo(true);
            assertThat(text(result))
                    .contains("The operation rename needs the value line")
                    .contains("(operation-invalid-values;");
            assertThat(daemon.requests()).isEmpty();
        }
    }

    @Test
    void aDaemonThatCannotBeStartedIsAFailedCallAndTheServerGoesOn() throws IOException {
        var bin = directory.resolve("bin");
        Files.createDirectories(bin);
        var environment = Map.of("GABIER_SOCKET", socket().toString(), "PATH", bin.toString());

        var answers = mcp(
                environment,
                call("diagnostics", "{\"file\":\"/project/Main.java\"}"),
                call("diagnostics", "{\"file\":\"/project/Main.java\"}"));

        assertThat(answers).hasSize(2).allSatisfy(answer -> {
            var result = result(answer);
            assertThat(result.get("isError")).isEqualTo(true);
            assertThat(text(result))
                    .startsWith("Daemon start failed: The daemon did not start: gabier-daemon was not found")
                    .contains("(daemon-start-failed; socket=" + socket() + ")");
        });
        assertThat(err()).isEmpty();
    }

    @Test
    void aCallThatReachesNoDaemonStartsTheDaemonOfThePathOnce() throws IOException {
        var bin = directory.resolve("bin");
        FakeDaemonScripts.installListening(bin);
        var environment = Map.of("GABIER_SOCKET", socket().toString(), "PATH", bin.toString());

        var answers = mcp(
                environment,
                call("diagnostics", "{\"file\":\"/project/Main.java\"}"),
                call("diagnostics", "{\"file\":\"/project/Other.java\"}"));

        assertThat(answers).hasSize(2).allSatisfy(answer -> {
            assertThat(result(answer).get("isError")).isEqualTo(false);
            assertThat(result(answer).get("structuredContent")).isEqualTo(Map.of("diagnostics", List.of()));
        });
        assertThat(Files.readAllLines(directory.resolve("gabier.sock.log"))).hasSize(1);
        assertThat(err()).isEmpty();
    }

    @Test
    void theHelpPointerOfAFailedCallNamesTheCommandThatRunsTheOperation() {
        for (var operation : Operations.all()) {
            assertThat(Capabilities.named(Capabilities.commandOf(operation))).isPresent();
        }
        assertThat(Capabilities.commandOf(Operations.RENAME_TOKEN)).isEqualTo("rename");
        assertThat(Capabilities.commandOf(Operations.MOVE_TYPE_TO_FILE)).isEqualTo("move-type-to-file");
    }
}
