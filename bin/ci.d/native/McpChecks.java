import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/** The native cli as an MCP server over a pipe, alone and against a daemon that holds a call. */
final class McpChecks extends Support {

    /**
     * 0 when {@code gabier mcp} of the build answers over a pipe, in an empty directory with an empty environment (so no
     * daemon and no locale: the encoding of the platform may be ASCII): a {@code tools/list} of the modern era gives the
     * tools of the operations with their schemas and the hint of the read-only ones, none for {@code start} and {@code
     * stop}, and the tool {@code guide}, and nothing but the protocol is written on the standard output; {@code
     * server/discover} gives the versions and the instructions (a phrase of the resource {@code mcp-instructions.md},
     * which proves it is in the image); the handshake of the earlier era is answered, with the instructions, and a {@code
     * tools/list} after it gives the same tools; a {@code tools/call} of {@code guide} answers the cleanup for the text
     * block task with the command written as the tool, and a blank task is a failed call; the daemon that the handshake
     * starts in the background cannot be started here, which is one line on the standard error and fails no answer; the
     * id of a request is given back as it came, in UTF-8; a relative path is refused as an invalid value; a line that is
     * not JSON is a parse error and the server goes on.
     */
    static int mcp(Path binary, Path directory) throws IOException, InterruptedException {
        String meta = "\"_meta\":{\"io.modelcontextprotocol/protocolVersion\":\"2026-07-28\","
                + "\"io.modelcontextprotocol/clientCapabilities\":{}}";
        String input = String.join(
                        "\n",
                        "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\",\"params\":{" + meta + "}}",
                        "{\"jsonrpc\":\"2.0\",\"id\":\"café\",\"method\":\"server/discover\",\"params\":{" + meta
                                + "}}",
                        "{not json",
                        "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-11-25\"}}",
                        "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}",
                        "{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"tools/list\"}",
                        "{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"tools/call\",\"params\":{\"name\":\"guide\","
                                + "\"arguments\":{\"task\":\"turn this string concatenation into a text block\"},"
                                + meta + "}}",
                        "{\"jsonrpc\":\"2.0\",\"id\":6,\"method\":\"tools/call\",\"params\":{\"name\":\"guide\","
                                + "\"arguments\":{\"task\":\"  \"}," + meta + "}}",
                        "{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"tools/call\",\"params\":{\"name\":\"diagnostics\","
                                + "\"arguments\":{\"files\":[\"/nowhere/A.java\"]}," + meta + "}}",
                        "{\"jsonrpc\":\"2.0\",\"id\":8,\"method\":\"tools/call\",\"params\":{\"name\":\"diagnostics\","
                                + "\"arguments\":{\"files\":[\"\"]}," + meta + "}}",
                        "{\"jsonrpc\":\"2.0\",\"id\":9,\"method\":\"tools/call\",\"params\":{\"name\":\"diagnostics\","
                                + "\"arguments\":{\"files\":[\"src/A.java\"]}," + meta + "}}")
                + "\n";
        Run run = runBinaryReading(binary, directory, List.of(), input, "mcp");
        List<String> answers = run.out().lines().toList();
        System.out.println(
                "gabier mcp -> " + answers.size() + " answers (exit " + run.exit() + "), stderr \"" + run.err() + "\"");

        if (answers.size() != 10) {
            return 1;
        }
        // The handshakes started the daemon in the background; there is none to start here (an empty environment, no
        // gabier-daemon next to the binary), which is told on the standard error and fails no answer. The guide and a
        // call whose values are wrong (id 8, an empty file) are answered at once, in the order they came; a call that
        // asks the daemon (id 7) waits for that start and ends as a failed start, whenever it ends among them: the
        // answers of the calls are found by their id. The line on the standard error is there when the server ends.
        String asked = answerOf(answers, "7");
        String empty = answerOf(answers, "8");
        // A relative path is refused at once, whatever the folder the server runs in: it does not know the session's.
        String relative = answerOf(answers, "9");
        boolean warmUpFailed = run.err().lines().count() == 1
                && run.err().startsWith("gabier mcp: the daemon could not be started: ")
                && asked.contains("\"isError\":true")
                && asked.contains("\"key\":\"daemon-start-failed\"")
                && empty.contains("\"isError\":true")
                && empty.contains("\"key\":\"operation-invalid-values\"")
                && empty.contains("must not have an empty element")
                && relative.contains("\"isError\":true")
                && relative.contains("\"key\":\"operation-invalid-values\"")
                && relative.contains("must be an absolute path");
        // The instructions are a resource of the image: a phrase of mcp-instructions.md proves it is in it.
        String instructions = "Use its tools instead of editing Java as text";
        boolean instructed =
                answers.get(1).contains("\"instructions\":\"Gabier changes Java through the language server")
                        && answers.get(1).contains(instructions)
                        && answers.get(3).contains(instructions);
        boolean guided = answers.get(5)
                        .startsWith(
                                "{\"jsonrpc\":\"2.0\",\"id\":5,\"result\":{\"resultType\":\"complete\",\"content\":[{\"type\":\"text\",\"text\":\"For"
                                    + " \\\"turn this string concatenation into a text block\\\":")
                && answers.get(5).contains("Ask: `cleanup dryRun=true <file> stringConcatToTextBlock`")
                && !answers.get(5).contains("gabier cleanup")
                && !answers.get(5).contains("--dry-run")
                && answers.get(5).contains("\"isError\":false")
                && answers.get(6).contains("\"isError\":true")
                && answers.get(6).contains("\"key\":\"operation-invalid-values\"");
        boolean listed = answers.get(0)
                        .startsWith(
                                "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"resultType\":\"complete\",\"tools\":[{\"name\":\"status\"")
                && answers.get(0).contains("\"name\":\"diagnostics\"")
                && answers.get(0).contains("\"name\":\"apply-action\"")
                && answers.get(0).contains("\"name\":\"guide\"")
                && answers.get(0).contains("\"annotations\":{\"readOnlyHint\":true}")
                && answers.get(0).contains("\"minimum\":1")
                && !answers.get(0).contains("\"name\":\"start\"")
                && !answers.get(0).contains("\"name\":\"stop\"");
        boolean discovered = answers.get(1).startsWith("{\"jsonrpc\":\"2.0\",\"id\":\"café\",\"result\":{")
                && answers.get(1).contains("\"supportedVersions\":[\"2026-07-28\",\"2025-11-25\"]");
        boolean parseError = answers.get(2).contains("\"id\":null,\"error\":{\"code\":-32700");
        boolean earlier = answers.get(3).contains("\"id\":3,\"result\":{\"protocolVersion\":\"2025-11-25\"")
                && answers.get(4)
                        .startsWith("{\"jsonrpc\":\"2.0\",\"id\":4,\"result\":{\"tools\":[{\"name\":\"status\"")
                && !answers.get(4).contains("resultType");

        return run.exit() == 0 && warmUpFailed && instructed && guided && listed && discovered && parseError && earlier
                ? 0
                : 1;
    }

    /**
     * 0 when {@code gabier mcp} of the build goes on answering while a call waits for the daemon, which no pipe of a whole
     * input can show, so the binary is driven line by line. The daemon is a socket served by this script: it answers
     * {@code status} at once, with jdtls importing the project, and holds every other request until the script lets it
     * go, as the real one does while jdtls starts. A {@code diagnostics} call is sent, then a {@code ping} and a
     * {@code status} call: both must be answered while the call is still held (the answer of {@code status} says jdtls
     * is importing, which is the state of the daemon read on the side); then the daemon answers, and the call ends with
     * its result, no line more is written, and the server ends with the input.
     */
    static int mcpWaiting(Path binary, Path directory) throws IOException, InterruptedException {
        Path folder = Files.createTempDirectory(directory, "waiting");
        Path socket = folder.resolve("s.sock");
        CountDownLatch release = new CountDownLatch(1);
        String status =
                "{\"version\":\"0.1\",\"pid\":4242,\"uptimeMillis\":20000,\"project\":\"/work\",\"jdtls\":\"starting\","
                    + "\"imports\":{\"importOrder\":{\"value\":[\"java\"],\"source\":\"default\"},"
                    + "\"starThreshold\":{\"value\":99,\"source\":\"default\"},"
                    + "\"staticStarThreshold\":{\"value\":99,\"source\":\"default\"}},\"jdtlsProgress\":{\"phase\":\"importing\",\"message\":\"Build"
                    + " model\",\"spentMillis\":15000}}";
        String meta = "\"_meta\":{\"io.modelcontextprotocol/protocolVersion\":\"2026-07-28\","
                + "\"io.modelcontextprotocol/clientCapabilities\":{}}";
        try (ServerSocketChannel listener = ServerSocketChannel.open(StandardProtocolFamily.UNIX)) {
            listener.bind(UnixDomainSocketAddress.of(socket));
            Thread.ofVirtual().start(() -> holdingDaemon(listener, status, release));
            ProcessBuilder builder = new ProcessBuilder(binary.toString(), "mcp").directory(directory.toFile());
            builder.environment().clear();
            builder.environment().put("GABIER_SOCKET", socket.toString());
            builder.redirectError(ProcessBuilder.Redirect.DISCARD);
            Process process = builder.start();
            BlockingQueue<String> lines = new LinkedBlockingQueue<>();
            Thread.ofVirtual().start(() -> {
                try (var reader =
                        new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                        lines.add(line);
                    }
                } catch (IOException closed) {
                    // The server is gone: the lines it wrote are all there will be.
                }
            });
            var stdin = process.getOutputStream();
            stdin.write(("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"diagnostics\","
                            + "\"arguments\":{\"files\":[\"/work/A.java\"]}," + meta + "}}\n"
                            + "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"ping\"}\n"
                            + "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":{\"name\":\"status\","
                            + "\"arguments\":{}," + meta + "}}\n")
                    .getBytes(StandardCharsets.UTF_8));
            stdin.flush();
            String first = lines.poll(20, TimeUnit.SECONDS);
            String second = lines.poll(20, TimeUnit.SECONDS);
            boolean answeredWhileWaiting = first != null && second != null && lines.isEmpty();
            List<String> meanwhile = new ArrayList<>(Arrays.asList(String.valueOf(first), String.valueOf(second)));
            String ping = answerOf(meanwhile, "2");
            String reported = answerOf(meanwhile, "3");
            release.countDown();
            String call = lines.poll(20, TimeUnit.SECONDS);
            stdin.close();
            boolean ended = process.waitFor(20, TimeUnit.SECONDS);
            if (!ended) {
                process.destroyForcibly();
            }
            System.out.println("gabier mcp (a call that waits) -> ping \"" + ping + "\", status \"" + reported
                    + "\", call \"" + call + "\"");
            boolean ok = answeredWhileWaiting
                    && ended
                    && process.exitValue() == 0
                    && ping.equals("{\"jsonrpc\":\"2.0\",\"id\":2,\"result\":{}}")
                    && reported.contains("\"isError\":false")
                    && reported.contains("jdtls starting (importing the project for 15s: Build model)")
                    && call != null
                    && call.startsWith("{\"jsonrpc\":\"2.0\",\"id\":1,")
                    && call.contains("\"isError\":false")
                    && call.contains("no problems")
                    && lines.poll(1, TimeUnit.SECONDS) == null;
            if (!ok) {
                System.out.println(
                        "FAIL: gabier mcp must answer ping and status while a call waits for the daemon, then the"
                                + " call, then end");
            }
            return ok ? 0 : 1;
        } finally {
            release.countDown();
            deleteTree(folder);
        }
    }

    /** The daemon of {@code mcpWaiting}: {@code status} at once, any other request once the latch is released. */
    static void holdingDaemon(ServerSocketChannel listener, String status, CountDownLatch release) {
        try {
            while (true) {
                SocketChannel client = listener.accept();
                Thread.ofVirtual().start(() -> {
                    try (client) {
                        var reader = new BufferedReader(
                                new InputStreamReader(Channels.newInputStream(client), StandardCharsets.UTF_8));
                        String request = reader.readLine();
                        if (request == null) {
                            return;
                        }
                        boolean asksStatus = request.contains("\"method\":\"status\"");
                        if (!asksStatus) {
                            release.await();
                        }
                        String result = asksStatus ? status : "{\"files\":[]}";
                        String response = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":" + result + "}\n";
                        client.write(ByteBuffer.wrap(response.getBytes(StandardCharsets.UTF_8)));
                    } catch (IOException | InterruptedException gone) {
                        // The cli went away, or the stage is over.
                    }
                });
            }
        } catch (IOException closed) {
            // The stage is over.
        }
    }
}
