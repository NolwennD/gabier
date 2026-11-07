package eu.gabier.daemon;

import eu.gabier.editing.application.ListCodeActions;
import eu.gabier.protocol.CodeActions;
import java.util.Map;
import java.util.Set;

/**
 * {@code actions}: what the language server offers on a range of a file, the fixes of the problems it holds and the
 * rewrites of the code it covers. The parameters are {@code file} (absolute: the daemon has no working directory that
 * means anything to the client) and the range as {@code startLine}, {@code startColumn}, {@code endLine} and {@code
 * endColumn} (from 0, as the protocol counts, the end excluded; a point is a range that ends where it starts). The
 * result lists each action with its kind and its title, and the command of gabier that does the same when there is one,
 * in the server's order ({@code CodeActions}). Nothing is written. The refusals of the language server (not ready, file
 * outside its project) are the problem objects they are.
 */
record CodeActionsMethod(ListCodeActions list) implements DaemonMethod {

    @Override
    public String name() {
        return "actions";
    }

    @Override
    public Set<String> parameters() {
        return Set.of(
                ChangeRequest.FILE,
                ChangeRequest.START_LINE,
                ChangeRequest.START_COLUMN,
                ChangeRequest.END_LINE,
                ChangeRequest.END_COLUMN);
    }

    @Override
    public Outcome call(Map<String, String> params) {
        var request = new ChangeRequest(name(), params);
        var offered = list.list(request.file(), request.range());

        return Outcome.of(new CodeActions(offered.actions().stream()
                        .map(action -> new CodeActions.Action(
                                action.kind(), action.title(), action.command().orElse("")))
                        .toList())
                .toJson());
    }
}
