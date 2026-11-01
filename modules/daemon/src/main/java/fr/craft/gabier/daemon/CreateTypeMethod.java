package fr.craft.gabier.daemon;

import fr.craft.gabier.editing.application.CreateType;
import java.util.Map;
import java.util.Set;

/**
 * {@code create}: creates a file that holds an empty type of a kind. The parameters are {@code file} (absolute, named
 * after the type, in a source folder of the project), {@code kind} ({@code class}, {@code interface}, {@code record} or
 * {@code enum}) and {@code dryRun}. The file is written unless it is a dry run; the result says what was (or would be)
 * created and gives the unified diff ({@code ChangedFiles}). The refusals (a file that exists, a name that is not a type
 * name, a file outside the project or the source folders) are the problem objects they are; a {@code kind} that is not
 * one of the four is an {@code invalid-parameter}. Named for the command, not for the creation of a type.
 */
record CreateTypeMethod(CreateType create) implements DaemonMethod {

    @Override
    public String name() {
        return "create";
    }

    @Override
    public Set<String> parameters() {
        return Set.of(ChangeRequest.FILE, ChangeRequest.KIND, ChangeRequest.DRY_RUN);
    }

    @Override
    public Outcome call(Map<String, String> params) {
        return new ChangeRequest(name(), params).run(create);
    }
}
