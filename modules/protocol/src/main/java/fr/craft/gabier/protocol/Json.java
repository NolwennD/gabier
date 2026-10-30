package fr.craft.gabier.protocol;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/** JSON, by hand: the cli native image has no third-party library. */
final class Json {

    private Json() {}

    /** The text as a JSON string, quotation marks included. */
    public static String quote(String value) {
        var quoted = new StringBuilder("\"");
        value.chars().forEach(character -> escape(quoted, (char) character));

        return quoted.append('"').toString();
    }

    // RFC 8259: the quotation mark, the reverse solidus and the control characters below U+0020 must be escaped, and
    // nothing else has to be.
    private static void escape(StringBuilder quoted, char character) {
        switch (character) {
            case '"' -> quoted.append("\\\"");
            case '\\' -> quoted.append("\\\\");
            case '\n' -> quoted.append("\\n");
            case '\r' -> quoted.append("\\r");
            case '\t' -> quoted.append("\\t");
            default -> {
                if (character < ' ') {
                    quoted.append(String.format("\\u%04x", (int) character));
                } else {
                    quoted.append(character);
                }
            }
        }
    }

    /**
     * Reads one JSON value, RFC 8259 and nothing more. An object is a {@code Map} in the order of its members, an
     * array a {@code List}, a string a {@code String}, a number a {@code BigDecimal} (no digit is lost), {@code true}
     * and {@code false} a {@code Boolean}, and {@code null} is {@code null}.
     *
     * @throws MalformedJsonException when the text is not one JSON value
     */
    public static @Nullable Object parse(String text) {
        return new Parser(text).document();
    }

    private static final class Parser {

        private static final Pattern NUMBER = Pattern.compile("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?");
        // Bounds that keep a hostile line from costing a stack overflow or hundreds of megabytes: the exponent of a
        // number (BigDecimal accepts up to the int range, and an id is turned into an integer) and the nesting.
        private static final int MAX_SCALE = 1000;
        private static final int MAX_DEPTH = 64;
        private static final String HEXADECIMAL_DIGITS = "0123456789abcdefABCDEF";

        private final String text;
        private int position;
        private int depth;

        private Parser(String text) {
            this.text = text;
        }

        private @Nullable Object document() {
            var value = value();
            skipWhitespace();
            if (!atEnd()) {
                throw fail("the value is followed by more text");
            }

            return value;
        }

        private @Nullable Object value() {
            skipWhitespace();
            if (atEnd()) {
                throw fail("a value was expected");
            }

            return switch (text.charAt(position)) {
                case '{' -> nested(this::object);
                case '[' -> nested(this::array);
                case '"' -> string();
                case 't' -> literal("true", true);
                case 'f' -> literal("false", false);
                case 'n' -> literal("null", null);
                default -> number();
            };
        }

        private <T> T nested(Supplier<T> container) {
            if (depth == MAX_DEPTH) {
                throw fail("the nesting is too deep");
            }
            depth++;
            var value = container.get();
            depth--;

            return value;
        }

        private Map<String, @Nullable Object> object() {
            var members = new LinkedHashMap<String, @Nullable Object>();
            position++;
            skipWhitespace();
            if (consume('}')) {
                return members;
            }
            do {
                skipWhitespace();
                if (atEnd() || text.charAt(position) != '"') {
                    throw fail("a string was expected");
                }
                var name = string();
                skipWhitespace();
                if (!consume(':')) {
                    throw fail("a colon was expected");
                }
                members.put(name, value());
                skipWhitespace();
            } while (consume(','));
            if (!consume('}')) {
                throw fail("a comma or a closing brace was expected");
            }

            return members;
        }

        private List<@Nullable Object> array() {
            var elements = new ArrayList<@Nullable Object>();
            position++;
            skipWhitespace();
            if (consume(']')) {
                return elements;
            }
            do {
                elements.add(value());
                skipWhitespace();
            } while (consume(','));
            if (!consume(']')) {
                throw fail("a comma or a closing bracket was expected");
            }

            return elements;
        }

        private String string() {
            var string = new StringBuilder();
            position++;
            while (true) {
                if (atEnd()) {
                    throw fail("the string is not closed");
                }
                var character = text.charAt(position);
                if (character == '"') {
                    position++;

                    return string.toString();
                }
                if (character < ' ') {
                    throw fail("a control character must be escaped");
                }
                position++;
                string.append(character == '\\' ? escape() : character);
            }
        }

        private char escape() {
            if (atEnd()) {
                throw fail("the string is not closed");
            }
            var escaped = text.charAt(position);
            var character = switch (escaped) {
                case '"', '\\', '/' -> escaped;
                case 'b' -> '\b';
                case 'f' -> '\f';
                case 'n' -> '\n';
                case 'r' -> '\r';
                case 't' -> '\t';
                case 'u' -> unicode();
                default -> throw fail("this escape does not exist");
            };
            if (escaped != 'u') {
                position++;
            }

            return character;
        }

        private char unicode() {
            position++;
            var code = 0;
            for (var digit = 0; digit < 4; digit++) {
                var value = atEnd() ? -1 : HEXADECIMAL_DIGITS.indexOf(text.charAt(position));
                if (value < 0) {
                    throw fail("four hexadecimal digits were expected");
                }
                code = code * 16 + (value < 16 ? value : value - 6);
                position++;
            }

            return (char) code;
        }

        private @Nullable Object literal(String literal, @Nullable Object value) {
            if (!text.startsWith(literal, position)) {
                throw fail("a value was expected");
            }
            position += literal.length();

            return value;
        }

        private BigDecimal number() {
            var matcher = NUMBER.matcher(text).region(position, text.length());
            if (!matcher.lookingAt()) {
                throw fail("a value was expected");
            }
            // Said at the start of the number, even when the text ends with it.
            var number = bounded(matcher.group())
                    .orElseThrow(
                            () -> MalformedJsonException.at(position, "the exponent of the number is out of range"));
            position = matcher.end();

            return number;
        }

        /** The number, or nothing when its exponent is beyond what the daemon reads. */
        private static Optional<BigDecimal> bounded(String digits) {
            try {
                var number = new BigDecimal(digits);

                return Math.abs((long) number.scale()) > MAX_SCALE ? Optional.empty() : Optional.of(number);
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }

        private boolean consume(char expected) {
            if (!atEnd() && text.charAt(position) == expected) {
                position++;

                return true;
            }

            return false;
        }

        private void skipWhitespace() {
            while (!atEnd() && " \t\n\r".indexOf(text.charAt(position)) >= 0) {
                position++;
            }
        }

        private boolean atEnd() {
            return position >= text.length();
        }

        /** Whatever was expected, a text that ends is said to end too soon. */
        private MalformedJsonException fail(String reason) {
            return MalformedJsonException.at(position, atEnd() ? "the text ends too soon" : reason);
        }
    }
}
