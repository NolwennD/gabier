package fr.craft.gabier.daemon;

import fr.craft.gabier.editing.application.ExtractMethod;
import fr.craft.gabier.editing.domain.Position;
import fr.craft.gabier.editing.domain.Range;
import java.util.Map;
import java.util.Set;

/**
 * {@code extract-method}: replaces the statements, or the expression, of a selection by a call to a new method, which
 * the language server declares with the parameters and the return value it works out. The parameters are those of
 * {@code extract-variable}: {@code file} (absolute), the selection as {@code startLine}, {@code startColumn}, {@code
 * endLine} and {@code endColumn} (from 0, the end excluded), {@code name} (the name of the method) and {@code dryRun}.
 * The file is written unless it is a dry run; the result says what changed (or would) and gives the unified diff
 * ({@code ChangedFiles}). The refusals of the language server (a selection it cannot extract, a name that is not valid or
 * that another method of the class has with the same parameters) and of the application of its answer are the problem
 * objects they are.
 */
record ExtractMethodMethod(ExtractMethod extract) implements DaemonMethod {

    @Override
    public String name() {
        return "extract-method";
    }

    @Override
    public Set<String> parameters() {
        return Set.of(
                ChangeRequest.FILE,
                ChangeRequest.START_LINE,
                ChangeRequest.START_COLUMN,
                ChangeRequest.END_LINE,
                ChangeRequest.END_COLUMN,
                ChangeRequest.NAME,
                ChangeRequest.DRY_RUN);
    }

    @Override
    public Outcome call(Map<String, String> params) {
        var request = new ChangeRequest(name(), params);
        var selection = new Range(
                new Position(request.number(ChangeRequest.START_LINE), request.number(ChangeRequest.START_COLUMN)),
                new Position(request.number(ChangeRequest.END_LINE), request.number(ChangeRequest.END_COLUMN)));

        return request.run(extract, selection);
    }
}
