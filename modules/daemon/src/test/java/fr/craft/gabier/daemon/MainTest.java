package fr.craft.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;

import fr.craft.gabier.protocol.DaemonStatus;
import fr.craft.gabier.protocol.JdtlsState;
import fr.craft.gabier.protocol.RpcResponse;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MainTest {

    private static final SelfCheck PASSING = new SelfCheck("fr.craft.gabier.daemon", "org.eclipse.lsp4j");
    private static final SelfCheck FAILING = new SelfCheck(null, null);

    @TempDir
    Path directory;

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    private int run(SelfCheck selfCheck, String... args) {
        return run(Map.of(), selfCheck, args);
    }

    private int run(Map<String, String> environment, SelfCheck selfCheck, String... args) {
        return run(environment, directory, selfCheck, args);
    }

    private int run(Map<String, String> environment, Path workingDirectory, SelfCheck selfCheck, String... args) {
        return Main.run(args, environment, workingDirectory, selfCheck, printStream(out), printStream(err));
    }

    private static PrintStream printStream(ByteArrayOutputStream stream) {
        return new PrintStream(stream, true, StandardCharsets.UTF_8);
    }

    private String out() {
        return out.toString(StandardCharsets.UTF_8);
    }

    private String err() {
        return err.toString(StandardCharsets.UTF_8);
    }

    @Nested
    class Version {

        @Test
        void printsTheNameAndTheProtocolVersion() {
            run(PASSING, "--version");

            assertThat(out()).isEqualTo("gabier-daemon 0.1" + System.lineSeparator());
        }

        @Test
        void printsNothingOnTheStandardError() {
            run(PASSING, "--version");

            assertThat(err()).isEmpty();
        }

        @Test
        void exitsWithStatusZero() {
            assertThat(run(PASSING, "--version")).isZero();
        }

        @Test
        void doesNotRunTheSelfCheck() {
            assertThat(run(FAILING, "--version")).isZero();
        }
    }

    @Nested
    class Usage {

        @Test
        void anotherArgumentIsAUsageErrorAsAProblemObject() {
            assertThat(run(PASSING, "--other")).isEqualTo(2);

            assertThat(out()).isEmpty();
            assertThat(err())
                    .isEqualTo("Usage error: Unsupported arguments; usage: gabier-daemon [<project folder> | --version"
                            + " | --self-check] (usage-error; arguments=--other)" + System.lineSeparator());
        }

        @Test
        void theSelfCheckFollowedByAnotherArgumentIsAUsageError() {
            assertThat(run(FAILING, "--self-check", "--other")).isEqualTo(2);
        }

        @Test
        void theVersionFollowedByAnotherArgumentIsAUsageError() {
            assertThat(run(PASSING, "--version", "--other")).isEqualTo(2);
        }

        @Test
        void twoProjectsAreAUsageError() {
            assertThat(run(PASSING, directory.toString(), directory.toString())).isEqualTo(2);
        }

        @Test
        void aProjectThatIsNotAFolderIsAUsageErrorThatNamesIt() throws IOException {
            var file = Files.writeString(directory.resolve("not-a-folder"), "x");

            assertThat(run(PASSING, file.toString())).isEqualTo(2);

            assertThat(out()).isEmpty();
            assertThat(err())
                    .isEqualTo("Usage error: The project " + file + " is not a folder (usage-error; project=" + file
                            + ")" + System.lineSeparator());
        }

        @Test
        void aProjectThatDoesNotExistIsAUsageError() {
            assertThat(run(PASSING, "no-such-project")).isEqualTo(2);

            assertThat(err()).contains("(usage-error; project=" + directory.resolve("no-such-project") + ")");
        }
    }

    @Nested
    class SelfCheckOption {

        @Test
        void exitsWithStatusZeroWhenTheCheckPasses() {
            assertThat(run(PASSING, "--self-check")).isZero();
        }

        @Test
        void printsTheSummaryOnTheStandardOutputWhenTheCheckPasses() {
            run(PASSING, "--self-check");

            assertThat(out()).isEqualTo("self-check ok: " + PASSING.summary() + System.lineSeparator());
            assertThat(err()).isEmpty();
        }

        @Test
        void exitsWithStatusOneWhenTheCheckFails() {
            assertThat(run(FAILING, "--self-check")).isEqualTo(1);
        }

        @Test
        void printsEveryProblemOnTheStandardErrorWhenTheCheckFails() {
            run(FAILING, "--self-check");

            assertThat(out()).isEmpty();
            assertThat(err()).startsWith("self-check failed: ").contains(FAILING.problems());
        }
    }

    @Nested
    class Serving {

        private static final String STATUS = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"status\"}\n";
        private static final String STOP = "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"stop\"}\n";

        private Path socket;

        private Path socketPath() {
            return directory.resolve("gabier/gabier.sock");
        }

        private CompletableFuture<Integer> start(Map<String, String> environment, Path expectedSocket)
                throws Exception {
            return start(() -> run(environment, PASSING), expectedSocket);
        }

        private CompletableFuture<Integer> start(java.util.function.IntSupplier run, Path expectedSocket)
                throws Exception {
            socket = expectedSocket;
            var daemon = CompletableFuture.supplyAsync(run::getAsInt);
            for (var attempt = 0; attempt < 500 && !Files.exists(socket) && !daemon.isDone(); attempt++) {
                TimeUnit.MILLISECONDS.sleep(20);
            }
            assertThat(socket).exists();

            return daemon;
        }

        private CompletableFuture<Integer> start() throws Exception {
            return start(Map.of("GABIER_SOCKET", socketPath().toString()), socketPath());
        }

        private Client connect() throws IOException {
            return new Client(socket);
        }

        private String ask(String request) throws IOException {
            try (var client = connect()) {
                return client.ask(request);
            }
        }

        @Test
        void listensOnTheSocketOfTheEnvironmentUntilItIsAskedToStop() throws Exception {
            var daemon = start();

            assertThat(ask(STATUS))
                    .contains("\"result\"")
                    .contains("\"pid\":" + ProcessHandle.current().pid());
            ask(STOP);

            assertThat(daemon.orTimeout(10, TimeUnit.SECONDS).join()).isZero();
            assertThat(socket).doesNotExist();
            assertThat(out())
                    .isEqualTo("gabier-daemon 0.1 listening on " + socket + System.lineSeparator()
                            + "serving the project " + directory.toRealPath() + System.lineSeparator());
            assertThat(err()).isEmpty();
        }

        private DaemonStatus status() throws IOException {
            var answer = RpcResponse.parse(ask(STATUS));

            return DaemonStatus.of(((RpcResponse.Success) answer).result());
        }

        @Test
        void servesTheWorkingDirectoryByDefaultWithoutStartingJdtls() throws Exception {
            var daemon = start();

            assertThat(status().project()).isEqualTo(directory.toRealPath());
            assertThat(status().jdtls()).isEqualTo(JdtlsState.NOT_STARTED);
            ask(STOP);

            assertThat(daemon.orTimeout(10, TimeUnit.SECONDS).join()).isZero();
        }

        @Test
        void servesTheProjectOfTheEnvironmentRatherThanTheWorkingDirectory() throws Exception {
            var project = Files.createDirectory(directory.resolve("from-the-environment"));
            var daemon = start(
                    Map.of("GABIER_SOCKET", socketPath().toString(), "GABIER_PROJECT", project.toString()),
                    socketPath());

            assertThat(status().project()).isEqualTo(project.toRealPath());
            ask(STOP);

            daemon.orTimeout(10, TimeUnit.SECONDS).join();
        }

        @Test
        void servesTheProjectOfTheArgumentRatherThanTheEnvironmentOnceRelativeToTheWorkingDirectory() throws Exception {
            Files.createDirectory(directory.resolve("from-the-argument"));
            var environment = Map.of("GABIER_SOCKET", socketPath().toString(), "GABIER_PROJECT", "/does/not/matter");
            var daemon = start(() -> run(environment, directory, PASSING, "from-the-argument"), socketPath());

            assertThat(status().project())
                    .isEqualTo(directory.resolve("from-the-argument").toRealPath());
            ask(STOP);

            daemon.orTimeout(10, TimeUnit.SECONDS).join();
        }

        @Test
        void startsJdtlsAtTheFirstRequestThatNeedsItAndSaysWhyItCannotWhenItHasNoLauncher() throws Exception {
            var daemon = start();

            var refusal = ask("""
                {"jsonrpc":"2.0","id":3,"method":"diagnostics","params":{"file":"%s"}}
                """.formatted(directory.resolve("File.java")));

            assertThat(refusal)
                    .contains("\"key\":\"jdtls-start-failed\"")
                    .contains("GABIER_JDTLS is not set")
                    .contains("\"project\":\"" + directory.toRealPath() + "\"");
            assertThat(status().jdtls()).isEqualTo(JdtlsState.FAILED);
            ask(STOP);

            assertThat(daemon.orTimeout(10, TimeUnit.SECONDS).join()).isZero();
        }

        @Test
        void tellsOnItsStandardErrorWhatHappensToJdtls() throws Exception {
            var launcher = Files.writeString(directory.resolve("jdtls"), """
                #!/bin/sh
                exit 3
                """);
            Files.setPosixFilePermissions(launcher, PosixFilePermissions.fromString("rwx------"));
            var environment = Map.of("GABIER_SOCKET", socketPath().toString(), "GABIER_JDTLS", launcher.toString());
            var daemon = start(() -> run(environment, PASSING), socketPath());

            assertThat(ask("""
                {"jsonrpc":"2.0","id":3,"method":"diagnostics","params":{"file":"%s"}}
                """.formatted(directory.resolve("File.java"))))
                    .contains("\"key\":\"jdtls-start-failed\"")
                    .contains("jdtls exited with code 3");
            ask(STOP);

            daemon.orTimeout(10, TimeUnit.SECONDS).join();
            assertThat(err()).contains("jdtls: jdtls started, pid ");
        }

        @Test
        void neutralisesWhatJdtlsSaysBeforeItReachesTheStandardError() throws Exception {
            // A launcher that sends a status whose message holds an escape sequence (clear the screen), then leaves.
            var launcher = Files.writeString(directory.resolve("jdtls"), """
                #!/bin/sh
                body='{"jsonrpc":"2.0","method":"language/status","params":{"type":"Message","message":"red\\u001b[2Jgone"}}'
                printf 'Content-Length: %s\\r\\n\\r\\n%s' "${#body}" "$body"
                sleep 1
                exit 3
                """);
            Files.setPosixFilePermissions(launcher, PosixFilePermissions.fromString("rwx------"));
            var environment = Map.of("GABIER_SOCKET", socketPath().toString(), "GABIER_JDTLS", launcher.toString());
            var daemon = start(() -> run(environment, PASSING), socketPath());

            ask("""
                {"jsonrpc":"2.0","id":3,"method":"diagnostics","params":{"file":"%s"}}
                """.formatted(directory.resolve("File.java")));
            ask(STOP);

            daemon.orTimeout(10, TimeUnit.SECONDS).join();
            assertThat(err())
                    .contains("jdtls: jdtls status Message: red\\u001b[2Jgone")
                    .doesNotContain("\u001b");
        }

        @Test
        void usesTheRuntimeDirectoryOfTheUserWhenTheEnvironmentHasNoOverride() throws Exception {
            var runtimeDirectory = Files.createDirectory(directory.resolve("run"));
            var daemon = start(
                    Map.of("XDG_RUNTIME_DIR", runtimeDirectory.toString()),
                    runtimeDirectory.resolve("gabier/gabier.sock"));

            ask(STOP);

            assertThat(daemon.orTimeout(10, TimeUnit.SECONDS).join()).isZero();
        }

        @Test
        void refusesAPathThatIsTooLongBeforeAnyBind() {
            var longPath = "/" + "a".repeat(119);

            assertThat(run(Map.of("GABIER_SOCKET", longPath), PASSING)).isEqualTo(2);

            assertThat(out()).isEmpty();
            assertThat(err())
                    .startsWith("Socket path too long: ")
                    .contains("(socket-path-too-long; ")
                    .contains("length=120")
                    .contains("limit=107")
                    .contains("override=GABIER_SOCKET");
        }

        @Test
        void refusesToStartWhenAnotherDaemonAnswers() throws Exception {
            var first = start();

            assertThat(run(Map.of("GABIER_SOCKET", socket.toString()), PASSING)).isEqualTo(2);

            assertThat(err()).contains("(daemon-already-running; ");
            ask(STOP);
            first.orTimeout(10, TimeUnit.SECONDS).join();
        }

        @Test
        void isAnInternalErrorWhenTheSocketCannotBeSetUp() throws IOException {
            var file = Files.writeString(directory.resolve("file"), "x");

            assertThat(run(
                            Map.of(
                                    "GABIER_SOCKET",
                                    file.resolve("gabier/gabier.sock").toString()),
                            PASSING))
                    .isEqualTo(1);

            assertThat(err()).contains("(socket-unavailable; ");
        }

        @Test
        void isAnInternalErrorWithoutLeakWhenTheSocketCannotBeRemovedAtTheEnd() throws Exception {
            var daemon = start();

            try (var client = connect()) {
                assertThat(client.ask(STATUS)).contains("\"result\"");
                Files.delete(socket);
                Files.createFile(Files.createDirectory(socket).resolve("inside"));
                client.ask(STOP);
            }

            assertThat(daemon.orTimeout(10, TimeUnit.SECONDS).join()).isEqualTo(1);
            assertThat(err())
                    .isEqualTo("Internal server error: An internal error occurred, and it is not described here "
                            + "(internal-server-error)" + System.lineSeparator());
        }
    }

    /** A client of the daemon, as the cli is one. */
    private static final class Client implements AutoCloseable {

        private final SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX);
        private final BufferedReader reader =
                new BufferedReader(new InputStreamReader(Channels.newInputStream(channel), StandardCharsets.UTF_8));

        Client(Path socket) throws IOException {
            channel.connect(UnixDomainSocketAddress.of(socket));
        }

        String ask(String request) throws IOException {
            channel.write(ByteBuffer.wrap(request.getBytes(StandardCharsets.UTF_8)));

            return reader.readLine();
        }

        @Override
        public void close() throws IOException {
            channel.close();
        }
    }
}
