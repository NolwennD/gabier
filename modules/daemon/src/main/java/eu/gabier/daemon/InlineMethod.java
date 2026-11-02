package eu.gabier.daemon;

import eu.gabier.editing.application.Inline;
import eu.gabier.editing.domain.Position;
import java.util.Map;
import java.util.Set;

/**
 * {@code inline}: replaces a local variable, a method or a constant by its value, at every use, and removes its
 * declaration (a position on a use of a method or a constant inlines that use only). The parameters are {@code file}
 * (absolute), {@code line} and {@code column} (from 0, as the protocol counts) and {@code dryRun}. The files are edited
 * unless it is a dry run; the result says what changed (or would) and gives the unified diff ({@code ChangedFiles}). The
 * refusals of the language server (nothing to inline there) are the problem objects they are. Named for the command, not
 * for the inlining of a method.
 */
record InlineMethod(Inline inline) implements DaemonMethod {

    private static final String LINE = "line";
    private static final String COLUMN = "column";

    @Override
    public String name() {
        return "inline";
    }

    @Override
    public Set<String> parameters() {
        return Set.of(ChangeRequest.FILE, LINE, COLUMN, ChangeRequest.DRY_RUN);
    }

    @Override
    public Outcome call(Map<String, String> params) {
        var request = new ChangeRequest(name(), params);

        return request.run(inline, new Position(request.number(LINE), request.number(COLUMN)));
    }
}
