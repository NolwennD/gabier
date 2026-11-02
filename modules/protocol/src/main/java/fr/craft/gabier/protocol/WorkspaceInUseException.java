package fr.craft.gabier.protocol;

import fr.craft.gabier.shared.error.domain.GeneratorException;
import java.nio.file.Path;

/** The kept workspace of the project is in use by a daemon that is running: Eclipse allows one process per workspace. */
public final class WorkspaceInUseException extends GeneratorException {

    private WorkspaceInUseException(GeneratorExceptionBuilder builder) {
        super(builder);
    }

    public static WorkspaceInUseException of(Path project, Path workspace) {
        return new WorkspaceInUseException(GeneratorException.badRequest(ProtocolErrorKey.WORKSPACE_IN_USE)
                .message("The jdtls workspace " + workspace + " of " + project
                        + " is in use: a daemon holds its lock (" + WorkspaceLock.FILE + "), most likely the one of"
                        + " another daemon on the same project. Stop that daemon, or unset "
                        + KeptWorkspaces.VARIABLE + " to give each daemon a temporary workspace of its own")
                .addParameter("project", project.toString())
                .addParameter("workspace", workspace.toString())
                .addParameter("variable", KeptWorkspaces.VARIABLE));
    }
}
