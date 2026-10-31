package fr.craft.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Registration;
import org.eclipse.lsp4j.RegistrationParams;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.Unregistration;
import org.eclipse.lsp4j.UnregistrationParams;
import org.eclipse.lsp4j.jsonrpc.services.ServiceEndpoints;
import org.junit.jupiter.api.Test;

/** The statuses and the publications jdtls sends, which tell the adapter to go on or to stop. No jdtls is started. */
class JdtlsClientTest {

    private final List<String> log = new ArrayList<>();
    private final List<PublishDiagnosticsParams> published = new ArrayList<>();
    private final List<Registration> registered = new ArrayList<>();
    private final List<Unregistration> unregistered = new ArrayList<>();
    private final ImportChoices choices = new ImportChoices();
    private final JdtlsClient client =
            new JdtlsClient(log::add, published::add, registered::add, unregistered::add, choices);

    @Test
    void handsEachRegistrationOfJdtlsOverAndAnswersIt() {
        var first = new Registration("1", "workspace/didChangeWatchedFiles", "options");
        var second = new Registration("2", "workspace/symbol", "options");

        var answer = client.registerCapability(new RegistrationParams(List.of(first, second)));

        assertThat(registered).containsExactly(first, second);
        assertThat(answer).isCompleted();
    }

    @Test
    void handsEachUnregistrationOfJdtlsOverAndAnswersIt() {
        var unregistration = new Unregistration("1", "workspace/didChangeWatchedFiles");

        var answer = client.unregisterCapability(new UnregistrationParams(List.of(unregistration)));

        assertThat(unregistered).containsExactly(unregistration);
        assertThat(answer).isCompleted();
    }

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
    void theEventsOfJdtlsAreKnownToLsp4jAndIgnored() {
        // Without the method, LSP4J logs a warning for every event jdtls sends: ProjectsImported, ClasspathUpdated...
        assertThat(ServiceEndpoints.getSupportedMethods(JdtlsClient.class))
                .containsKeys("language/status", "language/eventNotification", "workspace/executeClientCommand");

        client.eventNotification(Map.of("eventType", 200.0, "data", List.of("file:///project/")));

        assertThat(log).isEmpty();
        assertThat(client.serviceReady()).isNotDone();
        assertThat(client.error()).isNotDone();
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

    private static ExecuteCommandParams chooseImports(String selections) {
        return new ExecuteCommandParams(
                "java.action.organizeImports.chooseImports",
                List.of("file:///project/A.java", JsonParser.parseString(selections), false));
    }

    @Test
    void keepsTheCandidatesJdtlsAsksTheClientToChooseBetweenAndChoosesNone() {
        var answer = client.executeClientCommand(chooseImports("""
            [{"candidates": [{"fullyQualifiedName": "java.sql.Date", "id": "java.sql.Date@1"},
                             {"fullyQualifiedName": "java.util.Date", "id": "java.util.Date@2"}],
              "range": {"start": {"line": 6, "character": 4}, "end": {"line": 6, "character": 8}}},
             {"candidates": [{"fullyQualifiedName": "java.awt.List", "id": "java.awt.List@3"},
                             {"fullyQualifiedName": "java.util.List", "id": "java.util.List@4"}],
              "range": {"start": {"line": 7, "character": 4}, "end": {"line": 7, "character": 8}}}]
            """));

        assertThat(choices.offered())
                .containsExactly("java.sql.Date", "java.util.Date", "java.awt.List", "java.util.List");
        assertThat(answer).isCompletedWithValue(List.of());
    }

    @Test
    void ignoresTheOtherCommandsOfJdtlsAndChoosesNothingForThem() {
        var answer = client.executeClientCommand(new ExecuteCommandParams("java.other", List.of("x")));

        assertThat(choices.offered()).isEmpty();
        assertThat(answer).isCompletedWithValue(List.of());
    }
}
