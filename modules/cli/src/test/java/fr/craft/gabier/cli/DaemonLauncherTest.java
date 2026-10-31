package fr.craft.gabier.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import fr.craft.gabier.protocol.JsonRpc;
import fr.craft.gabier.protocol.RpcResponse;
import fr.craft.gabier.shared.error.domain.ErrorStatus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The cli starting {@code gabier-daemon} when nothing answers: real processes, shell scripts standing for the daemon. */
class DaemonLauncherTest {

    private static final Duration SHORT = Duration.ofMillis(600);
    private static final Duration LONG = Duration.ofSeconds(20);

    /** The scripts run with the {@code PATH} of the test, which holds their own folder only. */
    private static final String SYSTEM_PATH = "PATH=/usr/bin:/bin\n";

    @TempDir
    Path directory;

    private Path socket() {
        return directory.resolve("gabier.sock");
    }

    private Path log() {
        return directory.resolve("gabier.sock.log");
    }

    private Path bin() {
        return directory.resolve("bin");
    }

    private Map<String, String> environment() {
        return Map.of("GABIER_SOCKET", socket().toString(), "PATH", bin().toString());
    }

    private DaemonLauncher launcher(Duration delay) {
        return new DaemonLauncher(environment(), directory, null, delay);
    }

    private DaemonException failure(Duration delay) {
        return catchThrowableOfType(DaemonException.class, () -> launcher(delay).connect(socket()));
    }

    /** The process is gone, or about to be: the kill is a signal, and the system reaps the process a moment later. */
    private static void assertGone(long pid) throws InterruptedException {
        var deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (ProcessHandle.of(pid).filter(ProcessHandle::isAlive).isPresent() && System.nanoTime() < deadline) {
            Thread.sleep(20);
        }

        assertThat(ProcessHandle.of(pid).filter(ProcessHandle::isAlive)).isEmpty();
    }

    @AfterEach
    void endTheDaemon() throws IOException {
        FakeDaemonScripts.end(log());
    }

    @Test
    void startsTheDaemonFoundOnThePathAndWaitsUntilItAnswers() throws IOException {
        FakeDaemonScripts.installListening(bin());

        try (var client = launcher(LONG).connect(socket())) {
            var response = client.call("diagnostics");

            assertThat(response).isInstanceOf(RpcResponse.Success.class);
        }
        assertThat(Files.readString(log())).startsWith("listening ");
    }

    @Test
    void startsTheDaemonInTheCurrentFolderOfTheCliSoThatItServesThatProject() throws IOException {
        FakeDaemonScripts.installListening(bin());
        var project = Files.createDirectory(directory.resolve("project"));

        try (var client = new DaemonLauncher(environment(), project, null, LONG).connect(socket())) {
            client.call("diagnostics");
        }

        assertThat(Files.readString(log())).contains("cwd=" + project.toRealPath() + " project=null ");
    }

    @Test
    void givesTheDaemonTheEnvironmentOfTheCliSoThatTheProjectVariableReachesIt() throws IOException {
        FakeDaemonScripts.installListening(bin());
        var environment = new HashMap<>(environment());
        environment.put("GABIER_PROJECT", "/work/demo");

        try (var client = new DaemonLauncher(environment, directory, null, LONG).connect(socket())) {
            client.call("diagnostics");
        }

        assertThat(Files.readString(log())).contains(" project=/work/demo ");
    }

    @Test
    void startsTheDaemonNextToTheBinaryOfTheCliBeforeLookingOnThePath() throws IOException {
        FakeDaemonScripts.install(bin(), "echo wrong daemon; exit 9");
        var beside = directory.resolve("beside");
        FakeDaemonScripts.installListening(beside);

        try (var client =
                new DaemonLauncher(environment(), directory, beside.resolve("gabier"), LONG).connect(socket())) {
            client.call("diagnostics");
        }

        assertThat(Files.readString(log())).startsWith("listening ");
    }

    @Test
    void looksOnThePathWhenNothingIsNextToTheBinaryOfTheCli() throws IOException {
        FakeDaemonScripts.installListening(bin());

        try (var client =
                new DaemonLauncher(environment(), directory, directory.resolve("gabier"), LONG).connect(socket())) {
            client.call("diagnostics");
        }

        assertThat(Files.readString(log())).startsWith("listening ");
    }

    @Test
    void looksOnThePathWhenTheBinaryOfTheCliHasNoFolder() throws IOException {
        FakeDaemonScripts.installListening(bin());

        try (var client = new DaemonLauncher(environment(), directory, Path.of("/"), LONG).connect(socket())) {
            client.call("diagnostics");
        }

        assertThat(Files.readString(log())).startsWith("listening ");
    }

    @Test
    void usesTheDaemonThatAnswersAndStartsNothing() throws IOException {
        var marker = directory.resolve("started");
        FakeDaemonScripts.install(bin(), "echo started > '" + marker + "'");

        try (var daemon = new ScriptedDaemon(socket(), JsonRpc.success("1", "{}"));
                var client = launcher(LONG).connect(socket())) {
            assertThat(client.call("diagnostics")).isInstanceOf(RpcResponse.Success.class);
            assertThat(daemon.requests()).hasSize(1);
        }

        assertThat(marker).doesNotExist();
        assertThat(log()).doesNotExist();
    }

    @Test
    void startsTheDaemonOnceForTwoRequests() throws IOException {
        FakeDaemonScripts.installListening(bin());
        var launcher = launcher(LONG);

        try (var first = launcher.connect(socket())) {
            first.call("diagnostics");
        }
        try (var second = launcher.connect(socket())) {
            second.call("diagnostics");
        }

        assertThat(Files.readAllLines(log())).hasSize(1);
    }

    @Test
    void createsAFolderForTheSocketThatIsPrivateToTheUser() throws IOException {
        FakeDaemonScripts.installListening(bin());
        var folder = directory.resolve("run").resolve("gabier");
        var socket = folder.resolve("gabier.sock");
        var environment = Map.of("GABIER_SOCKET", socket.toString(), "PATH", bin().toString());

        try (var client = new DaemonLauncher(environment, directory, null, LONG).connect(socket)) {
            client.call("diagnostics");
        } finally {
            FakeDaemonScripts.end(folder.resolve("gabier.sock.log"));
        }

        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(folder)))
                .isEqualTo("rwx------");
        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(folder.resolve("gabier.sock.log"))))
                .isEqualTo("rw-------");
    }

    @Test
    void reportsADaemonThatIsNotFoundAndWhereItLooked() throws IOException {
        Files.createDirectories(bin());

        var failure = failure(LONG);

        assertThat(failure.key().get()).isEqualTo("daemon-start-failed");
        assertThat(failure.status()).isEqualTo(ErrorStatus.INTERNAL_SERVER_ERROR);
        assertThat(failure.getMessage())
                .contains("gabier-daemon was not found")
                .contains("next to the binary of gabier or in the PATH")
                .contains(bin().toString());
        assertThat(failure.parameters()).containsEntry("socket", socket().toString());
        assertThat(log()).doesNotExist();
    }

    @Test
    void reportsADaemonThatIsNotFoundWhenThereIsNoPathAtAll() {
        var failure = catchThrowableOfType(
                DaemonException.class,
                () -> new DaemonLauncher(Map.of("GABIER_SOCKET", socket().toString()), directory, null, LONG)
                        .connect(socket()));

        assertThat(failure.key().get()).isEqualTo("daemon-start-failed");
        assertThat(failure.getMessage()).contains("gabier-daemon was not found");
    }

    @Test
    void doesNotTakeAFolderOrAFileThatIsNotExecutableForTheDaemon() throws IOException {
        Files.createDirectories(bin().resolve("gabier-daemon"));
        var other = Files.createDirectories(directory.resolve("other"));
        Files.writeString(other.resolve("gabier-daemon"), "#!/bin/sh\nexit 0\n");
        var environment = Map.of("GABIER_SOCKET", socket().toString(), "PATH", bin() + ":" + other);

        var failure = catchThrowableOfType(
                DaemonException.class, () -> new DaemonLauncher(environment, directory, null, LONG).connect(socket()));

        assertThat(failure.getMessage()).contains("gabier-daemon was not found");
    }

    @Test
    void reportsADaemonThatExitsAtOnceWithItsExitCodeAndTheLogHoldingWhatItSaid() throws IOException {
        FakeDaemonScripts.install(bin(), "echo 'cannot start: the project is not a folder'; exit 3");

        var failure = failure(LONG);

        assertThat(failure.key().get()).isEqualTo("daemon-start-failed");
        assertThat(failure.getMessage()).contains("exited with code 3 before it listened");
        assertThat(failure.parameters())
                .containsEntry("socket", socket().toString())
                .containsEntry("log", log().toString())
                .containsEntry("exitCode", "3");
        assertThat(Files.readString(log())).isEqualTo("cannot start: the project is not a folder\n");
    }

    @Test
    void keepsTheOutputOfTheLastStartOnly() throws IOException {
        FakeDaemonScripts.install(bin(), "echo first; exit 3");
        failure(LONG);
        FakeDaemonScripts.install(bin(), "echo second; exit 3");

        failure(LONG);

        assertThat(Files.readString(log())).isEqualTo("second\n");
    }

    @Test
    void reportsADaemonThatDoesNotListenBeforeTheDelayAndLeavesNothingRunning() throws Exception {
        var pidFile = directory.resolve("pid");
        FakeDaemonScripts.install(bin(), SYSTEM_PATH + "echo $$ > '" + pidFile + "'\nexec sleep 60");

        var failure = failure(SHORT);

        assertThat(failure.key().get()).isEqualTo("daemon-start-failed");
        assertThat(failure.getMessage()).contains("did not listen on " + socket() + " within 0.6 seconds");
        assertThat(failure.parameters()).containsEntry("log", log().toString());
        assertGone(Long.parseLong(Files.readString(pidFile).strip()));
    }

    @Test
    void killsWhatTheDaemonStartedWhenItDoesNotListenBeforeTheDelay() throws Exception {
        var pidFile = directory.resolve("child");
        FakeDaemonScripts.install(bin(), SYSTEM_PATH + "sleep 60 &\necho $! > '" + pidFile + "'\nwait");

        failure(SHORT);

        assertGone(Long.parseLong(Files.readString(pidFile).strip()));
    }

    @Test
    void reportsAWaitThatIsInterruptedAndKeepsTheInterruption() throws IOException {
        FakeDaemonScripts.install(bin(), SYSTEM_PATH + "exec sleep 60");
        Thread.currentThread().interrupt();

        try {
            var failure = failure(LONG);

            assertThat(failure.getMessage()).contains("was interrupted");
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void reportsALogThatCannotBeWritten() throws IOException {
        FakeDaemonScripts.install(bin(), "exit 0");
        var file = Files.writeString(directory.resolve("not-a-folder"), "");
        var environment = Map.of("GABIER_SOCKET", file.resolve("gabier.sock").toString(), "PATH", bin().toString());

        var failure = catchThrowableOfType(
                DaemonException.class,
                () -> new DaemonLauncher(environment, directory, null, LONG).connect(file.resolve("gabier.sock")));

        assertThat(failure.key().get()).isEqualTo("daemon-start-failed");
        assertThat(failure.getMessage()).contains("cannot write the log of the daemon");
    }

    @Test
    void reportsADaemonThatCannotBeLaunchedBecauseTheFolderItShouldRunInIsNotThere() throws IOException {
        FakeDaemonScripts.install(bin(), "exit 0");
        var missing = directory.resolve("missing");

        var failure = catchThrowableOfType(
                DaemonException.class, () -> new DaemonLauncher(environment(), missing, null, LONG).connect(socket()));

        assertThat(failure.key().get()).isEqualTo("daemon-start-failed");
        assertThat(failure.getMessage()).contains("could not be launched");
        assertThat(failure.parameters()).containsEntry("log", log().toString());
    }
}
