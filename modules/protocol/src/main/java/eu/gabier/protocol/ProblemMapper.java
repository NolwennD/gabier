package eu.gabier.protocol;

import eu.gabier.shared.error.domain.ErrorStatus;
import eu.gabier.shared.error.domain.GeneratorException;
import eu.gabier.shared.error.domain.StandardErrorKey;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The one place where an exception becomes a problem object, at the edge of the program that reports it. A {@link
 * GeneratorException} is a refusal gabier knows: its key, its message and its parameters are shown. Any other exception
 * is a bug or a failure of the environment, and the details of it (message, cause, stack trace) can leak a path or a
 * secret: it is reported as the generic internal error and nothing else.
 */
public final class ProblemMapper {

    private static final String URI_PREFIX = "urn:gabier:error:";
    private static final String NO_DETAIL = "An internal error occurred, and it is not described here";
    private static final String NO_MESSAGE = "An error occurred";

    private ProblemMapper() {}

    public static Problem toProblem(Throwable throwable) {
        if (throwable instanceof GeneratorException exception) {
            return of(exception);
        }

        return generic();
    }

    private static Problem of(GeneratorException exception) {
        var key = exception.key().get();
        // The message of a GeneratorException is never null (its builder gives one), but the JDK does not say so.
        var detail = Optional.ofNullable(exception.getMessage())
                .filter(message -> !message.isBlank())
                .orElse(NO_MESSAGE);

        return problem(key, detail, exception.parameters(), exception.status());
    }

    private static Problem generic() {
        return problem(
                StandardErrorKey.INTERNAL_SERVER_ERROR.get(), NO_DETAIL, Map.of(), ErrorStatus.INTERNAL_SERVER_ERROR);
    }

    private static Problem problem(String key, String detail, Map<String, String> parameters, ErrorStatus category) {
        return new Problem(URI_PREFIX + key, title(key), detail, key, parameters, category);
    }

    // The same for every problem of a key, and without a table that would have to know the keys of every context.
    private static String title(String key) {
        var words = key.replace('-', ' ');

        return words.substring(0, 1).toUpperCase(Locale.ROOT) + words.substring(1);
    }
}
