package eu.gabier.cli.infrastructure.primary.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import eu.gabier.cli.application.OperationRunner;
import eu.gabier.cli.domain.Daemon;
import eu.gabier.cli.domain.Guide;
import eu.gabier.protocol.Json;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * What the tests of the MCP server share: the lines a client writes (a request of each era, a notification), a run of
 * the server over streams, and the way to read the JSON it wrote.
 */
final class McpExchange {

    /** The handshake of the earlier era, as a host of that era opens it. */
    static final String INITIALIZE = request("1", "initialize", """
        {"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"host","version":"1"}}\
        """);

    /** The guide a server of the tests answers from: six situations, not the real guide, which a test of the cli reads. */
    static final String GUIDE = """
        ### Rename a method

        Words: rename symbol, change name, method

        Ask: `gabier rename <file> <line>:<column> <new-name>`

        ### Rename a package

        Words: rename symbol, change name, package

        Ask: `gabier rename <file> <line>:<column> <new-name>`

        ### Remove an else

        Words: unnecessary else, else after return

        By hand: remove the else

        ### Make a text block

        Words: text block, multi-line string

        Ask: `gabier cleanup --dry-run <file> stringConcatToTextBlock`

        ### Order the imports

        Words: organize imports, sort imports

        Ask: `gabier organize-imports <file>`

        ### Move a class

        Words: move class, other package

        Ask: `gabier move <file>... <package>`
        """;

    /** What a server of the tests tells a host to do with it: not the real text, which a test of the cli reads. */
    static final String INSTRUCTIONS = "Use the tools of gabier, not text edits.\nPositions count from 1.";

    private static final String MODERN_META = """
        "_meta":{"io.modelcontextprotocol/protocolVersion":"2026-07-28","io.modelcontextprotocol/clientCapabilities":{}}\
        """;

    private McpExchange() {}

    /** The line of a request of the modern era, with its per-request metadata. */
    static String modern(String id, String method) {
        return """
            {"jsonrpc":"2.0","id":%s,"method":"%s","params":{%s}}\
            """.formatted(id, method, MODERN_META);
    }

    /** The line of a call of a tool in the modern era; the members of its params, but the metadata, are given as JSON. */
    static String modernCall(String members) {
        return request("1", "tools/call", "{" + members + "," + MODERN_META + "}");
    }

    /** The line of a call of the tool of this name with these arguments, as JSON, in the modern era. */
    static String modernCall(String name, String arguments) {
        return modernCall("\"name\":" + Json.quote(name) + ",\"arguments\":" + arguments);
    }

    /** The line of a request with these params, as JSON. */
    static String request(String id, String method, String params) {
        return """
            {"jsonrpc":"2.0","id":%s,"method":"%s","params":%s}\
            """.formatted(id, method, params);
    }

    static String notification(String method) {
        return """
            {"jsonrpc":"2.0","method":"%s"}\
            """.formatted(method);
    }

    /**
     * A server over this daemon. The command that does an operation is named after it, here, so that a test sees which
     * one a pointer to the help names.
     */
    static McpServer serverOf(Daemon daemon) {
        return serverOf(daemon, silent());
    }

    /** A server over this daemon, that logs on this stream. */
    static McpServer serverOf(Daemon daemon, PrintStream err) {
        return serverOf(daemon, err, noWarmUp());
    }

    /** A server over this daemon that begins this warm-up at its first handshake. */
    static McpServer serverOf(Daemon daemon, WarmUp warmUp) {
        return serverOf(daemon, silent(), warmUp);
    }

    private static McpServer serverOf(Daemon daemon, PrintStream err, WarmUp warmUp) {
        var backend = new McpServer.Backend(
                new OperationRunner(daemon), operation -> "command-of-" + operation.method(), new Guide(GUIDE));

        return new McpServer(backend, err, INSTRUCTIONS, warmUp);
    }

    /** A warm-up that starts nothing, for the tests that are not about it. */
    static WarmUp noWarmUp() {
        return new WarmUp(() -> {}, silent(), Runnable::run);
    }

    /** A stream that keeps nothing, for the log that a test does not read. */
    static PrintStream silent() {
        return new PrintStream(OutputStream.nullOutputStream());
    }

    /** What the server wrote for these lines of input, as the JSON of each line of output; it never reaches a daemon. */
    static List<@Nullable Object> exchange(String... lines) {
        return exchange(new StubDaemon(), lines);
    }

    /** What the server over this daemon wrote for these lines of input, as the JSON of each line of output. */
    static List<@Nullable Object> exchange(Daemon daemon, String... lines) {
        return exchange(daemon, silent(), lines);
    }

    /** As {@link #exchange(Daemon, String...)}, with the server logging on this stream. */
    static List<@Nullable Object> exchange(Daemon daemon, PrintStream err, String... lines) {
        return exchange(
                daemon, err, new ByteArrayInputStream(String.join("\n", lines).getBytes(StandardCharsets.UTF_8)));
    }

    static List<@Nullable Object> exchange(InputStream in) {
        return exchange(new StubDaemon(), silent(), in);
    }

    /** What this server wrote for these lines of input, as the JSON of each line of output. */
    static List<@Nullable Object> exchange(McpServer server, String... lines) {
        return exchange(
                server, new ByteArrayInputStream(String.join("\n", lines).getBytes(StandardCharsets.UTF_8)));
    }

    private static List<@Nullable Object> exchange(Daemon daemon, PrintStream err, InputStream in) {
        return exchange(serverOf(daemon, err), in);
    }

    private static List<@Nullable Object> exchange(McpServer server, InputStream in) {
        var out = new ByteArrayOutputStream();
        server.serve(in, new PrintStream(out, true, StandardCharsets.UTF_8));

        return out.toString(StandardCharsets.UTF_8).lines().map(Json::parse).toList();
    }

    /** The value at this path of members and indexes. */
    static @Nullable Object at(@Nullable Object json, Object... path) {
        var current = json;
        for (var step : path) {
            current = step instanceof Integer index
                    ? list(current).get(index)
                    : object(current).get(step);
        }

        return current;
    }

    @SuppressWarnings("unchecked") // The server writes JSON objects, and the tests know where.
    static Map<String, @Nullable Object> object(@Nullable Object json) {
        return (Map<String, @Nullable Object>) json;
    }

    @SuppressWarnings("unchecked") // The server writes JSON arrays, and the tests know where.
    static List<@Nullable Object> list(@Nullable Object json) {
        return (List<@Nullable Object>) json;
    }

    /** A JSON number, as the reader of the protocol gives it. */
    static BigDecimal number(int value) {
        return new BigDecimal(value);
    }

    /** The answer is an error of this code, whose message is this text, or starts with it for a message that explains. */
    static void assertError(@Nullable Object answer, int code, String message) {
        assertThat(at(answer, "error", "code")).isEqualTo(number(code));
        assertThat((String) at(answer, "error", "message")).startsWith(message);
        assertThat(object(answer)).doesNotContainKey("result");
    }
}
