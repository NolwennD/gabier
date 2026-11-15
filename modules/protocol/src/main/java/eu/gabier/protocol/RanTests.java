package eu.gabier.protocol;

import eu.gabier.shared.error.domain.Assert;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * The result of the {@code test} and {@code test-changed} methods: how the tests ran, and the ones that failed. A test
 * that ran passed or failed; one that was disabled, or whose assumption did not hold, is skipped and did not run. The
 * names and the messages come from the tests and the libraries they use: hostile text, whatever prints them for a
 * person neutralises them.
 *
 * @param passed how many tests passed
 * @param skipped how many tests were skipped
 * @param failures the tests that failed, in the order they finished
 */
public record RanTests(int passed, int skipped, List<Failure> failures) {

    public RanTests {
        Assert.field("passed", passed).min(0);
        Assert.field("skipped", skipped).min(0);
        Assert.field("failures", failures).notNull().noNullElement();

        failures = List.copyOf(failures);
    }

    /** How many tests failed. */
    public int failed() {
        return failures.size();
    }

    /** How many tests ran: those that passed and those that failed. */
    public int ran() {
        return passed + failed();
    }

    /**
     * A test that failed.
     *
     * @param testClass the binary name of the test class
     * @param method the name of the test method; none when the class itself failed
     * @param exception the binary name of the class of what was thrown
     * @param message what it said, empty when it said nothing
     * @param where the first frame of the stack that is in the project, as {@code app.Ledger.withdraw(Ledger.java:17)};
     *     none when the stack has none
     */
    public record Failure(
            String testClass, Optional<String> method, String exception, String message, Optional<String> where) {

        public Failure {
            Assert.notBlank("testClass", testClass);
            Assert.notNull("method", method);
            Assert.notBlank("exception", exception);
            Assert.notNull("message", message);
            Assert.notNull("where", where);
        }

        private String toJson() {
            return "{\"class\":" + Json.quote(testClass)
                    + method.map(name -> ",\"method\":" + Json.quote(name)).orElse("")
                    + ",\"exception\":" + Json.quote(exception)
                    + ",\"message\":" + Json.quote(message)
                    + where.map(place -> ",\"where\":" + Json.quote(place)).orElse("")
                    + "}";
        }
    }

    /** The result as JSON, the members in a stable order. */
    public String toJson() {
        return "{\"ran\":" + ran() + ",\"passed\":" + passed + ",\"failed\":" + failed() + ",\"skipped\":" + skipped
                + ",\"failures\":"
                + failures.stream().map(Failure::toJson).collect(Collectors.joining(",", "[", "]")) + "}";
    }

    /**
     * Reads the result of a {@code test} response.
     *
     * @throws InvalidResponseException when a member is missing or has the wrong type, or the counts do not add up
     */
    public static RanTests of(Map<String, @Nullable Object> result) {
        if (!(result.get("failures") instanceof List<?> elements)) {
            throw invalid("The tests that ran have no list of failures");
        }
        var read = new RanTests(
                count(result, "passed"),
                count(result, "skipped"),
                elements.stream().map(RanTests::failure).toList());
        if (count(result, "failed") != read.failed() || count(result, "ran") != read.ran()) {
            throw invalid("The counts of the tests that ran do not add up");
        }

        return read;
    }

    private static Failure failure(@Nullable Object element) {
        if (!(element instanceof Map<?, ?> members)) {
            throw invalid("A failure of the tests is not an object");
        }

        return new Failure(
                text(members, "class"),
                optionalText(members, "method"),
                text(members, "exception"),
                text(members, "message"),
                optionalText(members, "where"));
    }

    private static int count(Map<String, @Nullable Object> result, String name) {
        try {
            if (result.get(name) instanceof BigDecimal number) {
                return number.intValueExact();
            }
        } catch (ArithmeticException e) {
            throw InvalidResponseException.of("The member " + name + " of the tests that ran is not an integer", e);
        }

        throw invalid("The member " + name + " of the tests that ran is not a number");
    }

    private static String text(Map<?, ?> members, String name) {
        if (members.get(name) instanceof String text) {
            return text;
        }

        throw invalid("The member " + name + " of a failure is not a string");
    }

    private static Optional<String> optionalText(Map<?, ?> members, String name) {
        return members.containsKey(name) ? Optional.of(text(members, name)) : Optional.empty();
    }

    private static InvalidResponseException invalid(String message) {
        return InvalidResponseException.of(message, null);
    }
}
