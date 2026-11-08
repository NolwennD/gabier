package eu.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import eu.gabier.editing.fixtures.CleanUpScenarios;
import eu.gabier.editing.fixtures.CodeActionScenarios;
import eu.gabier.editing.fixtures.FakeJavaLanguageServer;
import eu.gabier.editing.fixtures.PackageMoveScenarios;
import eu.gabier.editing.fixtures.Scenarios;
import eu.gabier.protocol.DaemonStatus;
import eu.gabier.protocol.FileDiagnostics;
import eu.gabier.protocol.FileDiagnostics.Diagnosis;
import eu.gabier.protocol.FileDiagnostics.Severity;
import eu.gabier.protocol.ImportSettings;
import eu.gabier.protocol.JdtlsProgress;
import eu.gabier.protocol.JdtlsState;
import eu.gabier.protocol.ProtocolVersion;
import eu.gabier.protocol.RpcResponse;
import eu.gabier.protocol.UserId;
import eu.gabier.shared.error.domain.ErrorStatus;
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
                        .scripted(Scenarios.ORGANIZE_IMPORTS_IN_ORDER)
                        .scripted(CleanUpScenarios.CLEAN_UP_A_CONCATENATION_INTO_A_TEXT_BLOCK)
                        .scripted(CleanUpScenarios.CLEAN_UP_A_FILE_THAT_IS_CLEAN)
                        .scripted(Scenarios.EXTRACT_VARIABLE)
                        .scripted(Scenarios.EXTRACT_VARIABLE_FROM_SOMETHING_THAT_IS_NOT_AN_EXPRESSION)
                        .scripted(Scenarios.EXTRACT_METHOD_THAT_USES_A_LOCAL_VARIABLE)
                        .scripted(Scenarios.EXTRACT_METHOD_FROM_HALF_A_STATEMENT)
                        .scripted(Scenarios.EXTRACT_CONSTANT)
                        .scripted(Scenarios.EXTRACT_CONSTANT_THAT_USES_A_LOCAL_VARIABLE)
                        .scripted(Scenarios.EXTRACT_FIELD)
                        .scripted(Scenarios.EXTRACT_FIELD_FROM_SOMETHING_THAT_IS_NOT_AN_EXPRESSION)
                        .scripted(Scenarios.MOVE_A_STATIC_NESTED_TYPE)
                        .scripted(Scenarios.MOVE_A_TOP_LEVEL_TYPE)
                        .scripted(PackageMoveScenarios.MOVE_A_CLASS_TO_A_NEW_PACKAGE)
                        .scripted(PackageMoveScenarios.MOVE_A_CLASS_TO_THE_PACKAGE_IT_IS_IN)
                        .scripted(Scenarios.INLINE_A_CONSTANT)
                        .scripted(Scenarios.INLINE_A_VARIABLE_ASSIGNED_TWICE)
                        .scripted(Scenarios.CREATE_A_CLASS)
                        .scripted(Scenarios.CREATE_A_TYPE_OUTSIDE_THE_PROJECT)
                        .scripted(Scenarios.REFERENCES_OF_A_TYPE)
                        .scripted(Scenarios.REFERENCES_WHERE_THERE_IS_NO_SYMBOL)
                        .scripted(CodeActionScenarios.CODE_ACTIONS_ON_A_TYPE_THAT_IS_NOT_IMPORTED)
                        .scripted(CodeActionScenarios.CODE_ACTIONS_WHERE_NOTHING_IS_OFFERED)
                        .scripted(CodeActionScenarios.APPLY_THE_IMPORT_OF_A_TYPE));
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
                        .isEqualTo(new DaemonStatus(
                                ProtocolVersion.CURRENT.toString(),
                                PID,
                                Duration.ofSeconds(90),
                                directory,
                                JdtlsState.READY));
            }
        }

        @Test
        void statusSaysWhichImportSettingsTheProjectFollowsReadFromItsFilesAtTheTimeOfTheCall() throws IOException {
            serving();

            try (var client = connect(socket())) {
                assertThat(imports(client)).isEqualTo(ImportSettings.DEFAULTS);

                Files.createDirectories(directory.resolve(".vscode"));
                Files.writeString(directory.resolve(".vscode/settings.json"), """
                    {"java.completion.importOrder": ["#", ""], "java.sources.organizeImports.starThreshold": 7}
                    """);

                assertThat(imports(client))
                        .isEqualTo(new ImportSettings(
                                new ImportSettings.Setting<>(List.of("#", ""), ImportSettings.Source.VSCODE),
                                new ImportSettings.Setting<>(7, ImportSettings.Source.VSCODE),
                                ImportSettings.DEFAULTS.staticStarThreshold()));
            }
        }

        private static ImportSettings imports(Client client) throws IOException {
            var answer = RpcResponse.parse(client.ask(STATUS_REQUEST));

            return DaemonStatus.of(((RpcResponse.Success) answer).result()).imports();
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

        @Test
        void statusSaysWhatJdtlsIsDoingWhileItStartsAndOnlyThen() throws IOException {
            var doing =
                    new JdtlsProgress(JdtlsProgress.Phase.IMPORTING, "Configure project :core", Duration.ofSeconds(47));
            project.doing(doing);
            serving();

            try (var client = connect(socket())) {
                project.in(JdtlsState.STARTING);
                assertThat(statusOf(client).jdtlsProgress()).contains(doing);

                for (var state : List.of(JdtlsState.NOT_STARTED, JdtlsState.READY, JdtlsState.FAILED)) {
                    project.in(state);
                    assertThat(statusOf(client).jdtlsProgress())
                            .as(state.text())
                            .isEmpty();
                }
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
        void cleanupAnswersWhatChangedAndWritesTheFileUnlessItIsADryRun() throws IOException {
            serving();
            CleanUpScenarios.CLEAN_UP_A_CONCATENATION_INTO_A_TEXT_BLOCK.writeProject(directory);
            var banner = directory.resolve("src/main/java/demo/Banner.java");
            var plain = directory.resolve("src/main/java/demo/Plain.java");

            try (var client = connect(socket())) {
                var dryRun = client.ask(cleanupRequest(banner, true));

                assertThat(dryRun).contains("\"modified\":[").contains("Hello");
                assertThat(Files.readString(banner)).contains("+ \"Welcome");

                var done = client.ask(cleanupRequest(banner, false));

                assertThat(done).contains("\"modified\":[");
                assertThat(Files.readString(banner)).doesNotContain("+ \"Welcome");
                assertThat(client.ask(cleanupRequest(plain, false))).contains("\"modified\":[]");
                assertThat(client.ask(STATUS_REQUEST)).contains("\"result\"");
            }
        }

        private static String cleanupRequest(Path file, boolean dryRun) {
            return """
                {"jsonrpc":"2.0","id":3,"method":"cleanup","params":{"file":"%s","cleanUps":"stringConcatToTextBlock","dryRun":"%s"}}
                """.formatted(file, dryRun);
        }

        @Test
        void extractVariableAnswersWhatChangedAndWritesTheFileUnlessItIsADryRun() throws IOException {
            serving();
            Scenarios.EXTRACT_VARIABLE.writeProject(directory);
            var greeter = directory.resolve("src/main/java/demo/Greeter.java");

            try (var client = connect(socket())) {
                var dryRun = client.ask(extractVariableRequest(greeter, "6", "25", "6", "47", true));

                assertThat(dryRun).contains("\"modified\":[").contains("String joined");
                assertThat(Files.readString(greeter)).doesNotContain("joined");

                var done = client.ask(extractVariableRequest(greeter, "6", "25", "6", "47", false));

                assertThat(done).contains("\"modified\":[");
                assertThat(Files.readString(greeter)).contains("String joined = greeting");
            }
        }

        @Test
        void aSelectionThatIsNotAnExpressionIsRefusedWithItsProblemObject() throws IOException {
            serving();
            Scenarios.EXTRACT_VARIABLE.writeProject(directory);
            var greeter = directory.resolve("src/main/java/demo/Greeter.java");

            try (var client = connect(socket())) {
                assertThat(client.ask(extractVariableRequest(greeter, "7", "8", "7", "14", false)))
                        .contains("\"key\":\"language-server-not-extractable\"");
                assertThat(Files.readString(greeter)).doesNotContain("joined");
            }
        }

        private static String extractVariableRequest(
                Path file, String startLine, String startColumn, String endLine, String endColumn, boolean dryRun) {
            return """
                {"jsonrpc":"2.0","id":3,"method":"extract-variable","params":{"file":"%s","startLine":"%s",\
                "startColumn":"%s","endLine":"%s","endColumn":"%s","name":"joined","dryRun":"%s"}}
                """.formatted(file, startLine, startColumn, endLine, endColumn, dryRun);
        }

        @Test
        void extractMethodAnswersWhatChangedAndWritesTheFileUnlessItIsADryRun() throws IOException {
            serving();
            Scenarios.EXTRACT_METHOD_THAT_USES_A_LOCAL_VARIABLE.writeProject(directory);
            var totals = directory.resolve("src/main/java/demo/Totals.java");

            try (var client = connect(socket())) {
                var dryRun = client.ask(extractMethodRequest(totals, "5", "8", "5", "46", "printCount", true));

                assertThat(dryRun).contains("\"modified\":[").contains("private void printCount");
                assertThat(Files.readString(totals)).doesNotContain("printCount");

                var done = client.ask(extractMethodRequest(totals, "5", "8", "5", "46", "printCount", false));

                assertThat(done).contains("\"modified\":[");
                assertThat(Files.readString(totals)).contains("        printCount(count);");
            }
        }

        @Test
        void aSelectionThatCannotBeExtractedIsRefusedWithItsProblemObject() throws IOException {
            serving();
            Scenarios.EXTRACT_METHOD_THAT_USES_A_LOCAL_VARIABLE.writeProject(directory);
            var totals = directory.resolve("src/main/java/demo/Totals.java");

            try (var client = connect(socket())) {
                assertThat(client.ask(extractMethodRequest(totals, "5", "30", "6", "20", "partial", false)))
                        .contains("\"key\":\"language-server-not-extractable\"");
                assertThat(Files.readString(totals)).doesNotContain("partial");
            }
        }

        private static String extractMethodRequest(
                Path file,
                String startLine,
                String startColumn,
                String endLine,
                String endColumn,
                String name,
                boolean dryRun) {
            return """
                {"jsonrpc":"2.0","id":3,"method":"extract-method","params":{"file":"%s","startLine":"%s",\
                "startColumn":"%s","endLine":"%s","endColumn":"%s","name":"%s","dryRun":"%s"}}
                """.formatted(file, startLine, startColumn, endLine, endColumn, name, dryRun);
        }

        @Test
        void extractConstantAnswersWhatChangedAndWritesTheFileUnlessItIsADryRun() throws IOException {
            serving();
            Scenarios.EXTRACT_CONSTANT.writeProject(directory);
            var totals = directory.resolve("src/main/java/demo/Totals.java");

            try (var client = connect(socket())) {
                var dryRun = client.ask(extractConstantRequest(totals, "5", "27", "5", "36", "COUNT_LABEL", true));

                assertThat(dryRun).contains("\"modified\":[").contains("private static final String COUNT_LABEL");
                assertThat(Files.readString(totals)).doesNotContain("COUNT_LABEL");

                var done = client.ask(extractConstantRequest(totals, "5", "27", "5", "36", "COUNT_LABEL", false));

                assertThat(done).contains("\"modified\":[");
                assertThat(Files.readString(totals)).contains("System.out.println(COUNT_LABEL + count);");
            }
        }

        @Test
        void anExpressionThatCannotBeAConstantIsRefusedWithItsProblemObject() throws IOException {
            serving();
            Scenarios.EXTRACT_CONSTANT_THAT_USES_A_LOCAL_VARIABLE.writeProject(directory);
            var totals = directory.resolve("src/main/java/demo/Totals.java");

            try (var client = connect(socket())) {
                assertThat(client.ask(extractConstantRequest(totals, "5", "27", "5", "44", "COUNT_LABEL", false)))
                        .contains("\"key\":\"language-server-not-extractable\"");
                assertThat(Files.readString(totals)).doesNotContain("COUNT_LABEL");
            }
        }

        private static String extractConstantRequest(
                Path file,
                String startLine,
                String startColumn,
                String endLine,
                String endColumn,
                String name,
                boolean dryRun) {
            return """
                {"jsonrpc":"2.0","id":3,"method":"extract-constant","params":{"file":"%s","startLine":"%s",\
                "startColumn":"%s","endLine":"%s","endColumn":"%s","name":"%s","dryRun":"%s"}}
                """.formatted(file, startLine, startColumn, endLine, endColumn, name, dryRun);
        }

        @Test
        void extractFieldAnswersWhatChangedAndWritesTheFileUnlessItIsADryRun() throws IOException {
            serving();
            Scenarios.EXTRACT_FIELD.writeProject(directory);
            var totals = directory.resolve("src/main/java/demo/Totals.java");

            try (var client = connect(socket())) {
                var dryRun = client.ask(extractFieldRequest(totals, "5", "27", "5", "36", "label", true));

                assertThat(dryRun).contains("\"modified\":[").contains("private String label;");
                assertThat(Files.readString(totals)).doesNotContain("label");

                var done = client.ask(extractFieldRequest(totals, "5", "27", "5", "36", "label", false));

                assertThat(done).contains("\"modified\":[");
                assertThat(Files.readString(totals)).contains("System.out.println(label + count);");
            }
        }

        @Test
        void aKeywordIsRefusedByExtractFieldWithItsProblemObject() throws IOException {
            serving();
            Scenarios.EXTRACT_FIELD_FROM_SOMETHING_THAT_IS_NOT_AN_EXPRESSION.writeProject(directory);
            var greeter = directory.resolve("src/main/java/demo/Greeter.java");

            try (var client = connect(socket())) {
                assertThat(client.ask(extractFieldRequest(greeter, "7", "8", "7", "14", "label", false)))
                        .contains("\"key\":\"language-server-not-extractable\"");
                assertThat(Files.readString(greeter)).doesNotContain("label");
            }
        }

        private static String extractFieldRequest(
                Path file,
                String startLine,
                String startColumn,
                String endLine,
                String endColumn,
                String name,
                boolean dryRun) {
            return """
                {"jsonrpc":"2.0","id":3,"method":"extract-field","params":{"file":"%s","startLine":"%s",\
                "startColumn":"%s","endLine":"%s","endColumn":"%s","name":"%s","dryRun":"%s"}}
                """.formatted(file, startLine, startColumn, endLine, endColumn, name, dryRun);
        }

        @Test
        void moveTypeToFileAnswersWhatWasCreatedAndChangedAndWritesTheFilesUnlessItIsADryRun() throws IOException {
            serving();
            Scenarios.MOVE_A_STATIC_NESTED_TYPE.writeProject(directory);
            var shapes = directory.resolve("src/main/java/demo/Shapes.java");
            var circle = directory.resolve("src/main/java/demo/Circle.java");

            try (var client = connect(socket())) {
                var dryRun = client.ask(moveTypeToFileRequest(shapes, "5", "24", true));

                assertThat(dryRun).contains("\"created\":[").contains("Circle.java");
                assertThat(circle).doesNotExist();

                var done = client.ask(moveTypeToFileRequest(shapes, "5", "24", false));

                assertThat(done).contains("\"created\":[").contains("\"modified\":[");
                assertThat(circle).content().startsWith("package demo;").contains("public class Circle {");
                assertThat(Files.readString(shapes)).doesNotContain("class Circle");
            }
        }

        @Test
        void aTypeThatCannotBeMovedIsRefusedWithItsProblemObjectAndNothingIsWritten() throws IOException {
            serving();
            Scenarios.MOVE_A_TOP_LEVEL_TYPE.writeProject(directory);
            var shapes = directory.resolve("src/main/java/demo/Shapes.java");
            var before = Files.readString(shapes);

            try (var client = connect(socket())) {
                assertThat(client.ask(moveTypeToFileRequest(shapes, "2", "13", false)))
                        .contains("\"key\":\"language-server-not-movable\"");
                assertThat(Files.readString(shapes)).isEqualTo(before);
            }
        }

        private static String moveTypeToFileRequest(Path file, String line, String column, boolean dryRun) {
            return """
                {"jsonrpc":"2.0","id":3,"method":"move-type-to-file","params":{"file":"%s","line":"%s",\
                "column":"%s","dryRun":"%s"}}
                """.formatted(file, line, column, dryRun);
        }

        @Test
        void moveAnswersWhatWasMovedAndMakesTheFolderOfThePackageUnlessItIsADryRun() throws IOException {
            serving();
            PackageMoveScenarios.MOVE_A_CLASS_TO_A_NEW_PACKAGE.writeProject(directory);
            var invoice = directory.resolve("src/main/java/demo/Invoice.java");
            var archived = directory.resolve("src/main/java/demo/archive/Invoice.java");

            try (var client = connect(socket())) {
                var dryRun = client.ask(moveRequest(invoice, "demo.archive", true));

                assertThat(dryRun).contains("\"renamed\":[").contains("demo/archive/Invoice.java");
                assertThat(directory.resolve("src/main/java/demo/archive")).doesNotExist();

                var done = client.ask(moveRequest(invoice, "demo.archive", false));

                assertThat(done).contains("\"renamed\":[").contains("\"modified\":[");
                assertThat(archived).content().startsWith("package demo.archive;");
                assertThat(invoice).doesNotExist();
            }
        }

        @Test
        void aMoveToTheSamePackageIsRefusedWithItsProblemObjectAndNothingIsWritten() throws IOException {
            serving();
            PackageMoveScenarios.MOVE_A_CLASS_TO_THE_PACKAGE_IT_IS_IN.writeProject(directory);
            var invoice = directory.resolve("src/main/java/demo/Invoice.java");
            var before = Files.readString(invoice);

            try (var client = connect(socket())) {
                assertThat(client.ask(moveRequest(invoice, "demo", false)))
                        .contains("\"key\":\"language-server-not-movable-to-package\"");
                assertThat(Files.readString(invoice)).isEqualTo(before);
            }
        }

        private static String moveRequest(Path file, String target, boolean dryRun) {
            return """
                {"jsonrpc":"2.0","id":3,"method":"move","params":{"files":"%s","package":"%s","dryRun":"%s"}}
                """.formatted(file, target, dryRun);
        }

        @Test
        void inlineAnswersWhatWasChangedAndWritesTheFilesUnlessItIsADryRun() throws IOException {
            serving();
            Scenarios.INLINE_A_CONSTANT.writeProject(directory);
            var prices = directory.resolve("src/main/java/demo/Prices.java");

            try (var client = connect(socket())) {
                var dryRun = client.ask(inlineRequest(prices, "3", "21", true));

                assertThat(dryRun)
                        .contains("\"modified\":[")
                        .contains("Prices.java")
                        .contains("Checkout.java");
                assertThat(Files.readString(prices)).contains("static final int VAT");

                var done = client.ask(inlineRequest(prices, "3", "21", false));

                assertThat(done).contains("\"modified\":[");
                assertThat(Files.readString(prices)).doesNotContain("VAT");
            }
        }

        @Test
        void aPositionThatCannotBeInlinedIsRefusedWithItsProblemObjectAndNothingIsWritten() throws IOException {
            serving();
            Scenarios.INLINE_A_VARIABLE_ASSIGNED_TWICE.writeProject(directory);
            var prices = directory.resolve("src/main/java/demo/Prices.java");
            var before = Files.readString(prices);

            try (var client = connect(socket())) {
                assertThat(client.ask(inlineRequest(prices, "20", "12", false)))
                        .contains("\"key\":\"language-server-not-inlinable\"");
                assertThat(Files.readString(prices)).isEqualTo(before);
            }
        }

        @Test
        void codeActionsListsWhatTheRangeOffersAndWritesNothing() throws IOException {
            serving();
            CodeActionScenarios.CODE_ACTIONS_ON_A_TYPE_THAT_IS_NOT_IMPORTED.writeProject(directory);
            var offers = directory.resolve("src/main/java/demo/Offers.java");
            var before = Files.readString(offers);

            try (var client = connect(socket())) {
                assertThat(client.ask(rangeRequest("actions", offers, "3", "4", "3", "8", "")))
                        .contains("\"actions\":[{\"kind\":\"quickfix\",\"title\":\"Import 'List' (java.util)\"}]");
                assertThat(client.ask(rangeRequest("actions", offers, "6", "0", "6", "0", "")))
                        .contains("\"actions\":[]");
                assertThat(Files.readString(offers)).isEqualTo(before);
            }
        }

        @Test
        void applyCodeActionAnswersWhatWasChangedAndWritesTheFileUnlessItIsADryRun() throws IOException {
            serving();
            CodeActionScenarios.CODE_ACTIONS_ON_A_TYPE_THAT_IS_NOT_IMPORTED.writeProject(directory);
            var offers = directory.resolve("src/main/java/demo/Offers.java");
            var title = ",\"title\":\"Import 'List' (java.util)\"";

            try (var client = connect(socket())) {
                var dryRun = client.ask(
                        rangeRequest("apply-action", offers, "3", "4", "3", "8", title + ",\"dryRun\":\"true\""));

                assertThat(dryRun).contains("\"modified\":[").contains("Offers.java");
                assertThat(Files.readString(offers)).doesNotContain("import java.util.List");

                var done = client.ask(
                        rangeRequest("apply-action", offers, "3", "4", "3", "8", title + ",\"dryRun\":\"false\""));

                assertThat(done).contains("\"modified\":[");
                assertThat(Files.readString(offers)).contains("import java.util.List;");
            }
        }

        @Test
        void aTitleTheRangeDoesNotOfferIsRefusedWithItsProblemObjectAndNothingIsWritten() throws IOException {
            serving();
            CodeActionScenarios.CODE_ACTIONS_ON_A_TYPE_THAT_IS_NOT_IMPORTED.writeProject(directory);
            var offers = directory.resolve("src/main/java/demo/Offers.java");
            var before = Files.readString(offers);

            try (var client = connect(socket())) {
                assertThat(client.ask(rangeRequest(
                                "apply-action",
                                offers,
                                "3",
                                "4",
                                "3",
                                "8",
                                ",\"title\":\"Nope\",\"dryRun\":\"false\"")))
                        .contains("\"key\":\"editing-no-such-code-action\"");
                assertThat(Files.readString(offers)).isEqualTo(before);
            }
        }

        private static String rangeRequest(
                String method,
                Path file,
                String startLine,
                String startColumn,
                String endLine,
                String endColumn,
                String more) {
            return """
                {"jsonrpc":"2.0","id":3,"method":"%s","params":{"file":"%s","startLine":"%s","startColumn":"%s",\
                "endLine":"%s","endColumn":"%s"%s}}
                """.formatted(method, file, startLine, startColumn, endLine, endColumn, more);
        }

        @Test
        void createAnswersWhatWasCreatedAndWritesTheFileUnlessItIsADryRun() throws IOException {
            serving();
            Scenarios.CREATE_A_CLASS.writeProject(directory);
            var widget = directory.resolve("src/main/java/demo/Widget.java");

            try (var client = connect(socket())) {
                var dryRun = client.ask(createRequest(widget, "class", true));

                assertThat(dryRun).contains("\"created\":[").contains("Widget.java");
                assertThat(widget).doesNotExist();

                var done = client.ask(createRequest(widget, "class", false));

                assertThat(done).contains("\"created\":[");
                assertThat(widget).content().isEqualTo("package demo;\n\npublic class Widget {\n\n}\n");
            }
        }

        @Test
        void aFileOutsideTheProjectIsRefusedByCreateWithItsProblemObject() throws IOException {
            serving();
            Scenarios.CREATE_A_TYPE_OUTSIDE_THE_PROJECT.writeProject(directory);
            var outside = directory.resolve("../outside/Elsewhere.java").normalize();

            try (var client = connect(socket())) {
                assertThat(client.ask(createRequest(outside, "class", false)))
                        .contains("\"key\":\"language-server-file-outside-project\"");
            }
        }

        @Test
        void referencesAnswersTheUsesWithTheirLinesOfSourceAndWritesNothing() throws IOException {
            serving();
            Scenarios.REFERENCES_OF_A_TYPE.writeProject(directory);
            var ledger = directory.resolve("src/main/java/demo/Ledger.java");
            var before = Files.readString(ledger);

            try (var client = connect(socket())) {
                var answer = client.ask(referencesRequest(ledger, "2", "13", false));

                assertThat(answer)
                        .contains("\"declarations\":[]")
                        .contains("\"source\":\"import demo.Ledger;\"")
                        .contains("Bank.java")
                        .contains("LedgerTest.java");
                assertThat(Files.readString(ledger)).isEqualTo(before);
            }
        }

        @Test
        void aPositionOnNothingIsRefusedByReferencesWithItsProblemObject() throws IOException {
            serving();
            Scenarios.REFERENCES_WHERE_THERE_IS_NO_SYMBOL.writeProject(directory);
            var ledger = directory.resolve("src/main/java/demo/Ledger.java");

            try (var client = connect(socket())) {
                assertThat(client.ask(referencesRequest(ledger, "4", "0", false)))
                        .contains("\"key\":\"language-server-no-such-symbol\"");
            }
        }

        private static String referencesRequest(Path file, String line, String column, boolean includeDeclaration) {
            return """
                {"jsonrpc":"2.0","id":3,"method":"references","params":{"file":"%s","line":"%s",\
                "column":"%s","includeDeclaration":"%s"}}
                """.formatted(file, line, column, includeDeclaration);
        }

        private static String createRequest(Path file, String kind, boolean dryRun) {
            return """
                {"jsonrpc":"2.0","id":3,"method":"create","params":{"file":"%s","kind":"%s","dryRun":"%s"}}
                """.formatted(file, kind, dryRun);
        }

        private static String inlineRequest(Path file, String line, String column, boolean dryRun) {
            return """
                {"jsonrpc":"2.0","id":3,"method":"inline","params":{"file":"%s","line":"%s",\
                "column":"%s","dryRun":"%s"}}
                """.formatted(file, line, column, dryRun);
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
