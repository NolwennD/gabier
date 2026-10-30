package fr.craft.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.junit.jupiter.api.Test;

/** The statuses and the publications jdtls sends, which tell the adapter to go on or to stop. No jdtls is started. */
class JdtlsClientTest {

    private final List<String> log = new ArrayList<>();
    private final List<PublishDiagnosticsParams> published = new ArrayList<>();
    private final JdtlsClient client = new JdtlsClient(log::add, published::add);

    @Test
    void anErrorStatusFailsTheWaitWithItsMessage() {
        client.status(new JdtlsClient.Status("Error", "boom"));

        assertThat(client.error()).isCompletedWithValue("jdtls sent the status Error: boom");
        assertThat(client.serviceReady()).isNotDone();
    }

    @Test
    void theOtherStatusesDoNotFailTheWait() {
        client.status(new JdtlsClient.Status("Starting", "50% Starting Java Language Server"));
        client.status(new JdtlsClient.Status("Started", "Ready"));

        assertThat(client.error()).isNotDone();
        assertThat(client.serviceReady()).isNotDone();
    }

    @Test
    void theServiceReadyStatusReleasesTheWaitForIt() {
        client.status(new JdtlsClient.Status("ServiceReady", "ServiceReady"));

        assertThat(client.serviceReady()).isCompleted();
        assertThat(client.error()).isNotDone();
    }

    @Test
    void theStatusesAreLoggedExceptTheProgressOfTheStart() {
        client.status(new JdtlsClient.Status("Starting", "50% Starting Java Language Server"));
        client.status(new JdtlsClient.Status("Started", "Ready"));
        client.status(new JdtlsClient.Status("Error", "boom"));

        assertThat(log).containsExactly("jdtls status Started: Ready", "jdtls status Error: boom");
    }

    @Test
    void thePublishedDiagnosticsAreHandedOver() {
        var publication = new PublishDiagnosticsParams("file:///project/A.java", List.of());

        client.publishDiagnostics(publication);

        assertThat(published).containsExactly(publication);
    }

    @Test
    void theMessagesAndTheTelemetryOfJdtlsAreIgnored() {
        client.telemetryEvent("event");
        client.showMessage(new MessageParams());
        client.logMessage(new MessageParams());

        assertThat(client.showMessageRequest(new ShowMessageRequestParams())).isCompletedWithValue(null);
        assertThat(log).isEmpty();
        assertThat(published).isEmpty();
    }
}
