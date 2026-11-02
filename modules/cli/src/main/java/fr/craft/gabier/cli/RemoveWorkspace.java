package fr.craft.gabier.cli;

import fr.craft.gabier.protocol.Json;
import fr.craft.gabier.protocol.KeptWorkspaces;
import fr.craft.gabier.protocol.TerminalText;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import org.jspecify.annotations.Nullable;

/**
 * {@code remove-workspace}: removes the jdtls workspace that the daemon kept for a project ({@link KeptWorkspaces}). It
 * talks to no daemon and starts none, so it works when the project's daemon is stopped, which is when its folder is
 * about to be deleted: it computes the folder of the workspace as the daemon does, from the root of the project, and
 * the lock that a running daemon holds on it says whether it is in use.
 */
final class RemoveWorkspace implements Capability {

    private static final String IN_USE =
            "workspace-in-use: a daemon runs jdtls on the workspace, which is left as it is";

    static final Command COMMAND = Command.called("remove-workspace")
            .summary("remove the jdtls workspace kept for a project, with no daemon")
            .usage("gabier [--json] remove-workspace [<project folder>]")
            .example("""
                $ gabier remove-workspace /work/demo-worktree
                Removed the jdtls workspace /home/me/.cache/gabier/workspaces/6f1c0d4e of /work/demo-worktree\
                """)
            .standalone(Refusal.USAGE, IN_USE)
            .withNotes("""
                The folder is the one of the project, else GABIER_PROJECT, else the working directory, as for \
                gabier-daemon. The workspaces are kept in the folder GABIER_JDTLS_WORKSPACES names: when it is not set \
                nothing is kept and nothing is removed. It is not an error to have nothing to remove, nor for the \
                folder of the project to be gone.\
                """);

    @Override
    public Command command() {
        return COMMAND;
    }

    @Override
    public void run(Invocation invocation) {
        var operands = invocation.arguments().operands();
        if (operands.size() > 1) {
            throw UsageException.unsupportedArguments(invocation.arguments().all());
        }
        var root = KeptWorkspaces.rootOf(project(operands.isEmpty() ? "" : operands.getFirst(), invocation));
        var folder = KeptWorkspaces.folder(invocation.environment());
        if (folder.isEmpty()) {
            invocation.out().println(invocation.json() ? json(false, root, null) : text(notKept()));

            return;
        }
        var workspace = KeptWorkspaces.of(folder.get(), root);
        var removed = remove(folder.get(), root);
        invocation
                .out()
                .println(invocation.json() ? json(removed, root, workspace) : text(message(removed, root, workspace)));
    }

    /** The folder named, else {@code GABIER_PROJECT}, else the working directory: the one the daemon would serve. */
    private static Path project(String named, Invocation invocation) {
        var given = named.isBlank() ? invocation.environment().getOrDefault("GABIER_PROJECT", "") : named;

        return Path.of(given).toAbsolutePath();
    }

    private static boolean remove(Path folder, Path root) {
        try {
            return KeptWorkspaces.remove(folder, root);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static String notKept() {
        return "No jdtls workspace is kept: " + KeptWorkspaces.VARIABLE + " is not set";
    }

    private static String message(boolean removed, Path root, Path workspace) {
        return removed
                ? "Removed the jdtls workspace " + workspace + " of " + root
                : "No jdtls workspace is kept for " + root + ": nothing to remove";
    }

    /** What a path makes of the terminal is not what it says: the text is neutralised. */
    private static String text(String line) {
        return TerminalText.neutralise(line);
    }

    private static String json(boolean removed, Path root, @Nullable Path workspace) {
        return "{\"removed\":" + removed + ",\"project\":" + Json.quote(root.toString())
                + (workspace == null ? "" : ",\"workspace\":" + Json.quote(workspace.toString())) + "}";
    }
}
