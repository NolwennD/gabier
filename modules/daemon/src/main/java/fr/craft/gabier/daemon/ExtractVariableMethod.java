package fr.craft.gabier.daemon;

import fr.craft.gabier.editing.application.ExtractVariable;
import fr.craft.gabier.editing.domain.Position;
import fr.craft.gabier.editing.domain.Range;
import java.util.Map;
import java.util.Set;

/**
 * {@code extract-variable}: replaces the expression of a selection by a new local variable, declared before the
 * statement that holds it. The parameters are {@code file} (absolute: the daemon has no working directory that means
 * anything to the client), the selection as {@code startLine}, {@code startColumn}, {@code endLine} and {@code
 * endColumn} (from 0, as the protocol counts, the end excluded), {@code name} (the name of the variable) and {@code
 * dryRun} ({@code true} or {@code false}). The file is written unless it is a dry run; the result says what changed
 * (or would) and gives the unified diff ({@code ChangedFiles}). The refusals of the language server (a selection that
 * is not an expression, a name that is not valid) and of the application of its answer are the problem objects they
 * are.
 */
record ExtractVariableMethod(ExtractVariable extract) implements DaemonMethod {

    private static final String START_LINE = "startLine";
    private static final String START_COLUMN = "startColumn";
    private static final String END_LINE = "endLine";
    private static final String END_COLUMN = "endColumn";

    @Override
    public String name() {
        return "extract-variable";
    }

    @Override
    public Set<String> parameters() {
        return Set.of(
                ChangeRequest.FILE,
                START_LINE,
                START_COLUMN,
                END_LINE,
                END_COLUMN,
                ChangeRequest.NAME,
                ChangeRequest.DRY_RUN);
    }

    @Override
    public Outcome call(Map<String, String> params) {
        var request = new ChangeRequest(name(), params);
        var selection = new Range(
                new Position(request.number(START_LINE), request.number(START_COLUMN)),
                new Position(request.number(END_LINE), request.number(END_COLUMN)));

        return request.run(extract, selection);
    }
}
