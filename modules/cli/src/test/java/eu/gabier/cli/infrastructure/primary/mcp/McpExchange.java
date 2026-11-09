package eu.gabier.cli.infrastructure.primary.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import eu.gabier.cli.application.OperationRunner;
import eu.gabier.cli.domain.Daemon;
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
        return new McpServer(new OperationRunner(daemon), operation -> "command-of-" + operation.method(), err);
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

    private static List<@Nullable Object> exchange(Daemon daemon, PrintStream err, InputStream in) {
        var out = new ByteArrayOutputStream();
        serverOf(daemon, err).serve(in, new PrintStream(out, true, StandardCharsets.UTF_8));

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
