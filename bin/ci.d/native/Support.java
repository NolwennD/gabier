import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** What every check of the native stage shares: the constants, the run of the binary and the files of a stage. */
abstract class Support {

    static final long TIMEOUT_SECONDS = 60;

    /** A semantic version, as a regular expression with no group of its own: the one the build is given, else 0.0.0-dev. */
    static final String VERSION =
            "(?:0|[1-9]\\d*)\\.(?:0|[1-9]\\d*)\\.(?:0|[1-9]\\d*)(?:-[0-9A-Za-z.-]+)?(?:\\+[0-9A-Za-z.-]+)?";

    static final Pattern SEMANTIC_VERSION_LINE = Pattern.compile("gabier " + VERSION);

    /** The whole problem object of {@code gabier --json --bogus}, member by member, on one line. */
    static final String USAGE_ERROR_JSON = """
        {"type":"urn:gabier:error:usage-error","title":"Usage error",\
        "detail":"Unsupported arguments; run gabier help for the commands, and gabier help <command> for the usage of one",\
        "key":"usage-error","parameters":{"arguments":"--bogus"},\
        "help":"Not sure which command? Ask for the task: gabier help --for=\\"<task>\\""}\
        """;

    /** The line after the problem of {@code gabier --bogus}: where to ask for the command of a task. */
    static final String USAGE_ERROR_POINTER = "Not sure which command? Ask for the task: gabier help --for=\"<task>\"";

    /** What a run of the binary printed on each stream, and its exit code. */
    record Run(String out, String err, int exit) {}

    /**
     * Runs the binary alone: in the empty directory, with an empty environment but the given variables, the two
     * streams kept apart. It must come back in a minute: a client that hangs is a failure, not a stuck stage.
     */
    static Run runBinary(Path binary, Path directory, List<String> environment, String... args)
            throws IOException, InterruptedException {
        return runBinaryReading(binary, directory, environment, null, args);
    }

    /** As {@code runBinary}, with the text written to the standard input of the binary, which is then closed. */
    static Run runBinaryReading(Path binary, Path directory, List<String> environment, String input, String... args)
            throws IOException, InterruptedException {
        var command = new ArrayList<String>();
        command.add(binary.toString());
        command.addAll(List.of(args));
        ProcessBuilder builder = new ProcessBuilder(command).directory(directory.toFile());
        builder.environment().clear();
        for (int i = 0; i < environment.size(); i += 2) {
            builder.environment().put(environment.get(i), environment.get(i + 1));
        }
        Path out = Files.createTempFile(directory, "out", ".txt");
        Path err = Files.createTempFile(directory, "err", ".txt");
        builder.redirectOutput(out.toFile()).redirectError(err.toFile());
        Process process = builder.start();
        if (input != null) {
            try (var stdin = process.getOutputStream()) {
                stdin.write(input.getBytes(StandardCharsets.UTF_8));
            }
        }
        if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            return new Run("", "timed out after " + TIMEOUT_SECONDS + "s: " + String.join(" ", command), -1);
        }
        try {
            return new Run(
                    Files.readString(out, StandardCharsets.UTF_8).strip(),
                    Files.readString(err, StandardCharsets.UTF_8).strip(),
                    process.exitValue());
        } finally {
            Files.delete(out);
            Files.delete(err);
        }
    }

    static Run runBinary(Path binary, Path directory, String... args) throws IOException, InterruptedException {
        return runBinary(binary, directory, List.of(), args);
    }

    /** The answer of the request of this id, whatever its place among the lines: calls are answered as they end. */
    static String answerOf(List<String> answers, String id) {
        String start = "{\"jsonrpc\":\"2.0\",\"id\":" + id + ",";
        return answers.stream()
                .filter(answer -> answer.startsWith(start))
                .findFirst()
                .orElse("");
    }

    static void deleteTree(Path directory) throws IOException {
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    static int run(String... command) throws InterruptedException {
        System.out.println("$ " + String.join(" ", command));
        try {
            return new ProcessBuilder(command).inheritIO().start().waitFor();
        } catch (IOException e) {
            System.err.println("Cannot run " + command[0] + ": " + e.getMessage());
            return 127;
        }
    }
}
