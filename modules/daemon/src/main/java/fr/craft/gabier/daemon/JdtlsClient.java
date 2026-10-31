package fr.craft.gabier.daemon;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Registration;
import org.eclipse.lsp4j.RegistrationParams;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.Unregistration;
import org.eclipse.lsp4j.UnregistrationParams;
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification;
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

    private final CompletableFuture<Void> serviceReady = new CompletableFuture<>();

    /** Completed, with what jdtls said, as soon as it sends a status of type {@code Error}. */
    private final CompletableFuture<String> error = new CompletableFuture<>();

    private final Consumer<String> log;
    private final Consumer<PublishDiagnosticsParams> diagnostics;
    private final Consumer<Registration> registrations;
    private final Consumer<Unregistration> unregistrations;

    /**
     * Listens to what jdtls tells the client.
     *
     * @param log receives a line for each status of jdtls worth telling
     * @param diagnostics receives every publication of diagnostics, from the first message of jdtls on
     * @param registrations receives each capability jdtls registers
     * @param unregistrations receives each capability jdtls unregisters
     */
    JdtlsClient(
            Consumer<String> log,
            Consumer<PublishDiagnosticsParams> diagnostics,
            Consumer<Registration> registrations,
            Consumer<Unregistration> unregistrations) {
        this.log = log;
        this.diagnostics = diagnostics;
        this.registrations = registrations;
        this.unregistrations = unregistrations;
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
