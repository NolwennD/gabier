package eu.gabier.daemon;

import com.google.gson.Gson;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Registration;
import org.eclipse.lsp4j.RegistrationParams;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.Unregistration;
import org.eclipse.lsp4j.UnregistrationParams;
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;
import org.eclipse.lsp4j.services.LanguageClient;

/**
 * The client side of the conversation with jdtls. It listens for {@code language/status}, a jdtls extension, to know
 * when jdtls is ready or has failed, and hands the diagnostics jdtls publishes to whoever asked for them. The methods
 * are called by the reader thread of LSP4J.
 */
final class JdtlsClient implements LanguageClient {

    /**
     * What jdtls says with {@code language/status}.
     *
     * @param type for instance {@code Starting}, {@code Started}, {@code ServiceReady} or {@code Error}
     * @param message the text that goes with it
     */
    record Status(String type, String message) {}

    /** A type jdtls offers for a name several packages declare. */
    record ImportCandidate(String fullyQualifiedName) {}

    /** The candidates jdtls offers for one use of a name; the range it also sends is not needed. */
    record ImportSelection(List<ImportCandidate> candidates) {}

    private static final String CHOOSE_IMPORTS = "java.action.organizeImports.chooseImports";

    private final CompletableFuture<Void> serviceReady = new CompletableFuture<>();

    /** Completed, with what jdtls said, as soon as it sends a status of type {@code Error}. */
    private final CompletableFuture<String> error = new CompletableFuture<>();

    private final Consumer<String> log;
    private final Consumer<PublishDiagnosticsParams> diagnostics;
    private final Consumer<Registration> registrations;
    private final Consumer<Unregistration> unregistrations;
    private final ImportChoices choices;

    /**
     * Listens to what jdtls tells the client.
     *
     * @param log receives a line for each status of jdtls worth telling
     * @param diagnostics receives every publication of diagnostics, from the first message of jdtls on
     * @param registrations receives each capability jdtls registers
     * @param unregistrations receives each capability jdtls unregisters
     * @param choices receives the types jdtls cannot choose an import for
     */
    JdtlsClient(
            Consumer<String> log,
            Consumer<PublishDiagnosticsParams> diagnostics,
            Consumer<Registration> registrations,
            Consumer<Unregistration> unregistrations,
            ImportChoices choices) {
        this.log = log;
        this.diagnostics = diagnostics;
        this.registrations = registrations;
        this.unregistrations = unregistrations;
        this.choices = choices;
    }

    CompletableFuture<Void> serviceReady() {
        return serviceReady;
    }

    CompletableFuture<String> error() {
        return error;
    }

    @JsonNotification("language/status")
    public void status(Status status) {
        // The progress of the start is a hundred lines of Starting: the other statuses tell where it got to.
        if (!"Starting".equals(status.type())) {
            log.accept("jdtls status " + status.type() + ": " + status.message());
        }
        if ("ServiceReady".equals(status.type())) {
            serviceReady.complete(null);
        }
        if ("Error".equals(status.type())) {
            error.complete("jdtls sent the status Error: " + status.message());
        }
    }

    /**
     * The events of jdtls ({@code ProjectsImported}, {@code ClasspathUpdated}, ...). Nothing here needs them, since
     * {@code ServiceReady} says the import is over, but a method that LSP4J does not know is logged by LSP4J as a
     * warning on the standard error for every event: declaring it is what keeps that warning out.
     */
    @JsonNotification("language/eventNotification")
    public void eventNotification(Object event) {}

    /**
     * A command jdtls asks the client to run ({@code workspace/executeClientCommand}, which a client opts into with
     * {@code executeClientCommandSupport}). The only one gabier answers is {@code
     * java.action.organizeImports.chooseImports}, which jdtls sends, with {@code advancedOrganizeImportsSupport}, for a
     * type that several packages declare and that the file uses without importing: its arguments are the document, the
     * candidates of each such use and whether the existing imports are restored. gabier does not choose for the agent: it
     * answers that nothing is chosen, and keeps the candidates (see {@link ImportChoices}) to refuse with them.
     */
    @JsonRequest("workspace/executeClientCommand")
    public CompletableFuture<Object> executeClientCommand(ExecuteCommandParams command) {
        if (CHOOSE_IMPORTS.equals(command.getCommand())) {
            var gson = new Gson();
            var selections =
                    gson.fromJson(gson.toJsonTree(command.getArguments().get(1)), ImportSelection[].class);
            for (ImportSelection selection : selections) {
                choices.offer(selection.candidates().stream()
                        .map(ImportCandidate::fullyQualifiedName)
                        .toList());
            }
        }

        return CompletableFuture.completedFuture(List.of());
    }

    @Override
    public void publishDiagnostics(PublishDiagnosticsParams publication) {
        diagnostics.accept(publication);
    }

    /** jdtls registers the files it wants watched once it is ready, see {@link WatchedFiles}. */
    @Override
    public CompletableFuture<Void> registerCapability(RegistrationParams params) {
        params.getRegistrations().forEach(registrations);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> unregisterCapability(UnregistrationParams params) {
        params.getUnregisterations().forEach(unregistrations);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void telemetryEvent(Object object) {}

    @Override
    public void showMessage(MessageParams messageParams) {}

    @Override
    public CompletableFuture<MessageActionItem> showMessageRequest(ShowMessageRequestParams requestParams) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void logMessage(MessageParams message) {}
}
