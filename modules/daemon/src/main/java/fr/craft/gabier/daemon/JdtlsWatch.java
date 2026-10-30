package fr.craft.gabier.daemon;

import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * What is awaited from jdtls, raced against its death. Every wait ends as soon as the answer arrives, jdtls exits or
 * reports an error, or the one deadline, started when this object is created, is over: the waits share it, so nothing
 * waits longer than that in all.
 */
final class JdtlsWatch {

    /** The reader of the output of jdtls can see its end before the process is reported as exited. */
    private static final Duration EXIT_GRACE = Duration.ofSeconds(2);

    private final Process jdtls;
    private final CompletableFuture<Process> exited;
    private final CompletableFuture<String> failure;
    private final Duration timeout;
    private final long start = System.nanoTime();

    /**
     * @param jdtls the jdtls process, whose exit ends every wait
     * @param failure completed, with the reason, when jdtls reports an error
     * @param timeout how long all the waits may last together
     */
    JdtlsWatch(Process jdtls, CompletableFuture<String> failure, Duration timeout) {
        this.jdtls = jdtls;
        this.exited = jdtls.onExit();
        this.failure = failure;
        this.timeout = timeout;
    }

    /**
     * Sends a request and waits for its answer.
     *
     * @param what says what is awaited, for the message of the failure
     * @param request sends the request and returns its answer; it may throw when jdtls is already gone
     * @throws JdtlsException when jdtls exits or reports an error, or when the deadline is over, before the answer
     */
    <T> T await(String what, Supplier<CompletableFuture<T>> request) throws InterruptedException {
        CompletableFuture<T> answer;
        try {
            answer = request.get();
        } catch (RuntimeException e) {
            throw stopped(what, e);
        }
        try {
            CompletableFuture.anyOf(answer, exited, failure).get(remaining().toNanos(), TimeUnit.NANOSECONDS);
        } catch (TimeoutException e) {
            throw timedOut(what);
        } catch (ExecutionException e) {
            // The first future to end failed: the checks below say which one.
        }
        if (answer.state() == Future.State.SUCCESS) {
            return answer.resultNow();
        }
        throw stopped(what, failureOf(answer));
    }

    /**
     * Waits for the interval, or less when the deadline comes first, and returns early only by failing.
     *
     * @param what says what is awaited, for the message of the failure
     * @throws JdtlsException when jdtls exits or reports an error, or when the deadline is over
     */
    void pause(String what, Duration interval) throws InterruptedException {
        Duration wait = interval.compareTo(remaining()) < 0 ? interval : remaining();
        try {
            CompletableFuture.anyOf(exited, failure).get(wait.toNanos(), TimeUnit.NANOSECONDS);
        } catch (TimeoutException e) {
            if (remaining().isZero()) {
                throw timedOut(what);
            }
            return;
        } catch (ExecutionException e) {
            throw stopped(what, e.getCause());
        }
        throw stopped(what, null);
    }

    /** Explains why the wait cannot go on: the status Error, the exit of jdtls (its code first), or else the cause. */
    private JdtlsException stopped(String what, @Nullable Throwable cause) throws InterruptedException {
        if (failure.state() == Future.State.SUCCESS) {
            return new JdtlsException(
                    failure.resultNow() + ", after " + seconds(elapsed()) + ", while waiting for " + what);
        }
        try {
            exited.get(EXIT_GRACE.toMillis(), TimeUnit.MILLISECONDS);
            return new JdtlsException("jdtls exited with code " + jdtls.exitValue() + " after " + seconds(elapsed())
                    + ", while waiting for " + what);
        } catch (TimeoutException | ExecutionException e) {
            return new JdtlsException(
                    "Failed after " + seconds(elapsed()) + " waiting for " + what + ": " + cause, cause);
        }
    }

    private JdtlsException timedOut(String what) {
        return new JdtlsException("Timed out after " + seconds(elapsed()) + " (jdtls is given " + seconds(timeout)
                + " in all) waiting for " + what);
    }

    private static @Nullable Throwable failureOf(CompletableFuture<?> future) {
        return future.state() == Future.State.FAILED ? future.exceptionNow() : null;
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
