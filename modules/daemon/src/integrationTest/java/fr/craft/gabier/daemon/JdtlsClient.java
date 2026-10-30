package fr.craft.gabier.daemon;

import java.util.concurrent.CompletableFuture;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification;
import org.eclipse.lsp4j.services.LanguageClient;

/** The client side of the conversation with jdtls. It only listens for {@code language/status}, a jdtls extension. */
final class JdtlsClient implements LanguageClient {

    /**
     * What jdtls says with {@code language/status}.
     *
     * @param type for instance {@code Starting}, {@code Started}, {@code ServiceReady} or {@code Error}
     * @param message the text that goes with it
     */
    record Status(String type, String message) {}

    private final CompletableFuture<Void> serviceReady = new CompletableFuture<>();

    CompletableFuture<Void> serviceReady() {
        return serviceReady;
    }

    @JsonNotification("language/status")
    public void status(Status status) {
        // The progress of the start is a hundred lines of Starting: the other statuses tell where it got to.
        if (!status.type().equals("Starting")) {
            System.out.println("jdtls status " + status.type() + ": " + status.message());
        }
        if (status.type().equals("ServiceReady")) {
            serviceReady.complete(null);
        }
    }

    @Override
    public void telemetryEvent(Object object) {}

    @Override
    public void publishDiagnostics(PublishDiagnosticsParams diagnostics) {}

    @Override
    public void showMessage(MessageParams messageParams) {}

    @Override
    public CompletableFuture<MessageActionItem> showMessageRequest(ShowMessageRequestParams requestParams) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void logMessage(MessageParams message) {}
}
