package fr.craft.gabier.daemon;

import fr.craft.gabier.editing.application.CleanUpFile;
import java.util.Map;
import java.util.Set;

/**
 * {@code cleanup}: applies clean-ups of the language server to a file (for instance {@code stringConcatToTextBlock},
 * which rewrites string concatenations into text blocks), given as the parameters {@code file} (absolute: the daemon
 * has no working directory that means anything to the client), {@code cleanUps} (the identifiers, one per line, applied
 * in the order given) and {@code dryRun} ({@code true} or {@code false}). The file is written unless it is a dry run;
 * the result says what changed (or would) and gives the unified diff ({@code ChangedFiles}), none of it for a file with
 * nothing to clean up. An identifier the language server does not know, the refusals of the language server and of the
 * application of its answer are the problem objects they are.
 */
record CleanUpMethod(CleanUpFile cleanUp) implements DaemonMethod {

    @Override
    public String name() {
        return "cleanup";
    }

    @Override
    public Set<String> parameters() {
        return Set.of(ChangeRequest.FILE, ChangeRequest.CLEAN_UPS, ChangeRequest.DRY_RUN);
    }

    @Override
    public Outcome call(Map<String, String> params) {
        return new ChangeRequest(name(), params).run(cleanUp);
    }
}
