package fr.craft.gabier.cli;

import static org.assertj.core.api.Assertions.assertThat;

import fr.craft.gabier.protocol.KeptWorkspaces;
import fr.craft.gabier.protocol.WorkspaceLock;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * {@code remove-workspace}: removes the jdtls workspace that was kept for a project. It needs no daemon (there is none
 * here, and no socket is given), only the folder the workspaces are kept in, and the root of the project to compute the
 * folder of its workspace as the daemon does.
 */
class RemovingAWorkspaceTest extends CliTest {

    private Path kept() {
        return directory.resolve("kept");
    }

    private Path project(String name) throws IOException {
        return Files.createDirectories(directory.resolve(name)).toRealPath();
    }

    /** What the daemon left for the root: its kept workspace, with what jdtls writes in it. */
    private Path workspaceOf(Path root) throws IOException {
        Path workspace = KeptWorkspaces.of(kept(), root);
        Files.createDirectories(workspace.resolve(".metadata/.plugins"));
        Files.writeString(workspace.resolve(".metadata/.plugins/state"), "imported");

        return workspace;
    }

    private Map<String, String> keptIn() {
        return Map.of("GABIER_JDTLS_WORKSPACES", kept().toString());
    }

    @Test
    void removesTheKeptWorkspaceOfTheProjectThatIsNamed() throws IOException {
        var root = project("worktree");
        var workspace = workspaceOf(root);
        var other = workspaceOf(project("another"));

        assertThat(run(keptIn(), "remove-workspace", root.toString())).isZero();

        assertThat(workspace).doesNotExist();
        assertThat(other).isDirectory();
        assertThat(out())
                .isEqualTo("Removed the jdtls workspace " + workspace + " of " + root + System.lineSeparator());
        assertThat(err()).isEmpty();
    }

    @Test
    void asksNoDaemonAndStartsNone() throws IOException {
        var root = project("worktree");
        workspaceOf(root);
        var marker = directory.resolve("started");
        FakeDaemonScripts.install(directory.resolve("bin"), "echo started > '" + marker + "'");
        var environment = Map.of(
                "GABIER_JDTLS_WORKSPACES",
                kept().toString(),
                "GABIER_SOCKET",
                socket().toString(),
                "PATH",
                directory.resolve("bin").toString());

        assertThat(run(environment, "remove-workspace", root.toString())).isZero();

        assertThat(marker).doesNotExist();
        assertThat(socket()).doesNotExist();
    }

    @Test
    void doesNothingTheSecondTimeAndSaysSo() throws IOException {
        var root = project("worktree");
        workspaceOf(root);
        run(keptIn(), "remove-workspace", root.toString());
        out.reset();

        assertThat(run(keptIn(), "remove-workspace", root.toString())).isZero();

        assertThat(out())
                .isEqualTo("No jdtls workspace is kept for " + root + ": nothing to remove" + System.lineSeparator());
        assertThat(err()).isEmpty();
    }

    @Test
    void removesTheWorkspaceOfAProjectWhoseFolderIsGone() throws IOException {
        var root = directory.resolve("deleted-worktree");
        var workspace = workspaceOf(root);

        assertThat(run(keptIn(), "remove-workspace", root.toString())).isZero();

        assertThat(workspace).doesNotExist();
    }

    @Test
    void takesTheFolderThatTheDaemonWouldServeWhenNoneIsNamed() throws IOException {
        var root = project("worktree");
        var workspace = workspaceOf(root);
        var environment = Map.of("GABIER_JDTLS_WORKSPACES", kept().toString(), "GABIER_PROJECT", root.toString());

        assertThat(run(environment, "remove-workspace")).isZero();

        assertThat(workspace).doesNotExist();
    }

    @Test
    void takesTheWorkingDirectoryWhenNothingNamesAProject() throws IOException {
        var root = Path.of("").toRealPath();
        var workspace = workspaceOf(root);

        assertThat(run(keptIn(), "remove-workspace")).isZero();

        assertThat(workspace).doesNotExist();
    }

    @Test
    void doesNothingAndSaysWhyWhenNoFolderIsGivenToKeepWorkspaces() throws IOException {
        var root = project("worktree");

        assertThat(run("remove-workspace", root.toString())).isZero();

        assertThat(out())
                .isEqualTo("No jdtls workspace is kept: GABIER_JDTLS_WORKSPACES is not set" + System.lineSeparator());
    }

    @Test
    void isRefusedWhileADaemonRunsJdtlsOnTheWorkspaceAndLeavesItAsItIs() throws IOException {
        var root = project("worktree");
        var workspace = workspaceOf(root);

        try (var held = WorkspaceLock.acquire(workspace).orElseThrow()) {
            assertThat(held).isNotNull();
            assertThat(run(keptIn(), "remove-workspace", root.toString())).isEqualTo(2);
        }

        assertThat(out()).isEmpty();
        assertThat(err())
                .startsWith("Workspace in use: The jdtls workspace " + workspace + " of " + root + " is in use")
                .contains("(workspace-in-use; ");
        assertThat(workspace.resolve(".metadata/.plugins/state")).hasContent("imported");
    }

    @Test
    void failsWhenTheLockOfTheWorkspaceCannotBeTriedForNothingProvesNobodyUsesIt() throws IOException {
        var root = project("worktree");
        var workspace = workspaceOf(root);
        var lock = Files.createFile(workspace.resolve(".gabier-lock"));
        Files.setPosixFilePermissions(lock, PosixFilePermissions.fromString("---------"));

        assertThat(run(keptIn(), "remove-workspace", root.toString())).isEqualTo(1);

        assertThat(workspace.resolve(".metadata/.plugins/state")).hasContent("imported");
    }

    @Test
    void saysItAsJsonWithTheJsonOption() throws IOException {
        var root = project("worktree");
        var workspace = workspaceOf(root);

        assertThat(run(keptIn(), "--json", "remove-workspace", root.toString())).isZero();
        assertThat(out())
                .isEqualTo("{\"removed\":true,\"project\":\"" + root + "\",\"workspace\":\"" + workspace + "\"}"
                        + System.lineSeparator());
        out.reset();

        assertThat(run(keptIn(), "--json", "remove-workspace", root.toString())).isZero();
        assertThat(out())
                .isEqualTo("{\"removed\":false,\"project\":\"" + root + "\",\"workspace\":\"" + workspace + "\"}"
                        + System.lineSeparator());
        out.reset();

        assertThat(run("--json", "remove-workspace", root.toString())).isZero();
        assertThat(out()).isEqualTo("{\"removed\":false,\"project\":\"" + root + "\"}" + System.lineSeparator());
    }

    @Test
    void refusesMoreThanOneFolder() {
        assertThat(run(keptIn(), "remove-workspace", "a", "b")).isEqualTo(2);

        assertThat(err()).isEqualTo(usageText("remove-workspace a b"));
    }

    @Test
    void takesTheNeutralisedPathsToTheTerminal() throws IOException {
        var root = project("worktree\u001b[31m");
        workspaceOf(root);

        assertThat(run(keptIn(), "remove-workspace", root.toString())).isZero();

        assertThat(out()).doesNotContain("\u001b");
    }
}
