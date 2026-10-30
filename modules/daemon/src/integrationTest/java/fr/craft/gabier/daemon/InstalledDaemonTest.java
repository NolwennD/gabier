package fr.craft.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;

import fr.craft.gabier.editing.fixtures.Scenario.Answer;
import fr.craft.gabier.editing.fixtures.Scenarios;
import fr.craft.gabier.protocol.DaemonStatus;
import fr.craft.gabier.protocol.FileDiagnostics;
import fr.craft.gabier.protocol.JdtlsState;
import fr.craft.gabier.protocol.RpcResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The installed daemon ({@code :daemon:installDist}, what a user runs) serving a small project through a real jdtls,
 * as a client on its socket sees it: {@code status} starts nothing, the first {@code diagnostics} starts jdtls and gets
 * the error of a broken file, and {@code stop} (or a stop while jdtls still starts) leaves no process of the daemon
 * nor of jdtls. Not run by {@code check}: {@code ./gradlew jdtlsProbe}, with {@code GABIER_JDTLS} set.
 */
class InstalledDaemonTest {

    private static final Duration TIMEOUT = Duration.ofMinutes(5);

    @TempDir
    Path directory;

    /** A daemon the test started, on a socket of its own, serving the project it was given. */
    private final class Daemon implements AutoCloseable {

        private final Process process;
        private final Path socket = directory.resolve("gabier.sock");
        private final Set<ProcessHandle> processes = new LinkedHashSet<>();

        Daemon(Path project) throws IOException, InterruptedException {
            var launcher = RealJdtls.launcher();
            var command = new ProcessBuilder(System.getProperty("gabier.daemon"), project.toString())
                    .directory(directory.toFile())
                    .redirectErrorStream(true)
                    .redirectOutput(directory.resolve("daemon.log").toFile());
            command.environment().put("GABIER_SOCKET", socket.toString());
            command.environment().put("GABIER_JDTLS", launcher.launcher().toString());
            command.environment().put("JAVA_HOME", System.getProperty("java.home"));
            process = command.start();
            for (var attempt = 0; attempt < 600 && !log().contains(" listening on "); attempt++) {
                assertThat(process.isAlive())
                        .as("the daemon is running: %s", log())
                        .isTrue();
                TimeUnit.MILLISECONDS.sleep(100);
            }
            assertThat(log()).contains(" listening on ");
        }

        String log() throws IOException {
            return Files.readString(directory.resolve("daemon.log"));
        }

        /** One request on a connection of its own; the line the daemon answers. */
        String ask(String method, String params) throws IOException {
            try (var channel = SocketChannel.open(StandardProtocolFamily.UNIX)) {
                channel.connect(UnixDomainSocketAddress.of(socket));
                var reader = new BufferedReader(
                        new InputStreamReader(Channels.newInputStream(channel), StandardCharsets.UTF_8));
                var request = """
                    {"jsonrpc":"2.0","id":1,"method":"%s","params":%s}
                    """.formatted(method, params);
                Channels.newOutputStream(channel).write(request.getBytes(StandardCharsets.UTF_8));
                var answer = reader.readLine();
                // A stop is answered, then the connection ends once the socket is gone and jdtls with it.
                if (method.equals("stop")) {
                    assertThat(reader.readLine()).isNull();
                }

                return answer;
            }
        }

        DaemonStatus status() throws IOException {
            var answer = RpcResponse.parse(ask("status", "{}"));
            remember();

            return DaemonStatus.of(((RpcResponse.Success) answer).result());
        }

        /** What runs under the daemon now (jdtls and its children), added to what was seen before. */
        void remember() {
            process.descendants().forEach(processes::add);
        }

        void stop() throws IOException, InterruptedException {
            remember();
            assertThat(ask("stop", "{}")).contains("\"stopping\":true");
            assertThat(process.waitFor(TIMEOUT.toSeconds(), TimeUnit.SECONDS))
                    .as("the daemon ends: %s", log())
                    .isTrue();
        }

        /** The daemon is gone, and so is every process it started: none is left running. */
        void assertNothingIsLeft() throws Exception {
            assertThat(process.exitValue()).isZero();
            assertThat(socket).doesNotExist();
            assertThat(processes)
                    .as("the processes jdtls started, seen while it ran")
                    .isNotEmpty();
            for (var handle : processes) {
                handle.onExit().get(30, TimeUnit.SECONDS);
                assertThat(handle.isAlive())
                        .as("process %d (%s)", handle.pid(), handle.info())
                        .isFalse();
            }
            System.out.println("no process left: " + processes.size() + " processes of jdtls ended with the daemon");
        }

        @Override
        public void close() {
            processes.forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
        }
    }

    private Path project() throws IOException {
        var project = Files.createDirectory(directory.resolve("project")).toRealPath();
        Scenarios.FILE_WITH_AN_ERROR.writeProject(project);
        EclipseProject.describe(project);

        return project;
    }

    private static String diagnosticsOf(Path project) {
        var file = project.resolve(Scenarios.FILE_WITH_AN_ERROR.request().file());

        return "{\"file\":\"%s\"}".formatted(file);
    }

    @Test
    void startsJdtlsAtTheFirstRequestThatNeedsItAnswersTheErrorOfTheFileAndLeavesNothingAfterStop() throws Exception {
        var project = project();
        try (var daemon = new Daemon(project)) {
            var before = daemon.status();
            assertThat(before.project()).isEqualTo(project);
            assertThat(before.jdtls()).as("status starts nothing").isEqualTo(JdtlsState.NOT_STARTED);
            assertThat(daemon.processes).isEmpty();

            var answer = RpcResponse.parse(daemon.ask("diagnostics", diagnosticsOf(project)));
            daemon.remember();
            assertThat(answer).as("the answer: %s", answer).isInstanceOf(RpcResponse.Success.class);
            var found = FileDiagnostics.of(((RpcResponse.Success) answer).result());
            var expected = ((Answer.Diagnosed) Scenarios.FILE_WITH_AN_ERROR.answer())
                    .diagnostics()
                    .getFirst();
            assertThat(found.diagnostics()).hasSize(1);
            var error = found.diagnostics().getFirst();
            assertThat(error.severity()).isEqualTo(FileDiagnostics.Severity.ERROR);
            assertThat(error.line()).isEqualTo(expected.position().line());
            assertThat(error.column()).isEqualTo(expected.position().column());
            assertThat(error.message()).isEqualTo(expected.message());
            System.out.println("the daemon answered: " + error);

            assertThat(daemon.status().jdtls()).isEqualTo(JdtlsState.READY);

            daemon.stop();
            daemon.assertNothingIsLeft();
        }
    }

    @Test
    void endsJdtlsWhileItStillStartsWhenTheDaemonIsStopped() throws Exception {
        var project = project();
        try (var daemon = new Daemon(project)) {
            var request = CompletableFuture.supplyAsync(() -> {
                try {
                    return daemon.ask("diagnostics", diagnosticsOf(project));
                } catch (IOException failure) {
                    return "the connection ended: " + failure;
                }
            });
            List<JdtlsState> seen = new ArrayList<>();
            for (var attempt = 0;
                    attempt < 600 && !(seen.contains(JdtlsState.STARTING) && !daemon.processes.isEmpty());
                    attempt++) {
                seen.add(daemon.status().jdtls());
                TimeUnit.MILLISECONDS.sleep(100);
            }
            assertThat(seen).as("states seen while the first request waits").contains(JdtlsState.STARTING);
            assertThat(request).as("the request waits for jdtls").isNotDone();

            daemon.stop();

            System.out.println("the request that waited ended with: "
                    + request.orTimeout(1, TimeUnit.MINUTES).join());
            daemon.assertNothingIsLeft();
        }
    }
}
