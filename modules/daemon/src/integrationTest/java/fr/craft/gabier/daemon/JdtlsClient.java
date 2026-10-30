package fr.craft.gabier.daemon;

import java.util.concurrent.CompletableFuture;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification;
import org.eclipse.lsp4j.services.LanguageClient;

/**
 * The client side of the conversation with jdtls. It listens for {@code language/status}, a jdtls extension, and keeps
 * the diagnostics jdtls publishes.
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

    private final Diagnostics diagnostics = new Diagnostics();

    /** What jdtls published about the files, delivered by the reader thread of LSP4J. */
    Diagnostics diagnostics() {
        return diagnostics;
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
            System.out.println("jdtls status " + status.type() + ": " + status.message());
        }
        if ("ServiceReady".equals(status.type())) {
            serviceReady.complete(null);
        }
        if ("Error".equals(status.type())) {
            error.complete("jdtls sent the status Error: " + status.message());
        }
    }

    @Override
    public void telemetryEvent(Object object) {}

    @Override
    public void publishDiagnostics(PublishDiagnosticsParams publication) {
        diagnostics.publish(publication);
        // The evidence of the probe: every publication, with what it holds.
        System.out.println("jdtls published " + publication.getDiagnostics().size() + " diagnostics for "
                + publication.getUri() + ": "
                + publication.getDiagnostics().stream()
                        .map(d -> d.getSeverity() + " line "
                                + (d.getRange().getStart().getLine() + 1) + " " + Diagnostics.text(d))
                        .toList());
    }

    @Override
    public void showMessage(MessageParams messageParams) {}

    @Override
    public CompletableFuture<MessageActionItem> showMessageRequest(ShowMessageRequestParams requestParams) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void logMessage(MessageParams message) {}
}
