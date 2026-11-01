package fr.craft.gabier.daemon;

import fr.craft.gabier.editing.application.MoveTypeToFile;
import fr.craft.gabier.editing.domain.Position;
import java.util.Map;
import java.util.Set;

/**
 * {@code move-type-to-file}: makes a top-level type, in a file of its own, of the nested type that encloses a position
 * of a file. The parameters are {@code file} (absolute), {@code line} and {@code column} (from 0, as the protocol
 * counts) and {@code dryRun}. The files are created and edited unless it is a dry run; the result says what changed (or
 * would) and gives the unified diff ({@code ChangedFiles}). The refusals of the language server (no nested type there)
 * and of the application of its answer (the new file exists) are the problem objects they are.
 */
record MoveTypeToFileMethod(MoveTypeToFile move) implements DaemonMethod {

    private static final String LINE = "line";
    private static final String COLUMN = "column";

    @Override
    public String name() {
        return "move-type-to-file";
    }

    @Override
    public Set<String> parameters() {
        return Set.of(ChangeRequest.FILE, LINE, COLUMN, ChangeRequest.DRY_RUN);
    }

    @Override
    public Outcome call(Map<String, String> params) {
        var request = new ChangeRequest(name(), params);

        return request.run(move, new Position(request.number(LINE), request.number(COLUMN)));
    }
}
