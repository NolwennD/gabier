package fr.craft.gabier.shared.error.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MissingMandatoryValueExceptionTest {

    public static final String FIELD = "field";

    @Test
    void shouldGetExceptionForBlankValue() {
        var exception = MissingMandatoryValueException.forBlankValue(FIELD);

        assertThat(exception.getMessage()).isEqualTo("The field \"field\" is mandatory and wasn't set (blank)");
    }

    @Test
    void shouldGetExceptionForNullValue() {
        var exception = MissingMandatoryValueException.forNullValue(FIELD);

        assertThat(exception.getMessage()).isEqualTo("The field \"field\" is mandatory and wasn't set (null)");
    }

    @Test
    void shouldGetExceptionForEmptyCollection() {
        var exception = MissingMandatoryValueException.forEmptyValue(FIELD);

        assertThat(exception.getMessage()).isEqualTo("The field \"field\" is mandatory and wasn't set (empty)");
    }
}
