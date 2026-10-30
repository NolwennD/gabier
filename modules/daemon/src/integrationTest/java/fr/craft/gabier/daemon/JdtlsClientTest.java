package fr.craft.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.junit.jupiter.api.Test;

/** The statuses jdtls sends, which tell the probe to go on or to stop. No jdtls is started. */
class JdtlsClientTest {

    private final JdtlsClient client = new JdtlsClient();

    @Test
    void anErrorStatusFailsTheProbeWithItsMessage() {
        client.status(new JdtlsClient.Status("Error", "boom"));

        assertThat(client.error()).isCompletedWithValueMatching(message -> message.contains("Error: boom"));
        assertThat(client.serviceReady()).isNotDone();
    }

    @Test
    void theOtherStatusesDoNotFailTheProbe() {
        client.status(new JdtlsClient.Status("Starting", "50% Starting Java Language Server"));
        client.status(new JdtlsClient.Status("Started", "Ready"));

        assertThat(client.error()).isNotDone();
        assertThat(client.serviceReady()).isNotDone();
    }

    @Test
    void thePublishedDiagnosticsAreKeptForTheProbe() {
        Path file = Path.of("/project/modules/a/src/main/java/A.java");
        var publication = client.diagnostics().expect(file);

        client.publishDiagnostics(new PublishDiagnosticsParams(file.toUri().toString(), List.of()));

        assertThat(publication).isCompletedWithValue(List.of());
        assertThat(client.diagnostics().publications(file)).isEqualTo(1);
    }

    @Test
    void theServiceReadyStatusReleasesTheWaitForIt() {
        client.status(new JdtlsClient.Status("ServiceReady", "ServiceReady"));

        assertThat(client.serviceReady()).isCompleted();
        assertThat(client.error()).isNotDone();
    }
}
