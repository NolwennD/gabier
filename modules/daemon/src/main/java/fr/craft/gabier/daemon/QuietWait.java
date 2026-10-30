package fr.craft.gabier.daemon;

import java.time.Duration;
import java.util.Locale;
import java.util.function.Supplier;

/** Waits for something to go quiet, inside the deadline of a {@link JdtlsWatch} and raced against the death of jdtls. */
final class QuietWait {

    private QuietWait() {}

    /**
     * Waits until the subject has been quiet for the given pause: it asks how long it has been quiet, and waits for
     * what is missing until the answer reaches the pause. A subject that keeps moving keeps the wait going, up to the
     * deadline.
     *
     * @param subject what must stay quiet, for the message of the failure ("waiting for the subject to stay quiet for
     *     5.0 seconds")
     * @param quiet how long the subject must have been quiet
     * @param quietFor how long the subject has been quiet so far
     * @throws JdtlsException when jdtls exits or reports an error, or when the deadline is over, before the quiet
     */
    static void await(JdtlsWatch watch, String subject, Duration quiet, Supplier<Duration> quietFor)
            throws InterruptedException {
        String what =
                subject + " to stay quiet for " + String.format(Locale.ROOT, "%.1f seconds", quiet.toMillis() / 1000.0);
        for (Duration quietSoFar = quietFor.get(); quietSoFar.compareTo(quiet) < 0; quietSoFar = quietFor.get()) {
            watch.pause(what, quiet.minus(quietSoFar));
        }
    }
}
