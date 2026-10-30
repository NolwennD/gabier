package fr.craft.gabier.shared.error.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The shortcuts of {@link GeneratorException} and the keys, which the tests of seed4j leave unused. */
class GeneratorExceptionBuildersTest {

    @Test
    void shouldBuildInternalServerError() {
        var exception = GeneratorException.internalServerError(StandardErrorKey.BAD_REQUEST)
                .build();

        assertThat(exception.key()).isEqualTo(StandardErrorKey.BAD_REQUEST);
        assertThat(exception.status()).isEqualTo(ErrorStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void shouldBuildBadRequest() {
        var exception =
                GeneratorException.badRequest(StandardErrorKey.BAD_REQUEST).build();

        assertThat(exception.key()).isEqualTo(StandardErrorKey.BAD_REQUEST);
        assertThat(exception.status()).isEqualTo(ErrorStatus.BAD_REQUEST);
    }

    @Test
    void shouldBuildTechnicalErrorWithoutCause() {
        var exception = GeneratorException.technicalError("This is a problem");

        assertThat(exception.getMessage()).isEqualTo("This is a problem");
        assertThat(exception.getCause()).isNull();
        assertThat(exception.key()).isEqualTo(StandardErrorKey.INTERNAL_SERVER_ERROR);
        assertThat(exception.status()).isEqualTo(ErrorStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void shouldGiveTheStringOfStandardKeys() {
        assertThat(StandardErrorKey.BAD_REQUEST.get()).isEqualTo("bad-request");
        assertThat(StandardErrorKey.INTERNAL_SERVER_ERROR.get()).isEqualTo("internal-server-error");
    }
}
