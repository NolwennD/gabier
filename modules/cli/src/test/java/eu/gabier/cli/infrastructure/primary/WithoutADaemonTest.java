package eu.gabier.cli.infrastructure.primary;

import static org.assertj.core.api.Assertions.assertThat;

import eu.gabier.protocol.JsonRpc;
import eu.gabier.protocol.SocketLocation;
import eu.gabier.protocol.UserId;
import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WithoutADaemonTest extends CliTest {

    private static final String STATUS = JsonRpc.success("1", """
        {"version":"0.1","pid":1,"uptimeMillis":0,"project":"/p","jdtls":"not-started","imports":%s}\
        """.formatted(CliFixtures.IMPORTS));

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

    /** A project the way Git makes one in a worktree: a folder with a {@code .git} file. */
    private Path worktree(String name) throws IOException {
        var root = Files.createDirectories(directory.resolve(name)).toRealPath();
        Files.createFile(root.resolve(".git"));

        return root;
    }

    private Map<String, String> inTheRuntimeDirectoryAndInTheProject(Path project) throws IOException {
        var runtimeDirectory = Files.createDirectories(directory.resolve("run"));

        return Map.of("XDG_RUNTIME_DIR", runtimeDirectory.toString(), "GABIER_PROJECT", project.toString());
    }

    @Test
    void looksForTheSocketOfTheProjectInTheRuntimeDirectoryOfTheUserWithoutAnOverride() throws Exception {
        var runtimeDirectory = directory.resolve("run");
        var environment = Map.of("XDG_RUNTIME_DIR", runtimeDirectory.toString());
        var expected = SocketLocation.resolve(
                environment, UserId.current(), Path.of("").toAbsolutePath());
        Files.createDirectories(expected.getParent());
        try (var daemon = new ScriptedDaemon(expected, STATUS)) {
            var code = run(environment, "status");

            assertThat(code).isZero();
            assertThat(daemon.requests()).hasSize(1);
            assertThat(expected.getParent()).isEqualTo(runtimeDirectory.resolve("gabier"));
        }
    }

    @Test
    void reachesTheDaemonOfTheProjectTheVariableNamesWhateverTheFolderItIsRunIn() throws Exception {
        var project = worktree("worktree");
        var environment = inTheRuntimeDirectoryAndInTheProject(project);
        var socket = SocketLocation.resolve(environment, UserId.current(), directory);
        Files.createDirectories(socket.getParent());
        try (var daemon = new ScriptedDaemon(socket, STATUS)) {
            assertThat(run(environment, "status")).isZero();

            assertThat(daemon.requests()).hasSize(1);
        }
    }

    @Test
    void doesNotReachTheDaemonOfAnotherProjectAndStartsNone() throws Exception {
        var running = worktree("running");
        var asked = worktree("asked");
        var socket = SocketLocation.resolve(inTheRuntimeDirectoryAndInTheProject(running), UserId.current(), directory);
        Files.createDirectories(socket.getParent());
        try (var daemon = new ScriptedDaemon(socket, STATUS)) {
            assertThat(runWithoutHanging(inTheRuntimeDirectoryAndInTheProject(asked), "status"))
                    .isEqualTo(2);

            assertThat(daemon.requests()).isEmpty();
            assertThat(err()).contains("(daemon-not-running; ");
        }
    }
}
