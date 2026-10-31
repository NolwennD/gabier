package fr.craft.gabier.daemon;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.jsonrpc.Endpoint;
import org.eclipse.lsp4j.jsonrpc.services.ServiceEndpoints;
import org.jspecify.annotations.Nullable;

/**
 * Proves that the daemon runs on the module path, with LSP4J as a real module and Gson able to reach into it. Gson
 * reflects on the LSP4J types: it can fail on the module path (a closed module) while passing on the class path, so the
 * check also refuses to pass on the class path. It also exercises what the jdtls client needs from the {@code opens} of
 * the module descriptor: LSP4J finding and calling the methods of {@link JdtlsClient}, and Gson filling its {@link
 * JdtlsClient.Status} and the {@code PublishDiagnosticsParams} that jdtls sends.
 *
 * @param daemonModule the name of the module of the daemon, {@code null} when it is on the class path
 * @param position the name of the module of {@link Position}, {@code null} when it is on the class path
 */
record SelfCheck(@Nullable String daemonModule, @Nullable String position) {

    private static final String LSP4J_MODULE = "org.eclipse.lsp4j";
    private static final String POSITION_JSON = "{\"line\":1,\"character\":2}";
    private static final List<String> CLIENT_METHODS =
            List.of("language/status", "language/eventNotification", "workspace/executeClientCommand");
    private static final JdtlsClient.Status STATUS = new JdtlsClient.Status("Message", "self-check");
    private static final String STATUS_JSON = "{\"type\":\"Message\",\"message\":\"self-check\"}";
    private static final String PUBLICATION_URI = "file:///self-check/A.java";
    private static final String PUBLICATION_JSON = "{\"uri\":\"" + PUBLICATION_URI + "\",\"diagnostics\":[]}";

    /**
     * What the check puts to work, as one value so that a test can replace a part with one that fails.
     *
     * @param toJson writes a value as JSON
     * @param fromJson reads a JSON text as a type
     * @param endpointOf turns a client into the endpoint LSP4J calls its methods through
     * @param methodsOf the methods of the protocol that LSP4J finds in a class
     */
    record Machinery(
            Function<Object, String> toJson,
            JsonReader fromJson,
            Function<Object, Endpoint> endpointOf,
            Function<Class<?>, Set<String>> methodsOf) {

        /** Reads a JSON text as a type. */
        @FunctionalInterface
        interface JsonReader {

            <T> T read(String json, Class<T> type);
        }

        static Machinery real() {
            var gson = new Gson();
            return new Machinery(
                    gson::toJson,
                    gson::fromJson,
                    ServiceEndpoints::toEndpoint,
                    type -> ServiceEndpoints.getSupportedMethods(type).keySet());
        }
    }

    static SelfCheck ofThisRuntime() {
        return new SelfCheck(
                SelfCheck.class.getModule().getName(),
                Position.class.getModule().getName());
    }

    static String positionAsJson() {
        return new Gson().toJson(new Position(1, 2));
    }

    List<String> problems() {
        return problems(Machinery.real());
    }

    List<String> problems(Function<Object, String> toJson) {
        var real = Machinery.real();
        return problems(new Machinery(toJson, real.fromJson(), real.endpointOf(), real.methodsOf()));
    }

    List<String> problems(Machinery machinery) {
        List<String> problems = new ArrayList<>();
        if (daemonModule == null) {
            problems.add("the daemon is not in a named module: it runs on the class path");
        }
        if (position == null) {
            problems.add("Position is not in the named module " + LSP4J_MODULE + ": it is on the class path");
        } else if (!position.equals(LSP4J_MODULE)) {
            problems.add("Position is in the module " + position + ", not in the named module " + LSP4J_MODULE);
        }
        try {
            String json = machinery.toJson().apply(new Position(1, 2));
            if (!POSITION_JSON.equals(json)) {
                problems.add("Gson wrote " + json + " instead of " + POSITION_JSON);
            }
        } catch (RuntimeException e) {
            problems.add("Gson cannot serialize a Position: " + e);
        }
        problems.addAll(clientProblems(machinery));
        return problems;
    }

    /** What the jdtls client needs: its methods found and called by LSP4J, and its messages read by Gson. */
    private static List<String> clientProblems(Machinery machinery) {
        List<String> problems = new ArrayList<>();
        try {
            Set<String> known = machinery.methodsOf().apply(JdtlsClient.class);
            CLIENT_METHODS.stream()
                    .filter(method -> !known.contains(method))
                    .forEach(method ->
                            problems.add("LSP4J does not know the method " + method + " of the jdtls client"));
        } catch (RuntimeException e) {
            problems.add("LSP4J cannot read the methods of the jdtls client: " + e);
        }
        List<String> heard = new ArrayList<>();
        try {
            var none = new ImportChoices();
            var jdtls = new JdtlsClient(heard::add, publication -> {}, registration -> {}, unregistration -> {}, none);
            machinery.endpointOf().apply(jdtls).notify("language/status", STATUS);
            if (heard.isEmpty()) {
                problems.add("The notification language/status did not reach the jdtls client");
            }
        } catch (RuntimeException e) {
            problems.add("LSP4J cannot call the jdtls client: " + e);
        }
        try {
            var status = machinery.fromJson().read(STATUS_JSON, JdtlsClient.Status.class);
            if (!STATUS.equals(status)) {
                problems.add("Gson read a language/status of jdtls as " + status);
            }
        } catch (RuntimeException e) {
            problems.add("Gson cannot read a language/status of jdtls: " + e);
        }
        try {
            var publication = machinery.fromJson().read(PUBLICATION_JSON, PublishDiagnosticsParams.class);
            if (!PUBLICATION_URI.equals(publication.getUri())) {
                problems.add("Gson read a publication of diagnostics for " + publication.getUri() + ", not for "
                        + PUBLICATION_URI);
            }
        } catch (RuntimeException e) {
            problems.add("Gson cannot read a publication of diagnostics: " + e);
        }
        return problems;
    }

    String summary() {
        return "the daemon is in " + describe(daemonModule) + ", Position in " + describe(position) + ", Gson wrote "
                + positionAsJson();
    }

    private static String describe(@Nullable String module) {
        return module == null ? "no named module" : "the module " + module;
    }
}
