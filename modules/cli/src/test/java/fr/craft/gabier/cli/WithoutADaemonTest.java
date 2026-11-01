package fr.craft.gabier.cli;

import static org.assertj.core.api.Assertions.assertThat;

import fr.craft.gabier.protocol.JsonRpc;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WithoutADaemonTest extends CliTest {

    /** The run must come back: a client that waits for a daemon that is not there hangs the shell of the user. */
    private int runWithoutHanging(Map<String, String> environment, String... args) throws Exception {
        return CompletableFuture.supplyAsync(() -> run(environment, args)).get(10, TimeUnit.SECONDS);
    }

    private int runWithoutHanging(String... args) throws Exception {
        return runWithoutHanging(Map.of("GABIER_SOCKET", socket().toString()), args);
    }

    @ParameterizedTest
    @ValueSource(strings = {"status", "stop"})
    void saysNoDaemonIsRunningAndExitsWithTheCodeOfARefusal(String command) throws Exception {
        assertThat(runWithoutHanging(command)).isEqualTo(2);

        assertThat(out()).isEmpty();
        assertThat(err())
                .isEqualTo("Daemon not running: No daemon answers on " + socket()
                        + ": start it with gabier-daemon (daemon-not-running; socket=" + socket() + ")"
                        + System.lineSeparator());
    }

    @Test
    void statusAndStopStartNoDaemonEvenWhenOneIsOnThePath() throws Exception {
        var marker = directory.resolve("started");
        FakeDaemonScripts.install(directory.resolve("bin"), "echo started > '" + marker + "'");
        var environment = Map.of(
                "GABIER_SOCKET",
                socket().toString(),
                "PATH",
                directory.resolve("bin").toString());

        assertThat(runWithoutHanging(environment, "status")).isEqualTo(2);
        assertThat(runWithoutHanging(environment, "stop")).isEqualTo(2);

        assertThat(err()).contains("(daemon-not-running; ");
        assertThat(marker).doesNotExist();
    }

    @Test
    void saysItAsJsonWithTheJsonOption() throws Exception {
        assertThat(runWithoutHanging("--json", "status")).isEqualTo(2);

        assertThat(err())
                .startsWith("{\"type\":\"urn:gabier:error:daemon-not-running\",")
                .contains("\"socket\":\"" + socket() + "\"");
    }

    @Test
    void aSocketFileThatNobodyAnswersOnIsNoDaemonEither() throws Exception {
        ServerSocketChannel.open(StandardProtocolFamily.UNIX)
                .bind(UnixDomainSocketAddress.of(socket()))
                .close();
        assertThat(socket()).exists();

        assertThat(runWithoutHanging("status")).isEqualTo(2);

        assertThat(err()).contains("(daemon-not-running; ");
    }

    @Test
    void refusesAnOverrideThatIsTooLongWithoutConnecting() throws Exception {
        var tooLong = "/" + "a".repeat(119);

        assertThat(runWithoutHanging(Map.of("GABIER_SOCKET", tooLong), "status"))
                .isEqualTo(2);

        assertThat(err())
                .contains("(socket-path-too-long; ")
                .contains("length=120")
                .contains("limit=107")
                .contains("override=GABIER_SOCKET");
    }

    @Test
    void looksForTheSocketInTheRuntimeDirectoryOfTheUserWithoutAnOverride() throws Exception {
        var runtimeDirectory = directory.resolve("run");
        Files.createDirectories(runtimeDirectory.resolve("gabier"));
        var expected = runtimeDirectory.resolve("gabier/gabier.sock");
        var status = JsonRpc.success("1", """
            {"version":"0.1","pid":1,"uptimeMillis":0,"project":"/p","jdtls":"not-started"}\
            """);
        try (var daemon = new ScriptedDaemon(expected, status)) {
            var code = run(Map.of("XDG_RUNTIME_DIR", runtimeDirectory.toString()), "status");

            assertThat(code).isZero();
            assertThat(daemon.requests()).hasSize(1);
        }
    }
}
