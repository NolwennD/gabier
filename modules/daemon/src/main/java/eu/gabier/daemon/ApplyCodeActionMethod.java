package eu.gabier.daemon;

import eu.gabier.editing.application.ApplyCodeAction;
import java.util.Map;
import java.util.Set;

/**
 * {@code apply-action}: applies the action of the language server that has the given title among the ones it offers
 * on a range. The parameters are {@code file} (absolute), the range as {@code startLine}, {@code startColumn}, {@code
 * endLine} and {@code endColumn} (from 0, as the protocol counts, the end excluded), {@code title} (the title the server
 * gives the action, as {@code actions} lists it) and {@code dryRun}. The files are edited unless it is a dry run;
 * the result says what changed (or would) and gives the unified diff ({@code ChangedFiles}). The refusals of the
 * language server (an action that is a command for an editor and gives no edit) and of the title (none has it, or several
 * do) are the problem objects they are.
 */
record ApplyCodeActionMethod(ApplyCodeAction apply) implements DaemonMethod {

    @Override
    public String name() {
        return "apply-action";
    }

    @Override
    public Set<String> parameters() {
        return Set.of(
                ChangeRequest.FILE,
                ChangeRequest.START_LINE,
                ChangeRequest.START_COLUMN,
                ChangeRequest.END_LINE,
                ChangeRequest.END_COLUMN,
                ChangeRequest.TITLE,
                ChangeRequest.DRY_RUN);
    }

    @Override
    public Outcome call(Map<String, String> params) {
        var request = new ChangeRequest(name(), params);

        return request.run(apply, request.range());
    }
}
