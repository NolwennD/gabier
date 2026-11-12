package eu.gabier.daemon;

import eu.gabier.editing.application.FollowProblems;
import eu.gabier.editing.domain.DiagnosedFile;
import eu.gabier.protocol.DiagnosedFiles;
import eu.gabier.protocol.FileDiagnostics;
import eu.gabier.protocol.JsonRpc;
import java.util.List;

/**
 * What {@code watch} and {@code watch-all} share: the answer is the problems the client starts from, and the client is
 * then told, as notifications, the files whose problems changed ({@link eu.gabier.editing.domain.ProblemWatch}, one for
 * each client) each time the project is read again ({@link ProjectFeed}). The client leaves by disconnecting: the
 * feed stops reading for it, and for nobody once the last one is gone.
 */
final class Watching {

    private Watching() {}

    /**
     * Starts to follow for the client.
     *
     * @param following where the client starts: what it is answered, and what tells it the changes from there
     */
    static DaemonMethod.Outcome begin(FollowProblems.Following following, ProjectFeed feed, Peer peer) {
        var watch = following.watch();
        var listener = peer.listen(
                state -> problems(watch.changesIn(state)), (waiting, offered) -> offered, feed::unsubscribe);
        feed.subscribe(listener);

        return DaemonMethod.Outcome.of(diagnosed(following.told()).toJson());
    }

    /** The files as the result of {@code diagnostics} gives them. */
    static DiagnosedFiles diagnosed(List<DiagnosedFile> files) {
        return new DiagnosedFiles(files.stream()
                .map(file ->
                        new FileDiagnostics(file.file().toString(), DiagnosticsMethod.diagnoses(file.diagnostics())))
                .toList());
    }

    /** The notification that tells these files, none when no file changed. */
    private static List<String> problems(List<DiagnosedFile> changed) {
        return changed.isEmpty() ? List.of() : List.of(JsonRpc.problems(diagnosed(changed)));
    }
}
