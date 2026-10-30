package fr.craft.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConnectionTest {

    private final List<String> stoppedBy = new ArrayList<>();

    private final RequestHandler handler =
            new RequestHandler(List.of(new StopMethod(() -> stoppedBy.add("stop")), new EchoMethod()));

    private static String request(int id, String method) {
        return "{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"method\":\"" + method + "\"}\n";
    }

    private static InputStream input(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    private static List<String> lines(ByteArrayOutputStream output) {
        return output.toString(StandardCharsets.UTF_8).lines().toList();
    }

    @Test
    void answersEachRequestWithOneLineInOrder() {
        var output = new ByteArrayOutputStream();

        Connection.serve(input(request(1, "echo") + request(2, "nope") + request(3, "echo")), output, handler);

        assertThat(lines(output)).hasSize(3);
        assertThat(lines(output).get(0)).contains("\"id\":1").contains("\"result\"");
        assertThat(lines(output).get(1)).contains("\"id\":2").contains("\"error\"");
        assertThat(lines(output).get(2)).contains("\"id\":3").contains("\"result\"");
    }

    @Test
    void answersAMalformedLineAndGoesOn() {
        var output = new ByteArrayOutputStream();

        Connection.serve(input("garbage\n" + request(1, "echo")), output, handler);

        assertThat(lines(output)).hasSize(2);
        assertThat(lines(output).get(0)).contains("\"code\":-32700");
        assertThat(lines(output).get(1)).contains("\"result\"");
    }

    @Test
    void refusesALineOverTheLimitWithAProblemObjectAndAnswersTheNextRequest() {
        var output = new ByteArrayOutputStream();
        var tooLong = "x".repeat(LineReader.MAX_LINE_BYTES + 1) + "\n";

        Connection.serve(input(tooLong + request(1, "echo")), output, handler);

        assertThat(lines(output)).hasSize(2);
        assertThat(lines(output).get(0))
                .contains("\"id\":null")
                .contains("\"key\":\"line-too-long\"")
                .contains("\"limit\":\"" + LineReader.MAX_LINE_BYTES + "\"");
        assertThat(lines(output).get(1)).contains("\"id\":1").contains("\"result\"");
    }

    @Test
    void refusesAParameterNameOverTheLimitForTheLengthOfTheLineAndAnswersTheNextRequest() {
        var output = new ByteArrayOutputStream();
        var hostile = """
            {"jsonrpc":"2.0","id":4,"method":"echo","params":{"%s":"x"}}
            """.formatted("n".repeat(LineReader.MAX_LINE_BYTES));

        Connection.serve(input(hostile + request(1, "echo")), output, handler);

        assertThat(lines(output)).hasSize(2);
        assertThat(lines(output).get(0))
                .contains("\"id\":null")
                .contains("\"code\":-32000")
                .contains("\"key\":\"line-too-long\"");
        assertThat(lines(output).get(1)).contains("\"id\":1").contains("\"result\"");
    }

    @Test
    void refusesAParameterValueOverTheLimitForTheLengthOfTheLineAndAnswersTheNextRequest() {
        var output = new ByteArrayOutputStream();
        var hostile = """
            {"jsonrpc":"2.0","id":4,"method":"echo","params":{"file":"%s"}}
            """.formatted("v".repeat(LineReader.MAX_LINE_BYTES));

        Connection.serve(input(hostile + request(1, "echo")), output, handler);

        assertThat(lines(output)).hasSize(2);
        assertThat(lines(output).get(0)).contains("\"key\":\"line-too-long\"");
        assertThat(lines(output).get(1)).contains("\"id\":1").contains("\"result\"");
    }

    @Test
    void readsAParameterNameThatFillsTheLineToTheLimitAndRefusesItAsUnknown() {
        var output = new ByteArrayOutputStream();
        var frame = "{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"echo\",\"params\":{\"\":\"x\"}}";
        var name = "n".repeat(LineReader.MAX_LINE_BYTES - frame.length());

        Connection.serve(
                input(frame.replace("\"\":", "\"" + name + "\":") + "\n" + request(1, "echo")), output, handler);

        assertThat(lines(output)).hasSize(2);
        assertThat(lines(output).get(0)).contains("\"id\":4").contains("\"code\":-32602");
        assertThat(lines(output).get(0)).contains("\"key\":\"unknown-parameter\"");
        assertThat(lines(output).get(1)).contains("\"id\":1").contains("\"result\"");
    }

    @Test
    void readsALineOfExactlyTheLimit() {
        var output = new ByteArrayOutputStream();
        var request = request(1, "echo").stripTrailing();
        var padded = " ".repeat(LineReader.MAX_LINE_BYTES - request.length()) + request + "\n";

        Connection.serve(input(padded), output, handler);

        assertThat(lines(output)).hasSize(1);
        assertThat(lines(output).get(0)).contains("\"result\"");
    }

    @Test
    void doesNotAnswerARequestThatWasCutShort() {
        var output = new ByteArrayOutputStream();

        Connection.serve(input("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"stop\"}"), output, handler);

        assertThat(output.size()).isZero();
        assertThat(stoppedBy).isEmpty();
    }

    @Test
    void runsWhatFollowsTheReplyOnceItIsWritten() {
        var output = new ByteArrayOutputStream() {
            @Override
            public void flush() {
                stoppedBy.add("flushed");
            }
        };

        Connection.serve(input(request(1, "stop")), output, handler);

        assertThat(stoppedBy).containsExactly("flushed", "stop");
    }

    @Test
    void endsWhenTheClientIsGoneBeforeTheReply() {
        var gone = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                throw new IOException("Broken pipe");
            }
        };

        Connection.serve(input(request(1, "stop") + request(2, "stop")), gone, handler);

        assertThat(stoppedBy).isEmpty();
    }

    @Test
    void endsWhenTheConnectionFailsWhileReading() {
        var resetting = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("Connection reset");
            }
        };

        Connection.serve(resetting, new ByteArrayOutputStream(), handler);
    }

    @Test
    void closesBothStreams() {
        var closed = new ArrayList<String>();
        var in = new ByteArrayInputStream(new byte[0]) {
            @Override
            public void close() {
                closed.add("in");
            }
        };
        var out = new ByteArrayOutputStream() {
            @Override
            public void close() {
                closed.add("out");
            }
        };

        Connection.serve(in, out, handler);

        assertThat(closed).containsExactlyInAnyOrder("in", "out");
    }

    private record EchoMethod() implements DaemonMethod {

        @Override
        public String name() {
            return "echo";
        }

        @Override
        public Outcome call(Map<String, String> params) {
            return Outcome.of("{}");
        }
    }
}
