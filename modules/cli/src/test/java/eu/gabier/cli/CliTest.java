package eu.gabier.cli;

import eu.gabier.protocol.JsonRpc;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.io.TempDir;

/**
 * What the tests of the cli share: a folder of their own, the standard output and error of a run, a run of {@link
 * Gabier}, the socket a {@link ScriptedDaemon} listens on and the usage errors that every test expects the same way.
 */
abstract class CliTest {

    static final String FOR_A_TASK = "Not sure which command? Ask for the task: gabier help --for=\"<task>\"";
    static final String FOR_A_TASK_AS_JSON =
            "Not sure which command? Ask for the task: gabier help --for=\\\"<task>\\\"";

    private static final String USAGE_DETAIL = "Unsupported arguments; run gabier help for the commands, "
            + "and gabier help <command> for the usage of one";

    @TempDir
    Path directory;

    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final ByteArrayOutputStream err = new ByteArrayOutputStream();

    static String usageText(String arguments) {
        return "Usage error: " + USAGE_DETAIL + " (usage-error; arguments=" + arguments + ")" + System.lineSeparator();
    }

    static String usageJson(String arguments) {
        return "{\"type\":\"urn:gabier:error:usage-error\",\"title\":\"Usage error\",\"detail\":\"" + USAGE_DETAIL
                + "\",\"key\":\"usage-error\",\"parameters\":{\"arguments\":\"" + arguments + "\"}}"
                + System.lineSeparator();
    }

    /** The usage error of a first word that names no command: the problem, then the line that points to the guide. */
    static String unknownCommandText(String arguments) {
        return "Usage error: " + USAGE_DETAIL + " (usage-error; arguments=" + arguments + ")" + System.lineSeparator()
                + FOR_A_TASK + System.lineSeparator();
    }

    static String unknownCommandJson(String arguments) {
        var problem = usageJson(arguments).stripTrailing();

        return problem.substring(0, problem.length() - 1) + ",\"help\":\"" + FOR_A_TASK_AS_JSON + "\"}"
                + System.lineSeparator();
    }

    static PrintStream printStream(ByteArrayOutputStream stream) {
        return new PrintStream(stream, true, StandardCharsets.UTF_8);
    }

    int run(String... args) {
        return run(Map.of(), args);
    }

    int run(Map<String, String> environment, String... args) {
        return Gabier.run(args, environment, printStream(out), printStream(err));
    }

    String out() {
        return out.toString(StandardCharsets.UTF_8);
    }

    String err() {
        return err.toString(StandardCharsets.UTF_8);
    }

    Path socket() {
        return directory.resolve("gabier.sock");
    }

    int runWithSocket(String... args) {
        return run(Map.of("GABIER_SOCKET", socket().toString()), args);
    }

    /** A daemon on the socket of the test that answers every request with this result. */
    ScriptedDaemon daemonAnswering(String result) throws IOException {
        return new ScriptedDaemon(socket(), JsonRpc.success("1", result));
    }
}
