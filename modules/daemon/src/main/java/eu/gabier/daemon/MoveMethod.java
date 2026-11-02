package eu.gabier.daemon;

import eu.gabier.editing.application.MoveToPackage;
import eu.gabier.editing.domain.PackageName;
import java.util.Map;
import java.util.Set;

/**
 * {@code move}: moves Java sources to a package, creating the package when it does not exist. The parameters are {@code
 * files} (absolute paths, one per line), {@code package} (in dotted form) and {@code dryRun}. The files are moved and
 * edited, and the folder of the package made, unless it is a dry run; the result says what changed (or would) and gives
 * the unified diff ({@code ChangedFiles}). A name that is not a package, the refusals of the language server (a file
 * that cannot go to the package) and of the application of its answer are the problem objects they are.
 */
record MoveMethod(MoveToPackage move) implements DaemonMethod {

    private static final String PACKAGE = "package";

    @Override
    public String name() {
        return "move";
    }

    @Override
    public Set<String> parameters() {
        return Set.of(ChangeRequest.FILES, PACKAGE, ChangeRequest.DRY_RUN);
    }

    @Override
    public Outcome call(Map<String, String> params) {
        var request = new ChangeRequest(name(), params);

        return request.run(move, PackageName.of(request.text(PACKAGE)));
    }
}
