package eu.gabier.protocol;

import eu.gabier.shared.error.domain.Assert;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * The result of the {@code diagnostics} method: the problems a language server reports in a file, none when it is
 * clean. Positions are 0-based, as in the domain; showing them from 1 is the business of the client.
 *
 * @param diagnostics the problems, in the order the server gave them
 */
public record FileDiagnostics(List<Diagnosis> diagnostics) {

    public FileDiagnostics {
        Assert.field("diagnostics", diagnostics).notNull().noNullElement();

        diagnostics = List.copyOf(diagnostics);
    }

    /**
     * One problem, as the protocol carries it.
     *
     * @param severity how serious it is
     * @param line the line where it starts, from 0
     * @param column the column where it starts, from 0, in UTF-16 code units
     * @param message what the server says, as it says it
     */
    public record Diagnosis(Severity severity, int line, int column, String message) {

        public Diagnosis {
            Assert.notNull("severity", severity);
            Assert.field("line", line).positive();
            Assert.field("column", column).positive();
            Assert.notNull("message", message);
        }

        private String toJson() {
            return """
                {"severity":%s,"line":%d,"column":%d,"message":%s}\
                """.formatted(Json.quote(severity.text()), line, column, Json.quote(message));
        }
    }

    /** How serious a problem is, as the Language Server Protocol ranks them, from the most severe. */
    public enum Severity {
        ERROR,
        WARNING,
        INFORMATION,
        HINT;

        /** The name written in the result and shown to the user. */
        public String text() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** The result as JSON, the members in a stable order. */
    public String toJson() {
        return diagnostics.stream().map(Diagnosis::toJson).collect(Collectors.joining(",", "{\"diagnostics\":[", "]}"));
    }

    /**
     * Reads the result of a {@code diagnostics} response.
     *
     * @throws InvalidResponseException when a member is missing or has the wrong type
     */
    public static FileDiagnostics of(Map<String, @Nullable Object> result) {
        if (!(result.get("diagnostics") instanceof List<?> elements)) {
            throw invalid("The diagnostics have no list of problems");
        }

        return new FileDiagnostics(
                elements.stream().map(FileDiagnostics::diagnosis).toList());
    }

    private static Diagnosis diagnosis(@Nullable Object element) {
        if (!(element instanceof Map<?, ?> members)) {
            throw invalid("A problem of the diagnostics is not an object");
        }

        return new Diagnosis(
                severity(members.get("severity")),
                integer(members, "line"),
                integer(members, "column"),
                text(members, "message"));
    }

    private static Severity severity(@Nullable Object name) {
        for (var severity : Severity.values()) {
            if (severity.text().equals(name)) {
                return severity;
            }
        }

        throw invalid("The member severity of a problem is not a severity");
    }

    private static int integer(Map<?, ?> members, String name) {
        try {
            if (members.get(name) instanceof BigDecimal number) {
                return number.intValueExact();
            }
        } catch (ArithmeticException e) {
            throw InvalidResponseException.of("The member " + name + " of a problem is not an integer", e);
        }

        throw invalid("The member " + name + " of a problem is not a number");
    }

    private static String text(Map<?, ?> members, String name) {
        if (members.get(name) instanceof String text) {
            return text;
        }

        throw invalid("The member " + name + " of a problem is not a string");
    }

    private static InvalidResponseException invalid(String message) {
        return InvalidResponseException.of(message, null);
    }
}
