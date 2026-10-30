package fr.craft.gabier.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import fr.craft.gabier.shared.error.domain.ErrorStatus;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ExitCodeTest {

    private static Problem problem(ErrorStatus category) {
        return new Problem("urn:gabier:error:k", "K", "detail", "k", Map.of(), category);
    }

    @Test
    void successIsZero() {
        assertThat(ExitCode.SUCCESS.value()).isZero();
    }

    @Test
    void aRefusedRequestIsTwo() {
        assertThat(ExitCode.of(problem(ErrorStatus.BAD_REQUEST))).isEqualTo(ExitCode.REFUSED);
        assertThat(ExitCode.REFUSED.value()).isEqualTo(2);
    }

    @Test
    void anInternalErrorIsOne() {
        assertThat(ExitCode.of(problem(ErrorStatus.INTERNAL_SERVER_ERROR))).isEqualTo(ExitCode.INTERNAL_ERROR);
        assertThat(ExitCode.INTERNAL_ERROR.value()).isEqualTo(1);
    }

    @Test
    void everyCategoryOfProblemHasACode() {
        for (ErrorStatus category : ErrorStatus.values()) {
            assertThat(ExitCode.of(problem(category))).isNotEqualTo(ExitCode.SUCCESS);
        }
    }

    @Test
    void theCodesAreDistinct() {
        assertThat(Arrays.stream(ExitCode.values()).map(ExitCode::value)).doesNotHaveDuplicates();
    }
}
