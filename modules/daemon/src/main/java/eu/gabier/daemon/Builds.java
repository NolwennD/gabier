package eu.gabier.daemon;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Says when jdtls has finished building the project, so that what it published is what the files say now. jdtls
 * compiles incrementally and publishes the problems of the whole workspace as a build ends ({@link
 * Diagnostics#published}), the files it was told of that changed included. Two signals, one for each part of the life
 * of jdtls (checked on the real jdtls, with Gradle, Maven and no build tool):
 *
 * <ul>
 *   <li>After the start, the initialization of jdtls ends with the first publication of the problems of the whole
 *       workspace, which comes after its {@code ServiceReady} status and its answers to the requests (the first
 *       publication of a Gradle project of 500 files came six seconds after the answer to {@code java/buildWorkspace}).
 *       The one thing it does that the client sees, just before that publication (26 ms before, on that project), is to
 *       register the files it wants watched ({@link WatchedFiles}): {@link #awaitInitialized} waits for it.
 *   <li>Then, the answer to {@code java/buildWorkspace} (incremental): it waits for the build that is running and
 *       compiles what is still pending, and the problems it finds are published before it answers (for a file edited
 *       on disk). jdtls has no notification for the end of a build, and the progress it reports does not say that a
 *       build is not about to begin.
 * </ul>
 *
 * <p>The publications of a build are not ordered with that answer: the first one after the start arrives as a stream
 * of half a second on that project, and the answer to a request made right after the registration may come before the
 * end of it. The answer is therefore followed by a short pause that any publication extends ({@link #SETTLE}).
 */
final class Builds {

    /** How long nothing may be published after the answer to the build before what jdtls published is taken as final. */
    static final Duration SETTLE = Duration.ofSeconds(1);

    private final Diagnostics diagnostics;
    private final Duration settle;

    /**
     * Follows the publications of a jdtls.
     *
     * @param diagnostics what jdtls publishes, whose publications extend the pause
     * @param settle the pause: how long nothing is published after the answer to the build
     */
    Builds(Diagnostics diagnostics, Duration settle) {
        this.diagnostics = diagnostics;
        this.settle = settle;
    }

    /**
     * Waits until jdtls has finished its initialization as far as the client can see: it registers the files it wants
     * watched near the end of it, shortly before the first publication of the problems of the whole workspace. Until
     * then nothing it answers says anything about the problems, on a large project the build it answers for is not
     * the one that publishes. To be called before the changes on disk are told ({@code WatchedFiles} reports none
     * until jdtls has registered what it watches).
     *
     * @throws JdtlsException when jdtls exits or reports an error, or does not register in time
     */
    void awaitInitialized(JdtlsSession session, JdtlsWatch watch) throws InterruptedException {
        while (!session.watchersRegistered()) {
            watch.pause("jdtls to register the files it watches", Duration.ofMillis(100));
        }
    }

    /**
     * Waits until jdtls has built the project and what it published about it has arrived. The changes on disk are told
     * to jdtls before ({@code JdtlsLanguageServer.takeInChangesOnDisk}): a build cannot see a change jdtls does not know.
     *
     * @throws JdtlsException when jdtls exits or reports an error, does not answer in time or fails the build
     */
    void awaitFinished(JdtlsSession session, JdtlsWatch watch) throws InterruptedException {
        watch.await("jdtls to build the workspace", () -> session.server().buildWorkspace(false));
        long answered = System.nanoTime();
        Supplier<Duration> quietSinceTheAnswer =
                () -> min(diagnostics.quietFor(), Duration.ofNanos(System.nanoTime() - answered));
        QuietWait.await(watch, "the diagnostics published after the build", settle, quietSinceTheAnswer);
    }

    private static Duration min(Duration first, Duration second) {
        return first.compareTo(second) <= 0 ? first : second;
    }
}
