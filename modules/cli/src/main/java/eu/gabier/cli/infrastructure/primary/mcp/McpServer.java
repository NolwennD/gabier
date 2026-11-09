package eu.gabier.cli.infrastructure.primary.mcp;

import eu.gabier.cli.application.OperationRunner;
import eu.gabier.cli.domain.Guide;
import eu.gabier.cli.domain.Operation;
import eu.gabier.protocol.Json;
import eu.gabier.protocol.ProtocolVersion;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * The MCP server of gabier on the stdio transport, as a primary adapter: one JSON-RPC 2.0 message per line on the input
 * and on the output, and nothing else on the output (what the server has to say besides goes to the standard error).
 * It speaks the two eras of the protocol. In the modern one (revision 2026-07-28) there is no handshake: every request
 * carries its protocol version and the capabilities of the client in its {@code _meta}, and every result says it is
 * {@code complete}; {@code server/discover} tells what the server is. In the earlier one (revision 2025-11-25 and
 * before) the client opens with {@code initialize} and {@code notifications/initialized}, and its requests carry no
 * metadata. A request is served in the era it names, or, with no metadata, in the earlier one once {@code initialize}
 * has come.
 *
 * <p>No SDK and no library: the cli is a native image. The only state is whether {@code initialize} has come.
 */
public final class McpServer {

    private static final String PROTOCOL_VERSION_KEY = "io.modelcontextprotocol/protocolVersion";
    private static final String CLIENT_CAPABILITIES_KEY = "io.modelcontextprotocol/clientCapabilities";

    private static final String MODERN_VERSION = "2026-07-28";
    private static final String EARLIER_VERSION = "2025-11-25";

    /** The versions a request may name in its metadata, the newest first, as {@code server/discover} lists them. */
    private static final List<String> SUPPORTED_VERSIONS = List.of(MODERN_VERSION, EARLIER_VERSION);

    /** The versions of the earlier era that {@code initialize} answers with as they were asked. */
    private static final List<String> EARLIER_ERA_VERSIONS =
            List.of("2024-11-05", "2025-03-26", "2025-06-18", EARLIER_VERSION);

    /** The tools do not change while the server runs: an hour is a hint, not a promise ({@code ttlMs}). */
    private static final int TTL_MILLISECONDS = 3_600_000;

    private static final String SERVER_INFO =
            "{\"name\":\"gabier\",\"version\":" + Json.quote(ProtocolVersion.CURRENT.value()) + "}";

    private final Tools tools = Tools.served();
    private final String toolsJson = tools.json();
    private final ToolCalls calls;
    private final String instructions;
    private final WarmUp warmUp;
    private boolean initialized;

    /**
     * What the tools lean on: the application, which runs an operation and answers a question to the guide.
     *
     * @param operations how an operation is run, the one entry point that any front end uses
     * @param commandOf the word of the command line that runs an operation, for the pointer to its help
     * @param guide the guide that the tool {@code guide} is asked
     */
    public record Backend(OperationRunner operations, Function<Operation, String> commandOf, Guide guide) {}

    /**
     * A server of the operations of the daemon, and of the guide.
     *
     * @param backend what the tools run
     * @param err where the server logs what it must not say on its output, such as a call that failed unexpectedly
     * @param instructions what the host is told about using the tools, in its handshake, as one text
     * @param warmUp what starts the daemon at the first handshake, so that it is ready when the first call comes
     */
    public McpServer(Backend backend, PrintStream err, String instructions, WarmUp warmUp) {
        calls = new ToolCalls(tools, backend, err);
        this.instructions = instructions;
        this.warmUp = warmUp;
    }

    /** The era a request is served in. */
    private enum Era {
        MODERN,
        EARLIER
    }

    /**
     * Serves the client until it closes its output (the end of the input): reads a line, answers it when it is a
     * request, and goes on, whatever the line holds.
     *
     * @param in what the client writes, read as UTF-8
     * @param out where the answers go, one line each, written as UTF-8 whatever the encoding of the stream
     */
    public void serve(InputStream in, PrintStream out) {
        new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                .lines()
                .filter(line -> !line.isBlank())
                .forEach(line -> answer(line).ifPresent(answer -> write(out, answer)));
    }

    private Optional<String> answer(String line) {
        return switch (Incoming.of(line)) {
            case Incoming.Silent _ -> Optional.empty();
            case Incoming.Refused refused -> Optional.of(refused.failure().toJson(refused.id()));
            case Incoming.Request request -> Optional.of(answer(request));
        };
    }

    private String answer(Incoming.Request request) {
        try {
            return "{\"jsonrpc\":\"2.0\",\"id\":" + request.id() + ",\"result\":" + result(request) + "}";
        } catch (McpFailure failure) {
            return failure.toJson(request.id());
        }
    }

    private String result(Incoming.Request request) {
        return switch (request.method()) {
            case "initialize" -> initialize(request.params());
            case "ping" -> "{}";
            case "server/discover" -> discover(request.params());
            case "tools/list" -> toolsList(request.params());
            case "tools/call" -> toolsCall(request.params());
            default -> throw McpFailure.methodNotFound();
        };
    }

    /** The handshake of the earlier era: the version asked when the server knows it, else its latest. */
    private String initialize(Map<String, @Nullable Object> params) {
        if (!(params.get("protocolVersion") instanceof String asked)) {
            throw McpFailure.invalidParams("protocolVersion must be a text");
        }
        initialized = true;
        warmUp.begin();
        var version = EARLIER_ERA_VERSIONS.contains(asked) ? asked : EARLIER_VERSION;

        return "{\"protocolVersion\":" + Json.quote(version) + ",\"capabilities\":" + capabilities()
                + ",\"serverInfo\":" + SERVER_INFO + ",\"instructions\":" + Json.quote(instructions) + "}";
    }

    /** {@code server/discover}, which is of the modern era whatever the metadata of the request says of its version. */
    private String discover(Map<String, @Nullable Object> params) {
        eraOf(params);
        warmUp.begin();

        return modern("\"supportedVersions\":" + versions() + ",\"capabilities\":" + capabilities()
                + ",\"instructions\":" + Json.quote(instructions) + "," + caching());
    }

    private String toolsList(Map<String, @Nullable Object> params) {
        var tools = "\"tools\":" + toolsJson;

        return switch (eraOf(params)) {
            case MODERN -> modern(tools + "," + caching());
            case EARLIER -> "{" + tools + "}";
        };
    }

    /** A call of a tool, in the era its metadata names: its result says whether the operation worked. */
    private String toolsCall(Map<String, @Nullable Object> params) {
        var era = eraOf(params);
        warmUp.awaitEnd();
        var members = calls.call(params);

        return switch (era) {
            case MODERN -> modern(members);
            case EARLIER -> "{" + members + "}";
        };
    }

    /** What the server offers: tools, which never change while it runs. */
    private static String capabilities() {
        return "{\"tools\":{}}";
    }

    private static String versions() {
        return "[\"" + String.join("\",\"", SUPPORTED_VERSIONS) + "\"]";
    }

    private static String caching() {
        return "\"ttlMs\":" + TTL_MILLISECONDS + ",\"cacheScope\":\"public\"";
    }

    /** A result of the modern era: it says it is complete and who the server is, besides what it holds. */
    private static String modern(String members) {
        return "{\"resultType\":\"complete\"," + members + ",\"_meta\":{\"io.modelcontextprotocol/serverInfo\":"
                + SERVER_INFO + "}}";
    }

    /**
     * The era a request is served in, from its metadata: the version it names, which must be one the server serves,
     * and for the modern one the capabilities of the client; with no version, the earlier era once the client has
     * initialized.
     */
    private Era eraOf(Map<String, @Nullable Object> params) {
        var given = params.get("_meta");
        if (params.containsKey("_meta") && !(given instanceof Map<?, ?>)) {
            throw McpFailure.invalidParams("_meta must be an object");
        }
        var meta = given instanceof Map<?, ?> members ? members : Map.of();
        if (!meta.containsKey(PROTOCOL_VERSION_KEY)) {
            if (initialized) {
                return Era.EARLIER;
            }

            throw McpFailure.invalidParams("_meta has no " + PROTOCOL_VERSION_KEY + ", and no initialize came before");
        }
        if (!(meta.get(PROTOCOL_VERSION_KEY) instanceof String version)) {
            throw McpFailure.invalidParams(PROTOCOL_VERSION_KEY + " must be a text");
        }
        if (version.equals(EARLIER_VERSION)) {
            return Era.EARLIER;
        }
        if (!version.equals(MODERN_VERSION)) {
            throw McpFailure.unsupportedProtocolVersion(version, SUPPORTED_VERSIONS);
        }
        if (!(meta.get(CLIENT_CAPABILITIES_KEY) instanceof Map<?, ?>)) {
            throw McpFailure.invalidParams("_meta has no " + CLIENT_CAPABILITIES_KEY);
        }

        return Era.MODERN;
    }

    /**
     * One line, in UTF-8 bytes (a {@code PrintStream} would use the encoding of the platform, which can be ASCII where
     * the cli runs). The line and paragraph separators are escaped, which JSON allows only in a string, the only place
     * they can be: a reader that splits lines on them would cut the message.
     */
    private static void write(PrintStream out, String answer) {
        var line = answer.replace("\u2028", "\\u2028").replace("\u2029", "\\u2029") + "\n";
        var bytes = line.getBytes(StandardCharsets.UTF_8);
        out.write(bytes, 0, bytes.length);
        out.flush();
    }
}
