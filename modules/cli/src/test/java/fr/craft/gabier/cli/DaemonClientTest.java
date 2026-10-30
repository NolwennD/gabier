package fr.craft.gabier.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import fr.craft.gabier.protocol.RpcResponse;
import fr.craft.gabier.shared.error.domain.ErrorStatus;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The client over streams: the sockets are in {@code GabierTest}. */
class DaemonClientTest {

    private static final String RESULT = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"pid\":7}}\n";

    @TempDir
    Path directory;

    private final ByteArrayOutputStream written = new ByteArrayOutputStream();
    private boolean closed;

    private DaemonClient client(String answer) {
        return new DaemonClient(
                new ByteArrayInputStream(answer.getBytes(StandardCharsets.UTF_8)), written, () -> closed = true);
    }

    private static InputStream failingInput() {
        return new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("Connection reset");
            }
        };
    }

    private static OutputStream failingOutput() {
        return new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                throw new IOException("Broken pipe");
            }
        };
    }

    @Nested
    class Call {

        @Test
        void writesOneLineWithTheMethodAndReadsTheResponse() {
            var response = client(RESULT).call("status");

            assertThat(written.toString(StandardCharsets.UTF_8))
                    .isEqualTo("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"status\"}\n");
            assertThat(response).isInstanceOf(RpcResponse.Success.class);
        }

        @Test
        void writesTheParametersNextToTheMethodSortedByName() {
            client(RESULT).call("diagnostics", Map.of("file", "src/A.java", "bundle", "a\"b"));

            assertThat(written.toString(StandardCharsets.UTF_8)).isEqualTo("""
                {"jsonrpc":"2.0","id":1,"method":"diagnostics","params":{"bundle":"a\\"b","file":"src/A.java"}}
                """);
        }

        @Test
        void sendsTheParametersToTheDaemonOnItsSocket() throws IOException {
            var socket = directory.resolve("gabier.sock");

            try (var daemon = new ScriptedDaemon(socket, RESULT.strip());
                    var client = DaemonClient.connect(socket)) {
                var response = client.call("diagnostics", Map.of("file", "src/A.java"));

                assertThat(response).isInstanceOf(RpcResponse.Success.class);
                assertThat(daemon.requests()).containsExactly("""
                    {"jsonrpc":"2.0","id":1,"method":"diagnostics","params":{"file":"src/A.java"}}\
                    """);
            }
        }

        @Test
        void failsWhenTheDaemonHangsUpWithoutAnswering() {
            var failure =
                    catchThrowableOfType(DaemonException.class, () -> client("").call("status"));

            assertThat(failure.key()).isEqualTo(CliErrorKey.DAEMON_CONNECTION_FAILED);
            assertThat(failure.status()).isEqualTo(ErrorStatus.INTERNAL_SERVER_ERROR);
            assertThat(failure.getMessage()).contains("hung up");
        }

        @Test
        void failsWhenTheConnectionBreaksWhileReading() {
            var client = new DaemonClient(failingInput(), written, () -> {});

            var failure = catchThrowableOfType(DaemonException.class, () -> client.call("status"));

            assertThat(failure.key()).isEqualTo(CliErrorKey.DAEMON_CONNECTION_FAILED);
            assertThat(failure.getCause()).isInstanceOf(IOException.class);
        }

        @Test
        void failsWhenTheRequestCannotBeWritten() {
            var client = new DaemonClient(new ByteArrayInputStream(new byte[0]), failingOutput(), () -> {});

            var failure = catchThrowableOfType(DaemonException.class, () -> client.call("status"));

            assertThat(failure.key()).isEqualTo(CliErrorKey.DAEMON_CONNECTION_FAILED);
        }
    }

    @Nested
    class AwaitEnd {

        @Test
        void returnsOnceTheDaemonHasClosedTheConnection() {
            client("").awaitEnd();
        }

        @Test
        void ignoresWhatTheDaemonSaysBeforeItCloses() {
            client("one\ntwo\n").awaitEnd();
        }

        @Test
        void failsWhenTheConnectionBreaks() {
            var client = new DaemonClient(failingInput(), written, () -> {});

            var failure = catchThrowableOfType(DaemonException.class, client::awaitEnd);

            assertThat(failure.key()).isEqualTo(CliErrorKey.DAEMON_CONNECTION_FAILED);
        }
    }

    @Nested
    class Close {

        @Test
        void closesTheConnection() {
            var client = client("");

            client.close();

            assertThat(closed).isTrue();
        }

        @Test
        void saysNothingWhenTheConnectionCannotBeClosed() {
            new DaemonClient(new ByteArrayInputStream(new byte[0]), written, () -> {
                        throw new IOException("already gone");
                    })
                    .close();
        }
    }
}
