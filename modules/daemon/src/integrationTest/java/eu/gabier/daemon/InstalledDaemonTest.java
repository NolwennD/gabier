package eu.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;

import eu.gabier.editing.fixtures.Scenario.Answer;
import eu.gabier.editing.fixtures.Scenarios;
import eu.gabier.protocol.ChangedFiles;
import eu.gabier.protocol.DaemonStatus;
import eu.gabier.protocol.FileDiagnostics;
import eu.gabier.protocol.JdtlsState;
import eu.gabier.protocol.RpcResponse;
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
import java.util.Map;
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
        private final Set<ProcessHandle> java = new LinkedHashSet<>();

        Daemon(Path project) throws IOException, InterruptedException {
            this(project, Map.of("GABIER_JDTLS", RealJdtls.launcher().launcher().toString()));
        }

        /** A daemon started with the given environment on top of the current one, without GABIER_JDTLS. */
        Daemon(Path project, Map<String, String> environment) throws IOException, InterruptedException {
            var command = new ProcessBuilder(System.getProperty("gabier.daemon"), project.toString())
                    .directory(directory.toFile())
                    .redirectErrorStream(true)
                    .redirectOutput(directory.resolve("daemon.log").toFile());
            command.environment().put("GABIER_SOCKET", socket.toString());
            command.environment().remove("GABIER_JDTLS");
            command.environment().putAll(environment);
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
            process.descendants().forEach(handle -> {
                processes.add(handle);
                if (isJava(handle)) {
                    java.add(handle);
                }
            });
        }

        /** The java process of jdtls, which the launcher script may have started after it: it is not the launcher. */
        private static boolean isJava(ProcessHandle handle) {
            return handle.info()
                    .command()
                    .map(command -> Path.of(command).getFileName().toString().equals("java"))
                    .orElse(false);
        }

        /** Waits until the java process of jdtls runs: seeing the launcher script alone proves nothing about the rest. */
        void awaitJava() throws IOException, InterruptedException {
            for (var attempt = 0; attempt < 600 && java.isEmpty(); attempt++) {
                remember();
                TimeUnit.MILLISECONDS.sleep(100);
            }
            assertThat(java)
                    .as("the java process of jdtls, among %s", processes)
                    .isNotEmpty();
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
            assertThat(java).as("the java process of jdtls, seen while it ran").isNotEmpty();
            for (var handle : processes) {
                handle.onExit().get(30, TimeUnit.SECONDS);
                assertThat(handle.isAlive())
                        .as("process %d (%s)", handle.pid(), handle.info())
                        .isFalse();
            }
            System.out.println("no process left: " + processes.size() + " processes of jdtls ended with the daemon");
        }

        /**
         * Whatever failed, nothing of jdtls is left running: what runs under the daemon is remembered first, the
         * daemon is asked to end (SIGTERM, which ends jdtls on its way out), and everything seen is killed after it.
         */
        @Override
        public void close() throws InterruptedException {
            remember();
            process.destroy();
            process.waitFor(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
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

    private static String renameByPosition(Path file, String newName, boolean dryRun) {
        return """
            {"file":"%s","line":"5","column":"18","newName":"%s","dryRun":"%s"}\
            """.formatted(file, newName, dryRun);
    }

    private static String renameByToken(Path file, String token, String newName) {
        return """
            {"file":"%s","token":"%s","newName":"%s","dryRun":"false"}\
            """.formatted(file, token, newName);
    }

    private static ChangedFiles changedBy(String answer) {
        var parsed = RpcResponse.parse(answer);
        assertThat(parsed).as("the answer: %s", answer).isInstanceOf(RpcResponse.Success.class);

        return ChangedFiles.of(((RpcResponse.Success) parsed).result());
    }

    @Test
    void startsThePinnedJdtlsInstalledUnderTheHomeFolderWhenGabierJdtlsIsNotSet() throws Exception {
        // A home folder of its own, where the pinned jdtls is installed the way `mise run jdtls:install` does it (a
        // link to
        // the distribution of GABIER_JDTLS), and a PATH with no jdtls: only the pinned location can be the one found.
        var home = Files.createDirectory(directory.resolve("home"));
        var installed = home.resolve(".local/share/jdtls").resolve(System.getProperty("gabier.jdtlsVersion"));
        Files.createDirectories(installed.getParent());
        Files.createSymbolicLink(
                installed, RealJdtls.launcher().launcher().getParent().getParent());
        // A project with no mise.toml (nothing in the folder exports the variable either).
        var project = project();
        var environment = Map.of("JAVA_OPTS", "-Duser.home=" + home, "PATH", "/usr/bin:/bin");
        try (var daemon = new Daemon(project, environment)) {
            var answer = RpcResponse.parse(daemon.ask("diagnostics", diagnosticsOf(project)));
            daemon.remember();

            assertThat(answer).as("the answer: %s", answer).isInstanceOf(RpcResponse.Success.class);
            assertThat(FileDiagnostics.of(((RpcResponse.Success) answer).result())
                            .diagnostics())
                    .hasSize(1);
            System.out.println("the daemon found the pinned jdtls by itself: " + installed);

            daemon.stop();
            daemon.assertNothingIsLeft();
        }
    }

    @Test
    void renamesASymbolOfAProjectOnTheRealJdtlsAndAnotherRenameSeesTheFirst() throws Exception {
        var project = Files.createDirectory(directory.resolve("rename-project")).toRealPath();
        Scenarios.RENAME_METHOD.writeProject(project);
        EclipseProject.describe(project);
        var greeter = project.resolve("src/main/java/demo/Greeter.java");
        var main = project.resolve("src/main/java/demo/Main.java");
        var original = Files.readString(greeter);
        try (var daemon = new Daemon(project)) {
            var dryRun = changedBy(daemon.ask("rename", renameByPosition(greeter, "welcome", true)));
            daemon.remember();
            assertThat(dryRun.modified()).containsExactlyInAnyOrder(greeter, main);
            assertThat(dryRun.diff()).contains("+    public String welcome(String name) {");
            assertThat(Files.readString(greeter)).as("a dry run writes nothing").isEqualTo(original);

            var renamed = changedBy(daemon.ask("rename", renameByPosition(greeter, "welcome", false)));
            assertThat(renamed.modified()).containsExactlyInAnyOrder(greeter, main);
            assertThat(Files.readString(greeter)).contains("public String welcome(String name)");
            assertThat(Files.readString(main)).contains("greeter.welcome(\"world\")");
            System.out.println("the installed daemon renamed greet to welcome: " + renamed.modified());

            // jdtls has learnt of the files the first rename wrote: the second one finds the new name.
            var back = changedBy(daemon.ask("rename-token", renameByToken(greeter, "welcome", "greet")));
            assertThat(back.modified()).containsExactlyInAnyOrder(greeter, main);
            assertThat(Files.readString(greeter)).isEqualTo(original);

            var none = daemon.ask(
                    "rename",
                    "{\"file\":\"%s\",\"line\":\"4\",\"column\":\"0\",\"newName\":\"x\",\"dryRun\":\"false\"}"
                            .formatted(greeter));
            assertThat(none).contains("\"key\":\"language-server-no-such-symbol\"");
            assertThat(Files.readString(greeter)).isEqualTo(original);

            daemon.stop();
            daemon.assertNothingIsLeft();
        }
    }

    @Test
    void refusesAFolderWithNoBuildToImportAtOnceInsteadOfWaitingForTheTimeout() throws Exception {
        // The sources of the project, without the Eclipse files that make it a project jdtls can import.
        var project = Files.createDirectory(directory.resolve("no-build")).toRealPath();
        Scenarios.FILE_WITH_AN_ERROR.writeProject(project);
        try (var daemon = new Daemon(project)) {
            long begin = System.nanoTime();

            var answer = daemon.ask("diagnostics", diagnosticsOf(project));
            daemon.remember();

            assertThat(answer).contains("\"key\":\"no-build-to-import\"");
            assertThat(Duration.ofNanos(System.nanoTime() - begin)).isLessThan(Duration.ofMinutes(1));
            var failed = daemon.status();
            assertThat(failed.jdtls()).isEqualTo(JdtlsState.FAILED);
            assertThat(failed.jdtlsFailure())
                    .hasValueSatisfying(reason -> assertThat(reason).contains("no build to import in " + project));
            System.out.println("a folder with no build was refused: " + answer);

            // The failed start has ended jdtls itself, before the answer: nothing was left for the stop to find.
            daemon.stop();
            assertThat(daemon.process.exitValue()).isZero();
            assertThat(daemon.socket).doesNotExist();
        }
    }

    @Test
    void startsJdtlsAgainAtTheNextRequestWhenItWasKilledWhileReady() throws Exception {
        var project = project();
        try (var daemon = new Daemon(project)) {
            assertThat(RpcResponse.parse(daemon.ask("diagnostics", diagnosticsOf(project))))
                    .isInstanceOf(RpcResponse.Success.class);
            assertThat(daemon.status().jdtls()).isEqualTo(JdtlsState.READY);
            var first = List.copyOf(daemon.processes);
            assertThat(first).as("the processes of the first jdtls").isNotEmpty();

            // kill -9 of the whole tree of jdtls: the launcher and the java process.
            first.forEach(ProcessHandle::destroyForcibly);
            var status = daemon.status();
            for (var attempt = 0; attempt < 100 && status.jdtls() != JdtlsState.FAILED; attempt++) {
                TimeUnit.MILLISECONDS.sleep(100);
                status = daemon.status();
            }
            assertThat(status.jdtls()).as("status after the kill").isEqualTo(JdtlsState.FAILED);
            assertThat(status.jdtlsFailure())
                    .hasValueSatisfying(reason -> assertThat(reason)
                            .startsWith("jdtls is not running any more: it exited with code ")
                            .endsWith(" after it was ready"));
            assertThat(daemon.log()).contains("jdtls exited with code ");

            var answer = RpcResponse.parse(daemon.ask("diagnostics", diagnosticsOf(project)));
            daemon.remember();
            assertThat(answer).as("the answer after the restart: %s", answer).isInstanceOf(RpcResponse.Success.class);
            assertThat(daemon.status().jdtls()).isEqualTo(JdtlsState.READY);
            assertThat(daemon.processes).as("the first jdtls and the second").hasSizeGreaterThan(first.size());
            System.out.println("jdtls killed with SIGKILL, restarted by the next request");

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
                    attempt < 600 && !(seen.contains(JdtlsState.STARTING) && !daemon.java.isEmpty());
                    attempt++) {
                seen.add(daemon.status().jdtls());
                TimeUnit.MILLISECONDS.sleep(100);
            }
            assertThat(seen).as("states seen while the first request waits").contains(JdtlsState.STARTING);
            assertThat(request).as("the request waits for jdtls").isNotDone();

            daemon.stop();

            var answer = request.orTimeout(1, TimeUnit.MINUTES).join();
            System.out.println("the request that waited ended with: " + answer);
            assertThat(answer)
                    .as("the answer of the request that waited, before its connection ended")
                    .contains("\"key\":\"jdtls-start-failed\"")
                    .contains("the daemon is stopping");
            daemon.assertNothingIsLeft();
        }
    }
}
