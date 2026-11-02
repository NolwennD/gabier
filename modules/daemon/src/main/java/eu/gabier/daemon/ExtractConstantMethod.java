package eu.gabier.daemon;

import eu.gabier.editing.application.ExtractConstant;
import eu.gabier.editing.domain.Position;
import eu.gabier.editing.domain.Range;
import java.util.Map;
import java.util.Set;

/**
 * {@code extract-constant}: replaces the expression of a selection by a new constant ({@code private static final}),
 * which the language server declares at the top of the class. The parameters are those of {@code extract-variable}:
 * {@code file} (absolute), the selection as {@code startLine}, {@code startColumn}, {@code endLine} and {@code
 * endColumn} (from 0, the end excluded), {@code name} (the name of the constant) and {@code dryRun}. The file is
 * written unless it is a dry run; the result says what changed (or would) and gives the unified diff ({@code
 * ChangedFiles}). The refusals of the language server (an expression that cannot be a constant, a name that is not
 * valid) and of the application of its answer are the problem objects they are.
 */
record ExtractConstantMethod(ExtractConstant extract) implements DaemonMethod {

    @Override
    public String name() {
        return "extract-constant";
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
