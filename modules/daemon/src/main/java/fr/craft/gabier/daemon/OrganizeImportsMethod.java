package fr.craft.gabier.daemon;

import fr.craft.gabier.editing.application.OrganizeImports;
import java.util.Map;
import java.util.Set;

/**
 * {@code organize-imports}: adds the missing imports of a file, removes the unused ones and orders the rest as the
 * project keeps them, given as the parameters {@code file} (absolute: the daemon has no working directory that means
 * anything to the client) and {@code dryRun} ({@code true} or {@code false}). The file is written unless it is a dry
 * run; the result says what changed (or would) and gives the unified diff ({@code ChangedFiles}), none of it for a file
 * whose imports are already in order. The refusals of the language server and of the application of its answer are the
 * problem objects they are.
 */
record OrganizeImportsMethod(OrganizeImports organize) implements DaemonMethod {

    @Override
    public String name() {
        return "organize-imports";
    }

    @Override
    public Set<String> parameters() {
        return Set.of(ChangeRequest.FILE, ChangeRequest.DRY_RUN);
    }

    @Override
    public Outcome call(Map<String, String> params) {
        return new ChangeRequest(name(), params).run(organize);
    }
}
