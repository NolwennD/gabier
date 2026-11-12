package eu.gabier.daemon;

import eu.gabier.editing.application.FollowProblems;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@code watch}: follows the problems of files, given as the parameter {@code files} (as for {@code diagnostics}:
 * absolute paths, one per line, at least one, none blank). The result is the one of {@code diagnostics} (one element per
 * file, in the order given, a clean file included); after it the daemon writes the notifications of {@link
 * WatchAllMethod}, for those files only. The refusals are those of {@code diagnostics}, for the whole call, and then
 * nothing is followed.
 */
record WatchMethod(FollowProblems follow, ProjectFeed feed) implements StreamingMethod {

    @Override
    public String name() {
        return "watch";
    }

    @Override
    public Set<String> parameters() {
        return Set.of(ChangeRequest.FILES);
    }

    @Override
    public Outcome call(Map<String, String> params, Peer peer) {
        List<Path> files = new ChangeRequest(name(), params).files();
        if (files.isEmpty() || files.stream().anyMatch(file -> file.toString().isBlank())) {
            throw ParameterException.invalid(name(), ChangeRequest.FILES);
        }

        return Watching.begin(follow.start(files), feed, peer);
    }
}
