package eu.gabier.cli.infrastructure.primary;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class StartingTheDaemonTest extends CliTest {

    private Path bin() {
        return directory.resolve("bin");
    }

    private Path log() {
        return directory.resolve("gabier.sock.log");
    }

    private Map<String, String> environment() {
        return Map.of("GABIER_SOCKET", socket().toString(), "PATH", bin().toString());
    }

    @AfterEach
    void endTheDaemon() throws IOException {
        FakeDaemonScripts.end(log());
    }

    @Test
    void diagnosticsStartsTheDaemonOfThePathAndPrintsTheAnswer() throws IOException {
        FakeDaemonScripts.installListening(bin());

        assertThat(run(environment(), "diagnostics", "/project/Main.java")).isZero();

        assertThat(out()).isEmpty();
        assertThat(err()).isEmpty();
        assertThat(Files.readString(log())).startsWith("listening ");
    }

    @Test
    void renameStartsTheDaemonOfThePathAndPrintsTheAnswer() throws IOException {
        FakeDaemonScripts.installListening(bin());

        assertThat(run(environment(), "rename", "--dry-run", "/project/Greeter.java", "6:18", "welcome"))
                .isZero();

        assertThat(out()).isEqualTo("dry run, nothing written" + System.lineSeparator());
        assertThat(Files.readString(log())).startsWith("listening ");
    }

    @Test
    void aSecondCommandUsesTheDaemonThatIsRunningInsteadOfStartingAnother() throws IOException {
        FakeDaemonScripts.installListening(bin());

        assertThat(run(environment(), "diagnostics", "/project/Main.java")).isZero();
        assertThat(run(environment(), "diagnostics", "/project/Other.java")).isZero();

        assertThat(Files.readAllLines(log())).hasSize(1);
    }

    @Test
    void theDaemonServesTheProjectOfTheEnvironmentAndTheCurrentFolder() throws IOException {
        FakeDaemonScripts.installListening(bin());
        var environment = new HashMap<>(environment());
        environment.put("GABIER_PROJECT", "/work/demo");

        assertThat(run(environment, "diagnostics", "/project/Main.java")).isZero();

        assertThat(Files.readString(log()))
                .contains(" project=/work/demo ")
                .contains("cwd=" + Path.of("").toAbsolutePath().toRealPath() + " ");
    }

    @Test
    void aDaemonThatCannotBeFoundIsAProblemObjectWithExitCodeOneAndNothingOnTheStandardOutput() throws IOException {
        Files.createDirectories(bin());

        assertThat(run(environment(), "diagnostics", "/project/Main.java")).isEqualTo(1);

        assertThat(out()).isEmpty();
        assertThat(err())
                .startsWith("Daemon start failed: The daemon did not start: gabier-daemon was not found")
                .contains("(daemon-start-failed; socket=" + socket() + ")");
    }

    @Test
    void aDaemonThatDoesNotStartIsAProblemObjectAsJsonWithTheJsonOption() throws IOException {
        FakeDaemonScripts.install(bin(), "echo no good; exit 4");

        assertThat(run(environment(), "--json", "rename", "/project/Greeter.java", "6:18", "welcome"))
                .isEqualTo(1);

        assertThat(err())
                .startsWith("{\"type\":\"urn:gabier:error:daemon-start-failed\",")
                .contains("\"exitCode\":\"4\"")
                .contains("\"log\":\"" + log() + "\"");
        assertThat(Files.readString(log())).isEqualTo("no good\n");
    }

    @Test
    void aUsageErrorStartsNoDaemon() throws IOException {
        var marker = directory.resolve("started");
        FakeDaemonScripts.install(bin(), "echo started > '" + marker + "'");

        assertThat(run(environment(), "rename", "/project/Greeter.java")).isEqualTo(2);
        assertThat(run(environment(), "diagnostics")).isEqualTo(2);

        assertThat(marker).doesNotExist();
    }
}
