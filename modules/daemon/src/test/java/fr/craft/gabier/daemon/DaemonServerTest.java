package fr.craft.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import fr.craft.gabier.editing.fixtures.FakeJavaLanguageServer;
import fr.craft.gabier.editing.fixtures.Scenarios;
import fr.craft.gabier.protocol.DaemonStatus;
import fr.craft.gabier.protocol.FileDiagnostics;
import fr.craft.gabier.protocol.FileDiagnostics.Diagnosis;
import fr.craft.gabier.protocol.FileDiagnostics.Severity;
import fr.craft.gabier.protocol.JdtlsState;
import fr.craft.gabier.protocol.RpcResponse;
import fr.craft.gabier.protocol.UserId;
import fr.craft.gabier.shared.error.domain.ErrorStatus;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The daemon on a real Unix socket, in a temporary directory: never the default path of the user. */
class DaemonServerTest {

    private static final long PID = 4242;
    private static final String STATUS_REQUEST = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"status\"}\n";
    private static final String STOP_REQUEST = "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"stop\"}\n";

    @TempDir
    Path directory;

    private final String uid = UserId.current();
    private final NanoClock clock = new NanoClock();
    private final List<DaemonServer> servers = new ArrayList<>();
    private final List<CompletableFuture<Void>> serving = new ArrayList<>();

    /** The project served: the fake language server, scripted for the project the scenarios play on (the directory). */
    private ScriptedProject project;

    @AfterEach
    void stopEveryServer() throws IOException {
        for (var server : servers) {
            try {
                server.close();
            } catch (IOException e) {
                // A test that made the socket impossible to remove: the folder is deleted with the directory.
            }
        }
        for (var running : serving) {
            running.orTimeout(10, TimeUnit.SECONDS).join();
        }
    }

    private Path socket() {
        return directory.resolve("gabier/gabier.sock");
    }

    @BeforeEach
    void serveTheDirectoryAsAProject() {
        project = new ScriptedProject(
                directory,
                new FakeJavaLanguageServer(directory)
                        .scripted(Scenarios.FILE_WITH_AN_ERROR)
                        .scripted(Scenarios.DIAGNOSTICS_OUTSIDE_THE_PROJECT)
                        .scripted(Scenarios.RENAME_METHOD)
                        .scripted(Scenarios.RENAME_WHERE_THERE_IS_NO_SYMBOL)
                        .scripted(Scenarios.ORGANIZE_IMPORTS)
                        .scripted(Scenarios.ORGANIZE_IMPORTS_IN_ORDER));
    }

    private DaemonServer open() {
        var server = DaemonServer.open(socket(), uid, clock, PID, project);
        servers.add(server);

        return server;
    }

    private CompletableFuture<Void> serve(DaemonServer server) {
        var running = CompletableFuture.runAsync(() -> {
            try {
                server.serve();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        serving.add(running);

        return running;
    }

    private DaemonServer serving() {
        var server = open();
        serve(server);

        return server;
    }

    private static Client connect(Path socket) throws IOException {
        return new Client(socket);
    }

    /** A client of the daemon, as the cli is one. */
    private static final class Client implements AutoCloseable {

        private final SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX);
        private final BufferedReader reader =
                new BufferedReader(new InputStreamReader(Channels.newInputStream(channel), StandardCharsets.UTF_8));

        Client(Path socket) throws IOException {
            channel.connect(UnixDomainSocketAddress.of(socket));
        }

        void send(String text) throws IOException {
            channel.write(ByteBuffer.wrap(text.getBytes(StandardCharsets.UTF_8)));
        }

        /** The next line the daemon wrote, or null once it closed the connection. */
        String readLine() throws IOException {
            return reader.readLine();
        }

        String ask(String request) throws IOException {
            send(request);

            return readLine();
        }

        @Override
        public void close() throws IOException {
            channel.close();
        }
    }

    @Nested
    class Start {

        @Test
        void createsAFolderAndASocketThatAreOnlyTheUsers() throws IOException {
            open();

            assertThat(Files.getPosixFilePermissions(socket().getParent()))
                    .isEqualTo(PosixFilePermissions.fromString("rwx------"));
            assertThat(Files.getPosixFilePermissions(socket())).isEqualTo(PosixFilePermissions.fromString("rw-------"));
        }

        @Test
        void refusesToStartWhenAnotherDaemonAnswers() throws IOException {
            serving();

            var refusal = catchThrowableOfType(
                    DaemonSocketException.class, () -> DaemonServer.open(socket(), uid, clock, PID, project));

            assertThat(refusal.key()).isEqualTo(DaemonErrorKey.ALREADY_RUNNING);
            try (var client = connect(socket())) {
                assertThat(client.ask(STATUS_REQUEST)).contains("\"result\"");
            }
        }

        @Test
        void replacesAStaleSocketNobodyAnswersOn() throws IOException {
            // A daemon that was killed: its socket file is still there, and nobody answers on it.
            new SocketGuard(uid).prepareFolder(socket().getParent());
            ServerSocketChannel.open(StandardProtocolFamily.UNIX)
                    .bind(UnixDomainSocketAddress.of(socket()))
                    .close();
            assertThat(socket()).exists();

            serving();

            try (var client = connect(socket())) {
                assertThat(client.ask(STATUS_REQUEST)).contains("\"result\"");
            }
        }

        @Test
        void refusesAFolderThatIsNotPrivate() throws IOException {
            var folder = Files.createDirectories(socket().getParent());
            Files.setPosixFilePermissions(folder, PosixFilePermissions.fromString("rwxrwxr-x"));

            var refusal = catchThrowableOfType(
                    DaemonSocketException.class, () -> DaemonServer.open(socket(), uid, clock, PID, project));

            assertThat(refusal.key()).isEqualTo(DaemonErrorKey.SOCKET_NOT_PRIVATE);
            assertThat(socket()).doesNotExist();
        }

        @Test
        void doesNotRemoveAFileThatIsNotASocket() throws IOException {
            Files.createDirectories(
                    socket().getParent(),
                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
            Files.writeString(socket(), "precious");

            assertThatThrownBy(() -> DaemonServer.open(socket(), uid, clock, PID, project))
                    .isInstanceOf(DaemonSocketException.class);
            assertThat(socket()).hasContent("precious");
        }

        @Test
        void closesTheListenerAndRemovesTheSocketWhenTheSocketCannotBeRestricted() throws IOException {
            var queued = new ArrayList<SocketChannel>();
            var failing = new SocketGuard(uid) {
                @Override
                void restrict(Path socket) throws IOException {
                    // Bound and listening: a client that connects now waits in the queue of the listener.
                    var client = SocketChannel.open(StandardProtocolFamily.UNIX);
                    client.connect(UnixDomainSocketAddress.of(socket));
                    queued.add(client);

                    throw new IOException("restrict failed");
                }
            };

            var failure = catchThrowableOfType(
                    DaemonSocketException.class, () -> DaemonServer.open(socket(), failing, clock, PID, project));

            assertThat(failure.key()).isEqualTo(DaemonErrorKey.SOCKET_UNAVAILABLE);
            assertThat(failure.getCause()).isInstanceOf(IOException.class).hasMessage("restrict failed");
            assertThat(failure.getCause().getSuppressed()).isEmpty();
            assertThat(socket()).doesNotExist();
            // The listener is closed: the client that was queued on it is dropped.
            assertThatThrownBy(() -> queued.getFirst().read(ByteBuffer.allocate(1)))
                    .isInstanceOf(IOException.class);
            queued.getFirst().close();
        }

        @Test
        void reportsTheOriginalErrorAndWhatCouldNotBeCleanedUpWhenTheSocketCannotBeRestricted() throws IOException {
            var failing = new SocketGuard(uid) {
                @Override
                void restrict(Path socket) throws IOException {
                    Files.delete(socket);
                    Files.createFile(Files.createDirectory(socket).resolve("inside"));

                    throw new IOException("restrict failed");
                }
            };

            var failure = catchThrowableOfType(
                    DaemonSocketException.class, () -> DaemonServer.open(socket(), failing, clock, PID, project));

            assertThat(failure.getCause()).hasMessage("restrict failed");
            assertThat(failure.getCause().getSuppressed()).hasSize(1);
        }

        @Test
        void aSuccessfulStartAfterAFailedOneTakesTheSocket() throws IOException {
            var failing = new SocketGuard(uid) {
                @Override
                void restrict(Path socket) throws IOException {
                    throw new IOException("restrict failed");
                }
            };
            assertThatThrownBy(() -> DaemonServer.open(socket(), failing, clock, PID, project))
                    .isInstanceOf(DaemonSocketException.class);

            serving();

            try (var client = connect(socket())) {
                assertThat(client.ask(STATUS_REQUEST)).contains("\"result\"");
            }
        }

        @Test
        void reportsAnInternalErrorWhenTheSocketCannotBeSetUp() throws IOException {
            var file = Files.writeString(directory.resolve("file"), "x");

            var failure = catchThrowableOfType(
                    DaemonSocketException.class,
                    () -> DaemonServer.open(file.resolve("gabier/gabier.sock"), uid, clock, PID, project));

            assertThat(failure.key()).isEqualTo(DaemonErrorKey.SOCKET_UNAVAILABLE);
            assertThat(failure.status()).isEqualTo(ErrorStatus.INTERNAL_SERVER_ERROR);
            assertThat(failure.getCause()).isInstanceOf(IOException.class);
        }
    }

    @Nested
    class Requests {

        @Test
        void statusAnswersWithTheVersionThePidTheUptimeTheProjectAndWhereJdtlsIs() throws IOException {
            serving();
            clock.advance(Duration.ofSeconds(90));

            try (var client = connect(socket())) {
                var answer = RpcResponse.parse(client.ask(STATUS_REQUEST));

                assertThat(DaemonStatus.of(((RpcResponse.Success) answer).result()))
                        .isEqualTo(new DaemonStatus("0.1", PID, Duration.ofSeconds(90), directory, JdtlsState.READY));
            }
        }

        @Test
        void theUptimeIsMeasuredOnTheMonotonicSourceAndNeverOnTheWallClock() throws IOException {
            // The daemon reads no wall clock at all: a system clock set back after the start cannot make the uptime
            // negative. Here the only source is the scripted monotonic one, which starts at an arbitrary origin.
            serving();

            try (var client = connect(socket())) {
                assertThat(uptime(client)).isZero();
                clock.advance(Duration.ofSeconds(1));
                assertThat(uptime(client)).isEqualTo(Duration.ofSeconds(1));
                clock.advance(Duration.ofMillis(1500));
                assertThat(uptime(client)).isEqualTo(Duration.ofMillis(2500));
            }
        }

        private static Duration uptime(Client client) throws IOException {
            var answer = RpcResponse.parse(client.ask(STATUS_REQUEST));

            return DaemonStatus.of(((RpcResponse.Success) answer).result()).uptime();
        }

        @Test
        void statusSaysEachStateOfJdtlsAsItIsAtTheTimeOfTheRequest() throws IOException {
            serving();

            try (var client = connect(socket())) {
                for (var state : JdtlsState.values()) {
                    project.in(state);

                    var answer = RpcResponse.parse(client.ask(STATUS_REQUEST));

                    assertThat(DaemonStatus.of(((RpcResponse.Success) answer).result())
                                    .jdtls())
                            .isEqualTo(state);
                }
            }
        }

        @Test
        void statusSaysWhyJdtlsFailedAsTheProjectSaysItAndOnlyThen() throws IOException {
            project.because("jdtls exited with code 137 after it was ready");
            serving();

            try (var client = connect(socket())) {
                project.in(JdtlsState.FAILED);
                assertThat(statusOf(client).jdtlsFailure()).contains("jdtls exited with code 137 after it was ready");

                project.in(JdtlsState.READY);
                assertThat(statusOf(client).jdtlsFailure()).isEmpty();
            }
        }

        @Test
        void statusSaysNoReasonWhenJdtlsFailedForAReasonThatIsNotKnown() throws IOException {
            project.in(JdtlsState.FAILED);
            serving();

            try (var client = connect(socket())) {
                assertThat(statusOf(client).jdtlsFailure()).isEmpty();
            }
        }

        private static DaemonStatus statusOf(Client client) throws IOException {
            var answer = RpcResponse.parse(client.ask(STATUS_REQUEST));

            return DaemonStatus.of(((RpcResponse.Success) answer).result());
        }

        @Test
        void diagnosticsAnswersTheProblemsOfTheFileAndRefusesTheOneOutsideTheProject() throws IOException {
            serving();
            var broken =
                    directory.resolve(Scenarios.FILE_WITH_AN_ERROR.request().file());
            var outside = directory.resolve(
                    Scenarios.DIAGNOSTICS_OUTSIDE_THE_PROJECT.request().file());

            try (var client = connect(socket())) {
                var answer = RpcResponse.parse(client.ask(diagnosticsRequest(broken)));

                assertThat(FileDiagnostics.of(((RpcResponse.Success) answer).result()))
                        .isEqualTo(new FileDiagnostics(List.of(new Diagnosis(
                                Severity.ERROR, 4, 15, "Type mismatch: cannot convert from String to int"))));
                assertThat(client.ask(diagnosticsRequest(outside)))
                        .contains("\"key\":\"language-server-file-outside-project\"");
                assertThat(client.ask(STATUS_REQUEST)).contains("\"result\"");
            }
        }

        @Test
        void renameAndRenameTokenAnswerWhatChangedAndWriteTheFilesUnlessItIsADryRun() throws IOException {
            serving();
            Scenarios.RENAME_METHOD.writeProject(directory);
            var greeter = directory.resolve("src/main/java/demo/Greeter.java");

            try (var client = connect(socket())) {
                var dryRun = client.ask(renameRequest(greeter, true));

                assertThat(dryRun).contains("\"modified\":[").contains("welcome");
                assertThat(Files.readString(greeter)).contains("String greet(");

                var byToken = client.ask(renameTokenRequest(greeter));

                assertThat(byToken).contains("\"modified\":[");
                assertThat(Files.readString(greeter)).contains("String welcome(");
                assertThat(client.ask(STATUS_REQUEST)).contains("\"result\"");
            }
        }

        @Test
        void organizeImportsAnswersWhatChangedAndWritesTheFileUnlessItIsADryRun() throws IOException {
            serving();
            Scenarios.ORGANIZE_IMPORTS.writeProject(directory);
            var untidy = directory.resolve("src/main/java/demo/Untidy.java");
            var tidy = directory.resolve("src/main/java/demo/Tidy.java");

            try (var client = connect(socket())) {
                var dryRun = client.ask(organizeImportsRequest(untidy, true));

                assertThat(dryRun).contains("\"modified\":[").contains("ArrayList");
                assertThat(Files.readString(untidy)).contains("import java.util.Set;");

                var done = client.ask(organizeImportsRequest(untidy, false));

                assertThat(done).contains("\"modified\":[");
                assertThat(Files.readString(untidy)).doesNotContain("import java.util.Set;");
                assertThat(client.ask(organizeImportsRequest(tidy, false))).contains("\"modified\":[]");
                assertThat(client.ask(STATUS_REQUEST)).contains("\"result\"");
            }
        }

        private static String organizeImportsRequest(Path file, boolean dryRun) {
            return """
                {"jsonrpc":"2.0","id":3,"method":"organize-imports","params":{"file":"%s","dryRun":"%s"}}
                """.formatted(file, dryRun);
        }

        @Test
        void aRenameWhereThereIsNoSymbolIsRefusedWithItsProblemObject() throws IOException {
            serving();
            Scenarios.RENAME_METHOD.writeProject(directory);
            var greeter = directory.resolve("src/main/java/demo/Greeter.java");

            try (var client = connect(socket())) {
                assertThat(client.ask("""
                    {"jsonrpc":"2.0","id":4,"method":"rename","params":{"file":"%s","line":"4","column":"0",\
                    "newName":"anything","dryRun":"false"}}
                    """.formatted(greeter))).contains("\"key\":\"language-server-no-such-symbol\"");
            }
        }

        private static String renameRequest(Path file, boolean dryRun) {
            return """
                {"jsonrpc":"2.0","id":3,"method":"rename","params":{"file":"%s","line":"5","column":"18",\
                "newName":"welcome","dryRun":"%s"}}
                """.formatted(file, dryRun);
        }

        private static String renameTokenRequest(Path file) {
            return """
                {"jsonrpc":"2.0","id":3,"method":"rename-token","params":{"file":"%s","token":"greet",\
                "newName":"welcome","dryRun":"false"}}
                """.formatted(file);
        }

        private static String diagnosticsRequest(Path file) {
            return """
                {"jsonrpc":"2.0","id":3,"method":"diagnostics","params":{"file":"%s"}}
                """.formatted(file);
        }

        @Test
        void servesSeveralRequestsOnOneConnectionAndSeveralConnectionsAtOnce() throws IOException {
            serving();

            try (var first = connect(socket());
                    var second = connect(socket())) {
                assertThat(first.ask(STATUS_REQUEST)).contains("\"id\":1");
                assertThat(second.ask(STATUS_REQUEST)).contains("\"id\":1");
                assertThat(first.ask(STATUS_REQUEST)).contains("\"id\":1");
            }
        }

        @Test
        void aMalformedLineAnUnknownMethodAndAnInvalidRequestAreAnsweredAndTheDaemonGoesOn() throws IOException {
            serving();

            try (var client = connect(socket())) {
                assertThat(client.ask("{broken\n")).contains("\"code\":-32700").contains("\"key\":\"malformed-json\"");
                assertThat(client.ask("{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"nope\"}\n"))
                        .contains("\"id\":3")
                        .contains("\"code\":-32601")
                        .contains("\"key\":\"unknown-method\"");
                assertThat(client.ask("[]\n")).contains("\"code\":-32600");
                assertThat(client.ask(STATUS_REQUEST)).contains("\"result\"");
            }
        }

        @Test
        void aNumberWithAnAbsurdExponentAndANestingTooDeepAreAnsweredAndTheDaemonGoesOn() throws IOException {
            serving();

            try (var client = connect(socket())) {
                assertThat(client.ask("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"status\",\"x\":1e99999999999}\n"))
                        .contains("\"key\":\"malformed-json\"");
                assertThat(client.ask(STATUS_REQUEST)).contains("\"result\"");
                assertThat(client.ask("{\"jsonrpc\":\"2.0\",\"id\":1e999999999,\"method\":\"status\"}\n"))
                        .contains("\"key\":\"malformed-json\"");
                assertThat(client.ask(STATUS_REQUEST)).contains("\"result\"");
                assertThat(client.ask("[".repeat(100_000) + "\n")).contains("\"key\":\"malformed-json\"");
                assertThat(client.ask(STATUS_REQUEST)).contains("\"result\"");
            }
        }

        @Test
        void aLineOneByteOverTheLimitIsRefusedAndTheDaemonAnswersTheNextConnection() throws IOException {
            serving();
            var status = STATUS_REQUEST.stripTrailing();

            try (var greedy = connect(socket())) {
                var answer = greedy.ask(" ".repeat(LineReader.MAX_LINE_BYTES + 1 - status.length()) + status + "\n");

                assertThat(answer).contains("\"code\":-32000").contains("\"key\":\"line-too-long\"");
                assertThat(greedy.ask(STATUS_REQUEST)).contains("\"result\"");
            }
            try (var client = connect(socket())) {
                assertThat(client.ask(STATUS_REQUEST)).contains("\"result\"");
            }
        }

        @Test
        void aLineOfExactlyTheLimitIsRead() throws IOException {
            serving();
            var status = STATUS_REQUEST.stripTrailing();

            try (var client = connect(socket())) {
                var answer = client.ask(" ".repeat(LineReader.MAX_LINE_BYTES - status.length()) + status + "\n");

                assertThat(answer).contains("\"result\"");
            }
        }

        @Test
        void aClientThatDisconnectsInTheMiddleOfARequestLeavesTheDaemonAlive() throws IOException {
            serving();

            try (var cut = connect(socket())) {
                cut.send("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"stop\"}");
            }

            try (var client = connect(socket())) {
                assertThat(client.ask(STATUS_REQUEST)).contains("\"result\"");
            }
        }

        @Test
        void aClientThatDisconnectsBeforeTheAnswerLeavesTheDaemonAlive() throws IOException {
            serving();

            try (var gone = connect(socket())) {
                gone.send(STATUS_REQUEST + STATUS_REQUEST);
            }

            try (var client = connect(socket())) {
                assertThat(client.ask(STATUS_REQUEST)).contains("\"result\"");
            }
        }
    }

    @Nested
    class Stop {

        @Test
        void answersThenStopsServingAndRemovesTheSocket() throws IOException {
            var running = serve(open());

            try (var client = connect(socket())) {
                assertThat(client.ask(STOP_REQUEST))
                        .isEqualTo("{\"jsonrpc\":\"2.0\",\"id\":2,\"result\":{\"stopping\":true}}");
                // The connection is closed once the socket is gone: the client can wait for that.
                assertThat(client.readLine()).isNull();
            }

            running.orTimeout(10, TimeUnit.SECONDS).join();
            assertThat(socket()).doesNotExist();
            assertThatThrownBy(() -> connect(socket())).isInstanceOf(IOException.class);
        }

        @Test
        void endsTheLanguageServerOfTheProjectBeforeTheClientIsLetGo() throws IOException {
            var running = serve(open());

            try (var client = connect(socket())) {
                client.ask(STOP_REQUEST);
                assertThat(client.readLine()).isNull();

                assertThat(project.closes()).isEqualTo(1);
            }
            running.orTimeout(10, TimeUnit.SECONDS).join();
        }

        @Test
        void aRequestThatWaitsWhenTheDaemonStopsGetsItsAnswerBeforeItsConnectionEnds() throws Exception {
            project.whoseRequestsWaitForTheClose();
            var running = serve(open());

            try (var waiter = connect(socket());
                    var stopper = connect(socket())) {
                waiter.send(diagnosticsRequest());
                assertThat(project.awaitRequest()).as("the request waits").isTrue();

                stopper.ask(STOP_REQUEST);

                assertThat(waiter.readLine())
                        .contains("\"key\":\"jdtls-start-failed\"")
                        .contains("the daemon is stopping");
                assertThat(waiter.readLine()).isNull();
            }
            running.orTimeout(10, TimeUnit.SECONDS).join();
        }

        @Test
        void givesUpOnARequestThatNeverEndsOnceTheGraceIsOverAndDisconnectsItsClient() throws Exception {
            project.whoseRequestsNeverEnd();
            var running = serve(open().disconnectingAfter(Duration.ofMillis(200)));

            try (var waiter = connect(socket());
                    var stopper = connect(socket())) {
                waiter.send(diagnosticsRequest());
                assertThat(project.awaitRequest()).as("the request waits").isTrue();

                stopper.ask(STOP_REQUEST);

                assertThat(waiter.readLine()).isNull();
                running.orTimeout(10, TimeUnit.SECONDS).join();
            } finally {
                project.release();
            }
        }

        private String diagnosticsRequest() {
            return """
                {"jsonrpc":"2.0","id":3,"method":"diagnostics","params":{"file":"%s"}}
                """.formatted(directory.resolve("src/main/java/demo/Broken.java"));
        }

        @Test
        void endsTheLanguageServerOnceWhateverTheNumberOfCloses() throws IOException {
            var server = serving();

            server.close();
            server.shutdownHook().run();

            assertThat(project.closes()).isEqualTo(1);
        }

        @Test
        void stillDisconnectsTheClientsAndKeepsTheInterruptWhenEndingTheLanguageServerIsInterrupted()
                throws IOException {
            project.interruptedWhenClosed();
            var server = serving();

            try (var idle = connect(socket())) {
                assertThat(idle.ask(STATUS_REQUEST)).contains("\"result\"");

                server.close();

                assertThat(Thread.interrupted()).isTrue();
                assertThat(idle.readLine()).isNull();
            }
        }

        @Test
        void closesTheConnectionsOfTheClientsStillThere() throws IOException {
            serving();

            try (var idle = connect(socket());
                    var stopper = connect(socket())) {
                assertThat(idle.ask(STATUS_REQUEST)).contains("\"result\"");
                stopper.ask(STOP_REQUEST);

                assertThat(idle.readLine()).isNull();
            }
        }

        @Test
        void aServerThatIsClosedBeforeItServesDoesNotServe() throws IOException {
            var server = open();

            server.close();

            serve(server).orTimeout(10, TimeUnit.SECONDS).join();
            assertThat(socket()).doesNotExist();
        }

        @Test
        void closingTwiceIsHarmless() throws IOException {
            var server = serving();

            server.close();
            server.close();

            assertThat(socket()).doesNotExist();
        }
    }

    @Nested
    class Termination {

        @Test
        void theShutdownHookRemovesTheSocketAndEndsTheService() {
            var server = open();
            var running = serve(server);

            server.shutdownHook().run();

            running.orTimeout(10, TimeUnit.SECONDS).join();
            assertThat(socket()).doesNotExist();
        }

        @Test
        void theShutdownHookIsAThreadNamedAfterTheDaemon() {
            assertThat(open().shutdownHook().getName()).isEqualTo("gabier-shutdown");
        }

        @Test
        void closingReportsASocketThatCannotBeRemoved() throws IOException {
            var server = open();
            replaceTheSocketWithANonEmptyFolder();

            assertThatThrownBy(server::close).isInstanceOf(IOException.class);
        }

        @Test
        void theShutdownHookDoesNotFailWhenTheSocketCannotBeRemoved() throws IOException {
            var server = open();
            replaceTheSocketWithANonEmptyFolder();

            server.shutdownHook().run();
        }

        private void replaceTheSocketWithANonEmptyFolder() throws IOException {
            Files.delete(socket());
            Files.createFile(Files.createDirectory(socket()).resolve("inside"));
        }
    }

    /**
     * A monotonic source (the seam of {@code System.nanoTime}) that only moves when the test says so. Its origin is
     * arbitrary, as the one of {@code System.nanoTime}: here close enough to the end of the range to wrap around.
     */
    private static final class NanoClock implements LongSupplier {

        private long now = Long.MAX_VALUE - Duration.ofSeconds(30).toNanos();

        void advance(Duration duration) {
            now += duration.toNanos();
        }

        @Override
        public long getAsLong() {
            return now;
        }
    }
}
