package fr.craft.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.junit.jupiter.api.Test;

/** A request that jdtls declines is told apart from one that fails. */
class JdtlsRequestsTest {

    private static <T> CompletableFuture<T> failed(Throwable failure) {
        return CompletableFuture.failedFuture(failure);
    }

    private static ResponseErrorException error(ResponseErrorCode code) {
        return new ResponseErrorException(new ResponseError(code, "said by jdtls", null));
    }

    @Test
    void givesTheAnswer() {
        assertThat(JdtlsRequests.unlessDeclined(CompletableFuture.completedFuture("answer")))
                .isCompletedWithValue(Optional.of("answer"));
    }

    @Test
    void takesANullAnswerForNothingToGive() {
        assertThat(JdtlsRequests.unlessDeclined(CompletableFuture.completedFuture(null)))
                .isCompletedWithValue(Optional.empty());
    }

    @Test
    void takesAnInvalidRequestForADeclinedOne() {
        assertThat(JdtlsRequests.unlessDeclined(failed(error(ResponseErrorCode.InvalidRequest))))
                .isCompletedWithValue(Optional.empty());
    }

    @Test
    void keepsAnotherResponseErrorAsAFailure() {
        var result = JdtlsRequests.unlessDeclined(failed(error(ResponseErrorCode.InternalError)));

        assertThat(result).isCompletedExceptionally();
        assertThatThrownBy(result::join)
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(ResponseErrorException.class);
    }

    @Test
    void keepsAFailureThatIsNotAResponseErrorAsAFailure() {
        var cause = new IllegalStateException("the connection is gone");

        assertThatThrownBy(() -> JdtlsRequests.unlessDeclined(failed(cause)).join())
                .isInstanceOf(CompletionException.class)
                .hasRootCause(cause);
    }
}
