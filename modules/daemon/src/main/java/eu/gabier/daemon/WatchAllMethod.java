package eu.gabier.daemon;

import eu.gabier.editing.application.FollowProblems;
import java.util.Map;

/**
 * {@code watch-all}: follows the problems of every Java file of the project, with no parameter. The result is the one of
 * {@code diagnostics-all} (the files that have a problem now); after it the daemon writes, on the same connection, a
 * {@code problems} notification each time the files that have a problem are not the same, with the files whose list
 * changed (a file that became clean has an empty list), a {@code notice} for what the person who reads should know, and
 * {@code ended} with the reason if it cannot go on ({@link Watching}). The refusals are those of {@code
 * diagnostics-all}, and then nothing is followed; a connection follows one thing, a second request is refused with
 * {@code already-watching}.
 */
record WatchAllMethod(FollowProblems follow, ProblemFeed feed) implements StreamingMethod {

    @Override
    public String name() {
        return "watch-all";
    }

    @Override
    public Outcome call(Map<String, String> params, Peer peer) {
        return Watching.begin(follow.start(), feed, peer);
    }
}
