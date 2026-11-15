package eu.gabier.daemon;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * What the process of the tests writes to a file when it is done, and the daemon reads: one record per line, the fields
 * separated by a tab, a backslash, a tab, a line feed and a carriage return inside a field written as {@code \\},
 * {@code \t}, {@code \n} and {@code \r}. The process has no library but the JDK and the project's own, so there is no
 * JSON on this side, and none of the types of the domain: the process runs with the class path of the project and the
 * jar of the daemon, so this class and {@link TestRunnerMain} use the JDK only. The two sides are this class, from the
 * same jar.
 *
 * <ul>
 *   <li>{@code launcher-missing}: the class path has no JUnit Platform launcher, nothing ran;
 *   <li>{@code unrunnable <class>}, one for each class no test engine recognises: nothing ran;
 *   <li>{@code passed <count>} and {@code skipped <count>};
 *   <li>{@code failure <class> <method> <exception> <message> <where>}, one for each failed test; the method and the
 *       place are empty when there is none.
 * </ul>
 */
final class RunnerReport {

    private static final String LAUNCHER_MISSING = "launcher-missing";
    private static final String UNRUNNABLE = "unrunnable";
    private static final String PASSED = "passed";
    private static final String SKIPPED = "skipped";
    private static final String FAILURE = "failure";

    /**
     * A test that failed.
     *
     * @param testClass the binary name of its class
     * @param method the name of the method, empty for a class that failed
     * @param exception the binary name of what was thrown
     * @param message what it said, empty when it said nothing
     * @param where the place of the failure, empty when there is none
     */
    record Failure(String testClass, String method, String exception, String message, String where) {}

    private final boolean launcherMissing;
    private final List<String> unrunnable;
    private final int passed;
    private final int skipped;
    private final List<Failure> failures;

    private RunnerReport(
            boolean launcherMissing, List<String> unrunnable, int passed, int skipped, List<Failure> failures) {
        this.launcherMissing = launcherMissing;
        this.unrunnable = unrunnable;
        this.passed = passed;
        this.skipped = skipped;
        this.failures = failures;
    }

    /** The report of a run that could not start: no launcher on the class path. */
    static RunnerReport withoutLauncher() {
        return new RunnerReport(true, List.of(), 0, 0, List.of());
    }

    /** The report of a run that did not start because no engine recognises these classes. */
    static RunnerReport withUnrunnable(List<String> classes) {
        return new RunnerReport(false, List.copyOf(classes), 0, 0, List.of());
    }

    /** The report of the tests that ran. */
    static RunnerReport of(int passed, int skipped, List<Failure> failures) {
        return new RunnerReport(false, List.of(), passed, skipped, List.copyOf(failures));
    }

    boolean launcherMissing() {
        return launcherMissing;
    }

    List<String> unrunnable() {
        return unrunnable;
    }

    int passed() {
        return passed;
    }

    int skipped() {
        return skipped;
    }

    List<Failure> failures() {
        return failures;
    }

    /** The lines of the file. */
    List<String> lines() {
        List<String> lines = new ArrayList<>();
        if (launcherMissing) {
            lines.add(LAUNCHER_MISSING);
        }
        unrunnable.forEach(name -> lines.add(record(UNRUNNABLE, name)));
        lines.add(record(PASSED, String.valueOf(passed)));
        lines.add(record(SKIPPED, String.valueOf(skipped)));
        for (Failure failure : failures) {
            lines.add(record(
                    FAILURE,
                    failure.testClass(),
                    failure.method(),
                    failure.exception(),
                    failure.message(),
                    failure.where()));
        }

        return lines;
    }

    /**
     * Reads the lines of the file.
     *
     * @throws IllegalArgumentException when a line is not one the writer writes
     */
    static RunnerReport parse(List<String> lines) {
        boolean launcherMissing = false;
        List<String> unrunnable = new ArrayList<>();
        List<Failure> failures = new ArrayList<>();
        int passed = 0;
        int skipped = 0;
        for (String line : lines) {
            List<String> fields = fields(line);
            switch (fields.getFirst()) {
                case LAUNCHER_MISSING -> launcherMissing = true;
                case UNRUNNABLE -> unrunnable.add(fields.get(1));
                case PASSED -> passed = Integer.parseInt(fields.get(1));
                case SKIPPED -> skipped = Integer.parseInt(fields.get(1));
                case FAILURE ->
                    failures.add(
                            new Failure(fields.get(1), fields.get(2), fields.get(3), fields.get(4), fields.get(5)));
                default -> throw new IllegalArgumentException("Not a line of the report of the tests: " + line);
            }
        }

        return new RunnerReport(launcherMissing, unrunnable, passed, skipped, failures);
    }

    private static String record(String kind, String... fields) {
        return kind + "\t" + Arrays.stream(fields).map(RunnerReport::escape).collect(Collectors.joining("\t"));
    }

    private static String escape(String field) {
        return field.replace("\\", "\\\\")
                .replace("\t", "\\t")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private static List<String> fields(String line) {
        List<String> fields = new ArrayList<>();
        for (String field : line.split("\t", -1)) {
            fields.add(unescape(field));
        }

        return fields;
    }

    private static String unescape(String field) {
        StringBuilder text = new StringBuilder();
        for (int index = 0; index < field.length(); index++) {
            char current = field.charAt(index);
            if (current == '\\' && index + 1 < field.length()) {
                char escaped = field.charAt(++index);
                text.append(
                        switch (escaped) {
                            case 't' -> '\t';
                            case 'n' -> '\n';
                            case 'r' -> '\r';
                            default -> escaped;
                        });
            } else {
                text.append(current);
            }
        }

        return text.toString();
    }
}
