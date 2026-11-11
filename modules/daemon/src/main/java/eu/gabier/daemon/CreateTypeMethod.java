package eu.gabier.daemon;

import eu.gabier.editing.application.CreatePackageInfo;
import eu.gabier.editing.application.CreateType;
import java.util.Map;
import java.util.Set;

/**
 * {@code create}: creates a file that holds an empty type of a kind, or the {@code package-info.java} of a package. The
 * parameters are {@code file} (absolute, in a source folder of the project: named after the type, or {@code
 * package-info.java}), {@code kind} ({@code class}, {@code interface}, {@code record}, {@code enum} or {@code package})
 * and {@code dryRun}. The file is written unless it is a dry run; the result says what was (or would be) created and
 * gives the unified diff ({@code ChangedFiles}). The refusals (a file that exists, a name that is not a type name, or
 * for the kind {@code package} not {@code package-info.java}, a file outside the project or the source folders) are the
 * problem objects they are; a {@code kind} that is not one of the five is an {@code invalid-parameter}. Named for the
 * command, not for the creation of a type.
 */
record CreateTypeMethod(CreateType create, CreatePackageInfo packageInfo) implements DaemonMethod {

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
        return new ChangeRequest(name(), params).run(create, packageInfo);
    }
}
