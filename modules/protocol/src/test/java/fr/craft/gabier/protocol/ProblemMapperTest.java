package fr.craft.gabier.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import fr.craft.gabier.shared.error.domain.Assert;
import fr.craft.gabier.shared.error.domain.ErrorKey;
import fr.craft.gabier.shared.error.domain.ErrorStatus;
import fr.craft.gabier.shared.error.domain.GeneratorException;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ProblemMapperTest {

    private enum SomeKey implements ErrorKey {
        EDITING_INVALID_RANGE("editing-invalid-range"),
        X("x");

        private final String key;

        SomeKey(String key) {
            this.key = key;
        }

        @Override
        public String get() {
            return key;
        }
    }

    @Nested
    class OfAGeneratorException {

        private final Problem problem =
                ProblemMapper.toProblem(GeneratorException.badRequest(SomeKey.EDITING_INVALID_RANGE)
                        .message("The range ends before it starts")
                        .addParameter("start", "1:0")
                        .addParameter("end", "0:0")
                        .build());

        @Test
        void givesItsKey() {
            assertThat(problem.key()).isEqualTo("editing-invalid-range");
        }

        @Test
        void givesAnUriBuiltFromTheKey() {
            assertThat(problem.type()).isEqualTo("urn:gabier:error:editing-invalid-range");
        }

        @Test
        void givesItsMessageAsTheDetail() {
            assertThat(problem.detail()).isEqualTo("The range ends before it starts");
        }

        @Test
        void givesItsParameters() {
            assertThat(problem.parameters()).isEqualTo(Map.of("start", "1:0", "end", "0:0"));
        }

        @Test
        void givesTheKeyInWordsAsTheTitle() {
            assertThat(problem.title()).isEqualTo("Editing invalid range");
        }

        @Test
        void givesAOneWordKeyAnInitialCapitalAsTheTitle() {
            assertThat(ProblemMapper.toProblem(
                                    GeneratorException.badRequest(SomeKey.X).build())
                            .title())
                    .isEqualTo("X");
        }

        @Test
        void keepsItsStatusAsTheCategory() {
            assertThat(problem.category()).isEqualTo(ErrorStatus.BAD_REQUEST);
        }

        @Test
        void keepsAnInternalStatusAsTheCategory() {
            var internal = GeneratorException.internalServerError(SomeKey.X)
                    .message("disk full")
                    .build();

            assertThat(ProblemMapper.toProblem(internal).category()).isEqualTo(ErrorStatus.INTERNAL_SERVER_ERROR);
        }

        @Test
        void aBlankMessageGivesWayToAGenericDetail() {
            var blank = GeneratorException.badRequest(SomeKey.X).message(" ").build();

            assertThat(ProblemMapper.toProblem(blank).detail()).isEqualTo("An error occurred");
        }

        @Test
        void doesNotShowItsCause() {
            var failed = GeneratorException.internalServerError(SomeKey.X)
                    .message("The file could not be read")
                    .cause(new IllegalStateException("secret cause"))
                    .build();

            var json = ProblemMapper.toProblem(failed).toJson();

            assertThat(json).doesNotContain("secret cause").doesNotContain("IllegalStateException");
        }

        @Test
        void aTechnicalErrorHasTheInternalErrorKeyAndKeepsItsOwnMessageAsTheDetail() {
            // The message of a GeneratorException is gabier's own, written to be shown: unlike the message of any
            // other exception, it is not replaced by the generic text.
            var problem = ProblemMapper.toProblem(GeneratorException.technicalError("it broke", null));

            assertThat(problem.key()).isEqualTo("internal-server-error");
            assertThat(problem.title()).isEqualTo("Internal server error");
            assertThat(problem.detail()).isEqualTo("it broke");
            assertThat(problem.category()).isEqualTo(ErrorStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Nested
    class OfAnyOtherException {

        private final Problem problem = ProblemMapper.toProblem(
                new IllegalStateException("secret /home/someone/file", new RuntimeException("secret cause")));

        @Test
        void isTheGenericInternalError() {
            assertThat(problem.key()).isEqualTo("internal-server-error");
            assertThat(problem.type()).isEqualTo("urn:gabier:error:internal-server-error");
            assertThat(problem.title()).isEqualTo("Internal server error");
            assertThat(problem.category()).isEqualTo(ErrorStatus.INTERNAL_SERVER_ERROR);
            assertThat(problem.parameters()).isEmpty();
        }

        @Test
        void saysInEnglishThatNoDetailIsGiven() {
            assertThat(problem.detail()).isEqualTo("An internal error occurred, and it is not described here");
        }

        @Test
        void doesNotLeakTheMessageTheCauseOrAStackTrace() {
            var shown = problem.toJson() + problem.toText();

            assertThat(shown)
                    .doesNotContain("secret")
                    .doesNotContain("IllegalStateException")
                    .doesNotContain("RuntimeException")
                    .doesNotContain("\tat ")
                    .doesNotContain("ProblemMapperTest");
        }

        @Test
        void anAssertionFailureIsAnInternalErrorToo() {
            var failure = catchThrowable(() -> Assert.notNull("field", null));

            var problem = ProblemMapper.toProblem(failure);

            assertThat(problem.key()).isEqualTo("internal-server-error");
            assertThat(problem.detail()).doesNotContain("field");
        }
    }
}
