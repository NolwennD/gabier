package fr.craft.gabier.daemon;

import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * What the probe waits for, raced against the death of jdtls. Every wait ends as soon as the answer arrives, jdtls
 * exits or reports an error, or the one deadline, started when this object is created, is over: the waits share it,
 * so the probe never waits longer than that in all.
 */
final class ProbeWatch {

    /** The reader of the output of jdtls can see its end before the process is reported as exited. */
    private static final Duration EXIT_GRACE = Duration.ofSeconds(2);

    private final Process jdtls;
    private final CompletableFuture<String> failure;
    private final Duration timeout;
    private final long start = System.nanoTime();

    /**
     * @param jdtls the jdtls process, whose exit ends every wait
     * @param failure completed, with the reason, when jdtls reports an error
     * @param timeout how long the whole probe may wait, in all its waits together
     */
    ProbeWatch(Process jdtls, CompletableFuture<String> failure, Duration timeout) {
        this.jdtls = jdtls;
        this.failure = failure;
        this.timeout = timeout;
    }

    /**
     * Sends a request and waits for its answer.
     *
     * @param what says what is awaited, for the message of the failure
     * @param request sends the request and returns its answer; it may throw when jdtls is already gone
     * @throws AssertionError when jdtls exits or reports an error, or when the deadline is over, before the answer
     */
    <T> T await(String what, Supplier<CompletableFuture<T>> request) throws InterruptedException {
        CompletableFuture<T> answer;
        try {
            answer = request.get();
        } catch (RuntimeException e) {
            throw stopped(what, e);
        }
        try {
            CompletableFuture.anyOf(answer, jdtls.onExit(), failure)
                    .get(remaining().toNanos(), TimeUnit.NANOSECONDS);
        } catch (TimeoutException e) {
            throw timedOut(what);
        } catch (ExecutionException e) {
            // The first future to end failed: the checks below say which one.
        }
        if (answer.isDone() && !answer.isCompletedExceptionally()) {
            return answer.join();
        }
        throw stopped(what, answer.isCompletedExceptionally() ? cause(answer) : null);
    }

    /**
     * Waits for the interval, or less when the deadline comes first, and returns early only by failing.
     *
     * @param what says what is awaited, for the message of the failure
     * @throws AssertionError when jdtls exits or reports an error, or when the deadline is over
     */
    void pause(String what, Duration interval) throws InterruptedException {
        Duration wait = interval.compareTo(remaining()) < 0 ? interval : remaining();
        try {
            CompletableFuture.anyOf(jdtls.onExit(), failure).get(wait.toNanos(), TimeUnit.NANOSECONDS);
        } catch (TimeoutException e) {
            if (remaining().isZero()) {
                throw timedOut(what);
            }
            return;
        } catch (ExecutionException e) {
            // Nothing but a completed future ends the wait: the check below says which one.
        }
        throw stopped(what, null);
    }

    /** Explains why the wait cannot go on: the status Error, the exit of jdtls (its code first), or else the cause. */
    private AssertionError stopped(String what, Throwable cause) throws InterruptedException {
        if (failure.isDone()) {
            return new AssertionError(failure.join() + ", after " + seconds(elapsed()) + ", while waiting for " + what);
        }
        try {
            jdtls.onExit().get(EXIT_GRACE.toMillis(), TimeUnit.MILLISECONDS);
            return new AssertionError("jdtls exited with code " + jdtls.exitValue() + " after " + seconds(elapsed())
                    + ", while waiting for " + what);
        } catch (TimeoutException | ExecutionException e) {
            return new AssertionError(
                    "Failed after " + seconds(elapsed()) + " waiting for " + what + ": " + cause, cause);
        }
    }

    private AssertionError timedOut(String what) {
        return new AssertionError("Timed out after " + seconds(elapsed()) + " (the probe waits " + seconds(timeout)
                + " in all) waiting for " + what);
    }

    private static Throwable cause(CompletableFuture<?> failed) {
        try {
            failed.join();
            return null;
        } catch (RuntimeException e) {
            return e.getCause() == null ? e : e.getCause();
        }
    }

    private static String seconds(Duration duration) {
        return String.format(Locale.ROOT, "%.1f seconds", duration.toMillis() / 1000.0);
    }

    private Duration remaining() {
        Duration remaining = timeout.minus(elapsed());
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }

    private Duration elapsed() {
        return Duration.ofNanos(System.nanoTime() - start);
    }
}
