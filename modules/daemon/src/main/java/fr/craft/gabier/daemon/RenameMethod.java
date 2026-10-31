package fr.craft.gabier.daemon;

import fr.craft.gabier.editing.application.Rename;
import fr.craft.gabier.editing.application.RenameTarget;
import fr.craft.gabier.editing.domain.Position;
import java.util.Map;
import java.util.Set;

/**
 * {@code rename}: renames the symbol at a position of a file, given as the parameters {@code file} (absolute: the
 * daemon has no working directory that means anything to the client), {@code line} and {@code column} (from 0, as the
 * protocol counts), {@code newName} and {@code dryRun} ({@code true} or {@code false}). The files are written unless it
 * is a dry run; the result says what changed (or would) and gives the unified diff ({@code ChangedFiles}). The
 * refusals of the language server and of the application of its answer are the problem objects they are.
 */
record RenameMethod(Rename rename) implements DaemonMethod {

    private static final String LINE = "line";
    private static final String COLUMN = "column";

    @Override
    public String name() {
        return "rename";
    }

    @Override
    public Set<String> parameters() {
        return Set.of(ChangeRequest.FILE, LINE, COLUMN, ChangeRequest.NEW_NAME, ChangeRequest.DRY_RUN);
    }

    @Override
    public Outcome call(Map<String, String> params) {
        var request = new ChangeRequest(name(), params);
        var target = new RenameTarget.At(request.file(), new Position(request.number(LINE), request.number(COLUMN)));

        return request.run(rename, target);
    }
}
