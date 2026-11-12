package eu.gabier.daemon;

import eu.gabier.protocol.SelectedTests;
import java.util.Map;

/**
 * {@code watch-affected-tests}: follows the tests that what changes on disk affects, with no parameter. The result is the
 * one of {@code affected-tests-changed}, for the changes now (they are used up, as that call does); after it the daemon
 * writes, on the same connection, an {@code affected-tests} notification with the tests of each series of changes that
 * affects some (none when no test is affected), a {@code refusal} when the changes cannot be answered (a changed file does
 * not compile: they are kept and listed once the code compiles), a {@code notice} for what the person who reads should
 * know, and {@code ended} with the reason if it cannot go on. The refusals of the request are those of {@code
 * affected-tests-changed} except the refusals of the testing context, which are told as the notification {@code refusal}
 * after the result (an empty list of tests) and follow the changes all the same; a connection follows one thing, a second
 * request is refused with {@code already-watching}.
 *
 * <p>The changes belong to the daemon, like those of {@code affected-tests-changed}: what a watcher is told is no longer
 * told to another client, the watcher or a call.
 */
record WatchAffectedTestsMethod(FollowedTests followed, ProjectFeed feed) implements StreamingMethod {

    @Override
    public String name() {
        return "watch-affected-tests";
    }

    @Override
    public Outcome call(Map<String, String> params, Peer peer) {
        var now = followed.read();
        var listener = peer.<AffectedTestsUpdate>listen(
                AffectedTestsUpdate::notifications, AffectedTestsUpdate::then, feed::unsubscribeTests);
        feed.subscribeTests(listener);
        now.refusal().ifPresent(reason -> listener.offer(AffectedTestsUpdate.refused(reason)));

        return Outcome.of(new SelectedTests(now.tests()).toJson());
    }
}
