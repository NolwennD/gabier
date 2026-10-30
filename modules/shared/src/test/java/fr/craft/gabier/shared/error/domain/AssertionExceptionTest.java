package fr.craft.gabier.shared.error.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

/**
 * What each assertion exception tells its caller (type, field, parameters), which the tests of seed4j only reach
 * through the messages.
 */
class AssertionExceptionTest {

    private static final Instant NOW = Instant.parse("2024-01-01T00:00:00Z");

    private static AssertionException thrownBy(ThrowingCallable callable) {
        AssertionException exception = catchThrowableOfType(AssertionException.class, callable);
        assertThat(exception).isNotNull();

        return exception;
    }

    @Test
    void shouldDescribeMissingMandatoryValue() {
        var exception = thrownBy(() -> Assert.notNull("field", null));

        assertThat(exception).isInstanceOf(MissingMandatoryValueException.class);
        assertThat(exception.type()).isEqualTo(AssertionErrorType.MISSING_MANDATORY_VALUE);
        assertThat(exception.field()).isEqualTo("field");
        assertThat(exception.parameters()).isEmpty();
    }

    @Test
    void shouldDescribeStringWithWhitespaces() {
        var exception = thrownBy(() -> Assert.noWhitespace("field", "a b"));

        assertThat(exception).isInstanceOf(StringWithWhitespacesException.class);
        assertThat(exception.type()).isEqualTo(AssertionErrorType.STRING_WITH_WHITESPACES);
        assertThat(exception.field()).isEqualTo("field");
        assertThat(exception.parameters()).isEmpty();
    }

    @Test
    void shouldDescribeNullElementInCollection() {
        var exception = thrownBy(
                () -> Assert.field("field", java.util.Arrays.asList("a", null)).noNullElement());

        assertThat(exception).isInstanceOf(NullElementInCollectionException.class);
        assertThat(exception.type()).isEqualTo(AssertionErrorType.NULL_ELEMENT_IN_COLLECTION);
        assertThat(exception.field()).isEqualTo("field");
        assertThat(exception.parameters()).isEmpty();
    }

    @Test
    void shouldDescribeTooManyElements() {
        var exception =
                thrownBy(() -> Assert.field("field", List.of("a", "b", "c")).maxSize(2));

        assertThat(exception).isInstanceOf(TooManyElementsException.class);
        assertThat(exception.type()).isEqualTo(AssertionErrorType.TOO_MANY_ELEMENTS);
        assertThat(exception.field()).isEqualTo("field");
        assertThat(exception.parameters()).isEqualTo(Map.of("maxSize", "2", "currentSize", "3"));
    }

    @Test
    void shouldDescribeStringTooLong() {
        var exception = thrownBy(() -> Assert.field("field", "abc").maxLength(2));

        assertThat(exception).isInstanceOf(StringTooLongException.class);
        assertThat(exception.type()).isEqualTo(AssertionErrorType.STRING_TOO_LONG);
        assertThat(exception.field()).isEqualTo("field");
        assertThat(exception.parameters()).isEqualTo(Map.of("maxLength", "2", "currentLength", "3"));
    }

    @Test
    void shouldDescribeStringTooShort() {
        var exception = thrownBy(() -> Assert.field("field", "a").minLength(2));

        assertThat(exception).isInstanceOf(StringTooShortException.class);
        assertThat(exception.type()).isEqualTo(AssertionErrorType.STRING_TOO_SHORT);
        assertThat(exception.field()).isEqualTo("field");
        assertThat(exception.parameters()).isEqualTo(Map.of("minLength", "2", "currentLength", "1"));
    }

    @Test
    void shouldDescribeStringNotMatchingPattern() {
        var exception = thrownBy(() -> Assert.field("field", "abc").matchesPattern(Pattern.compile("[0-9]+")));

        assertThat(exception).isInstanceOf(NotMatchingExpectedPatternException.class);
        assertThat(exception.type()).isEqualTo(AssertionErrorType.STRING_NOT_MATCHING_PATTERN);
        assertThat(exception.field()).isEqualTo("field");
        assertThat(exception.parameters()).isEqualTo(Map.of("pattern", "[0-9]+"));
    }

    @Test
    void shouldDescribeNumberValueTooHigh() {
        var exception = thrownBy(() -> Assert.field("field", 5).max(3));

        assertThat(exception).isInstanceOf(NumberValueTooHighException.class);
        assertThat(exception.type()).isEqualTo(AssertionErrorType.NUMBER_VALUE_TOO_HIGH);
        assertThat(exception.field()).isEqualTo("field");
        assertThat(exception.parameters()).isEqualTo(Map.of("max", "3", "value", "5"));
    }

    @Test
    void shouldDescribeNumberValueTooLow() {
        var exception =
                thrownBy(() -> Assert.field("field", new BigDecimal("1")).min(3));

        assertThat(exception).isInstanceOf(NumberValueTooLowException.class);
        assertThat(exception.type()).isEqualTo(AssertionErrorType.NUMBER_VALUE_TOO_LOW);
        assertThat(exception.field()).isEqualTo("field");
        assertThat(exception.parameters()).isEqualTo(Map.of("min", "3", "value", "1"));
    }

    @Test
    void shouldDescribeNotAfterTime() {
        var exception = thrownBy(() -> Assert.field("field", NOW).after(NOW.plusSeconds(1)));

        assertThat(exception).isInstanceOf(NotAfterTimeException.class);
        assertThat(exception.type()).isEqualTo(AssertionErrorType.NOT_AFTER_TIME);
        assertThat(exception.field()).isEqualTo("field");
        assertThat(exception.parameters()).isEmpty();
    }

    @Test
    void shouldDescribeNotBeforeTime() {
        var exception = thrownBy(() -> Assert.field("field", NOW).before(NOW.minusSeconds(1)));

        assertThat(exception).isInstanceOf(NotBeforeTimeException.class);
        assertThat(exception.type()).isEqualTo(AssertionErrorType.NOT_BEFORE_TIME);
        assertThat(exception.field()).isEqualTo("field");
        assertThat(exception.parameters()).isEmpty();
    }

    @Test
    void shouldRefuseAnEmptyArray() {
        var exception = thrownBy(() -> Assert.notEmpty("field", new String[0]));

        assertThat(exception).isInstanceOf(MissingMandatoryValueException.class);
        assertThat(exception.field()).isEqualTo("field");
    }

    @Test
    void shouldAcceptAnArrayWithAnElement() {
        assertThatCode(() -> Assert.notEmpty("field", new String[] {"value"})).doesNotThrowAnyException();
    }
}
