package eu.gabier.daemon;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;

/** What the adapter does with the answers of jdtls that are not an answer: a request it declines. */
final class JdtlsRequests {

    private JdtlsRequests() {}

    /**
     * Tells apart a request that jdtls declines from one that fails. jdtls declines with a response error of the code
     * {@code InvalidRequest} (a rename of what cannot be renamed, to a name that is not an identifier) or with a null
     * answer, which the protocol defines as "nothing to give". Any other failure stays a failure.
     *
     * @return completed with the answer, or with none when jdtls declined
     */
    static <T> CompletableFuture<Optional<T>> unlessDeclined(CompletableFuture<T> request) {
        return request.handle((answer, failure) -> {
            if (failure == null) {
                return Optional.ofNullable(answer);
            }
            if (declines(failure)) {
                return Optional.empty();
            }
            throw new CompletionException(failure);
        });
    }

    /** Whether jdtls answered with the response error {@code InternalError}: a handler of its threw. */
    static boolean isInternalError(ResponseErrorException error) {
        return error.getResponseError().getCode() == ResponseErrorCode.InternalError.getValue();
    }

    static boolean declines(Throwable failure) {
        return failure instanceof ResponseErrorException error
                && error.getResponseError().getCode() == ResponseErrorCode.InvalidRequest.getValue();
    }
}
