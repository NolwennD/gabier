package fr.craft.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;

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
        return Main.run(args, environment, selfCheck, printStream(out), printStream(err));
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
                    .isEqualTo("Usage error: Unsupported arguments; usage: gabier-daemon [--version | --self-check]"
                            + " (usage-error; arguments=--other)" + System.lineSeparator());
        }

        @Test
        void theSelfCheckFollowedByAnotherArgumentIsAUsageError() {
            assertThat(run(FAILING, "--self-check", "--other")).isEqualTo(2);
        }

        @Test
        void theVersionFollowedByAnotherArgumentIsAUsageError() {
            assertThat(run(PASSING, "--version", "--other")).isEqualTo(2);
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

        private CompletableFuture<Integer> start(Map<String, String> environment, Path expectedSocket)
                throws Exception {
            socket = expectedSocket;
            var daemon = CompletableFuture.supplyAsync(() -> run(environment, PASSING));
            for (var attempt = 0; attempt < 500 && !Files.exists(socket) && !daemon.isDone(); attempt++) {
                TimeUnit.MILLISECONDS.sleep(20);
            }
            assertThat(socket).exists();

            return daemon;
        }

        private CompletableFuture<Integer> start() throws Exception {
            var socketPath = directory.resolve("gabier/gabier.sock");

            return start(Map.of("GABIER_SOCKET", socketPath.toString()), socketPath);
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
            assertThat(out()).isEqualTo("gabier-daemon 0.1 listening on " + socket + System.lineSeparator());
            assertThat(err()).isEmpty();
        }

        @Test
        void serveNoProjectYetSoItRefusesTheDiagnosticsOfAFileAsNotReady() throws Exception {
            var daemon = start();

            assertThat(ask("""
                {"jsonrpc":"2.0","id":3,"method":"diagnostics","params":{"file":"/some/File.java"}}
                """)).contains("\"key\":\"language-server-not-ready\"");
            ask(STOP);

            assertThat(daemon.orTimeout(10, TimeUnit.SECONDS).join()).isZero();
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
