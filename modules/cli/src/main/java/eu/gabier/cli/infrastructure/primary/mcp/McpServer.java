package eu.gabier.cli.infrastructure.primary.mcp;

import eu.gabier.cli.application.ProjectOperations;
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
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Supplier;
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
 * <p>The server reads the client on one thread and answers on it what needs no daemon, in the order the messages come
 * (the handshake, {@code ping}, the list of the tools, the guide, a call whose values are wrong). A call that asks the
 * daemon runs in a thread of its own ({@link Calls}), so that the server goes on reading while it waits for the start
 * of the daemon, the import of the project by jdtls or a slow answer: its answer is written when it ends, which can be
 * after the answer to a message that came later (the id tells them apart). Calls are not ordered among themselves: a
 * host that needs one to end before the next asks for it waits for its answer. At the end of the input the server waits
 * for the calls that have not ended, so that what was asked is answered. A call that has waited too long is answered
 * as a failed call, and a call that carries a {@code progressToken} is told, every few seconds, where jdtls is. A
 * {@code notifications/cancelled} ends the call it names with no answer.
 *
 * <p>No SDK and no library: the cli is a native image. The state is whether {@code initialize} has come, which only the
 * thread that reads the client touches, and the calls that run.
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

    /** Where a call that asks the daemon runs: a thread of its own, which does not keep the program alive. */
    private static final Executor CALL_THREADS =
            task -> Thread.ofPlatform().daemon().name("gabier-mcp-call").start(task);

    private static final String SERVER_INFO =
            "{\"name\":\"gabier\",\"version\":" + Json.quote(ProtocolVersion.CURRENT.value()) + "}";

    private final Tools tools = Tools.served();
    private final String toolsJson = tools.json();
    private final ToolCalls calls;
    private final String instructions;
    private final WarmUp warmUp;
    private final Pacing pacing;
    private boolean initialized;

    /**
     * What the tools lean on: the application, which runs an operation and answers a question to the guide.
     *
     * @param operations how an operation is run, in the project of the files it names
     * @param commandOf the word of the command line that runs an operation, for the pointer to its help
     * @param guide the guide that the tool {@code guide} is asked
     */
    public record Backend(ProjectOperations operations, Function<Operation, String> commandOf, Guide guide) {}

    /**
     * A server of the operations of the daemon, and of the guide.
     *
     * @param backend what the tools run
     * @param err where the server logs what it must not say on its output, such as a call that failed unexpectedly
     * @param instructions what the host is told about using the tools, in its handshake, as one text
     * @param warmUp what starts the daemon at the first handshake, so that it is ready when the first call comes
     */
    public McpServer(Backend backend, PrintStream err, String instructions, WarmUp warmUp) {
        this(backend, err, instructions, warmUp, Pacing.real());
    }

    McpServer(Backend backend, PrintStream err, String instructions, WarmUp warmUp, Pacing pacing) {
        calls = new ToolCalls(tools, backend, err);
        this.instructions = instructions;
        this.warmUp = warmUp;
        this.pacing = pacing;
    }

    /** The era a request is served in. */
    private enum Era {
        MODERN,
        EARLIER
    }

    /**
     * Serves the client until it closes its output (the end of the input): reads a line, answers it when it is a
     * request, and goes on, whatever the line holds. Then waits for the calls that have not ended.
     *
     * @param in what the client writes, read as UTF-8
     * @param out where the answers go, one line each, written as UTF-8 whatever the encoding of the stream
     */
    public void serve(InputStream in, PrintStream out) {
        var output = new Output(out);
        var running = new Calls(output, pacing, CALL_THREADS);
        new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                .lines()
                .filter(line -> !line.isBlank())
                .forEach(line -> read(line, output, running));
        running.drain();
    }

    private void read(String line, Output output, Calls running) {
        switch (Incoming.of(line)) {
            case Incoming.Silent _ -> {}
            case Incoming.Notification notification -> notified(notification, running);
            case Incoming.Refused refused -> output.write(refused.failure().toJson(refused.id()));
            case Incoming.Request request -> answer(request, output, running);
        }
    }

    /** The one notification the server reads: the cancellation of a call, which ends it with no answer. */
    private static void notified(Incoming.Notification notification, Calls running) {
        if (notification.method().equals("notifications/cancelled")) {
            Incoming.keyOf(notification.params().get("requestId")).ifPresent(running::cancel);
        }
    }

    private void answer(Incoming.Request request, Output output, Calls running) {
        try {
            if (request.method().equals("tools/call")) {
                call(request, output, running);

                return;
            }
            output.write("{\"jsonrpc\":\"2.0\",\"id\":" + request.id() + ",\"result\":" + result(request) + "}");
        } catch (McpFailure failure) {
            output.write(failure.toJson(request.id()));
        }
    }

    private String result(Incoming.Request request) {
        return switch (request.method()) {
            case "initialize" -> initialize(request.params());
            case "ping" -> ping(request.params());
            case "server/discover" -> discover(request.params());
            case "tools/list" -> toolsList(request.params());
            default -> throw McpFailure.methodNotFound();
        };
    }

    /**
     * {@code ping}, which revision 2026-07-28 removed and the hosts still send: it is answered whatever the era, even
     * before {@code initialize}. A request that names the modern era in its metadata gets what every result of that era
     * holds ({@code resultType}), the others the empty result.
     */
    private static String ping(Map<String, @Nullable Object> params) {
        var modern =
                params.get("_meta") instanceof Map<?, ?> meta && MODERN_VERSION.equals(meta.get(PROTOCOL_VERSION_KEY));

        return modern ? modern("") : "{}";
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

    /**
     * A call of a tool, in the era its metadata names: its result says whether the operation worked. Everything that
     * needs no daemon is decided here, on the thread that reads the client, and answered at once; what asks the daemon
     * runs in a thread of its own, after the warm-up that may be starting it when it is for the project the server was
     * launched in (a call that does not ask the daemon, {@code status}, or one for another project, does not wait for
     * it).
     */
    private void call(Incoming.Request request, Output output, Calls running) {
        var params = request.params();
        var era = eraOf(params);
        Function<String, String> answer = members -> "{\"jsonrpc\":\"2.0\",\"id\":" + request.id() + ",\"result\":"
                + (era == Era.MODERN ? modern(members) : "{" + members + "}") + "}";
        switch (calls.prepare(params)) {
            case ToolCalls.Done done -> output.write(answer.apply(done.members()));
            case ToolCalls.Waiting waiting -> {
                Supplier<String> work = () -> {
                    if (calls.awaitsTheWarmUp(waiting)) {
                        warmUp.awaitEnd();
                    }

                    return calls.complete(waiting);
                };
                var job = new Calls.Job(
                        request.id(),
                        request.key(),
                        progressTokenOf(params),
                        answer,
                        work,
                        () -> calls.status(waiting),
                        () -> calls.timedOut(pacing.bound()));
                if (!running.start(job)) {
                    throw McpFailure.invalidRequest("the id is the one of a request that has not been answered yet");
                }
            }
        }
    }

    /** The token that asks to be told how the call goes, as JSON, none when the request carries none. */
    private static @Nullable String progressTokenOf(Map<String, @Nullable Object> params) {
        return params.get("_meta") instanceof Map<?, ?> meta ? Incoming.jsonIdOf(meta.get("progressToken")) : null;
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
        return "{\"resultType\":\"complete\"," + (members.isEmpty() ? "" : members + ",")
                + "\"_meta\":{\"io.modelcontextprotocol/serverInfo\":" + SERVER_INFO + "}}";
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
}
