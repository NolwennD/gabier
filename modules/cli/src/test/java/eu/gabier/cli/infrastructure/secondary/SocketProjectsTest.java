package eu.gabier.cli.infrastructure.secondary;

import static org.assertj.core.api.Assertions.assertThat;

import eu.gabier.protocol.SocketLocation;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The projects of a server that is not run in one: a file belongs to the nearest folder above it with a {@code .git}. */
class SocketProjectsTest {

    @TempDir
    Path directory;

    private Path clone;
    private Path worktree;
    private Path elsewhere;

    @BeforeEach
    void aCloneWithAWorktreeInsideItAndAFolderWithNoGit() throws IOException {
        var root = directory.toRealPath();
        clone = Files.createDirectories(root.resolve("clone"));
        Files.createDirectory(clone.resolve(".git"));
        worktree = Files.createDirectories(clone.resolve("trees/tree"));
        Files.writeString(worktree.resolve(".git"), "gitdir: ../../.git/worktrees/tree\n");
        elsewhere = Files.createDirectories(root.resolve("elsewhere/src"));
        Files.createDirectories(clone.resolve("src"));
        Files.writeString(clone.resolve("src/A.java"), "class A {}\n");
    }

    private SocketProjects projects(Map<String, String> environment) {
        return new SocketProjects(environment, new PrintStream(PrintStream.nullOutputStream()), Duration.ofSeconds(1));
    }

    private SocketProjects projectsLaunchedIn(Path project) {
        return projects(Map.of(SocketLocation.PROJECT, project.toString(), "XDG_RUNTIME_DIR", directory.toString()));
    }

    @Test
    void theProjectOfAFileIsTheNearestFolderAboveItWithAGitFolder() {
        var projects = projectsLaunchedIn(elsewhere);

        assertThat(projects.projectOf(clone.resolve("src/A.java"))).isEqualTo(clone);
    }

    @Test
    void aWorktreeInsideAClonePrevailsOverTheClone() {
        var projects = projectsLaunchedIn(clone);

        assertThat(projects.projectOf(worktree.resolve("src/B.java"))).isEqualTo(worktree);
    }

    @Test
    void aFileThatDoesNotExistYetBelongsToTheProjectItIsToBeCreatedIn() {
        var projects = projectsLaunchedIn(elsewhere);

        assertThat(projects.projectOf(worktree.resolve("new/package/C.java"))).isEqualTo(worktree);
    }

    @Test
    void aFileThatDoesNotExistYetBelowASymlinkedFolderGoesToTheSameProjectAsAFileThatExists() throws IOException {
        var link = Files.createSymbolicLink(directory.toRealPath().resolve("elsewhere/link"), clone);
        var projects = projectsLaunchedIn(elsewhere);

        var existing = projects.projectOf(link.resolve("src/A.java"));
        var notYet = projects.projectOf(link.resolve("src/new/package/C.java"));

        assertThat(existing).isEqualTo(clone);
        assertThat(notYet).isEqualTo(existing);
    }

    @Test
    void aFileThatDoesNotExistYetBelowAFolderThatIsASymlinkToAWorktreeBelongsToThatWorktree() throws IOException {
        var link = Files.createSymbolicLink(directory.toRealPath().resolve("elsewhere/tree"), worktree);
        var projects = projectsLaunchedIn(clone);

        assertThat(projects.projectOf(link.resolve("a/b/C.java"))).isEqualTo(worktree);
    }

    @Test
    void aFileThatDoesNotExistYetUnderADanglingSymlinkKeepsTheNameOfTheLink() throws IOException {
        var dangling = Files.createSymbolicLink(
                directory.toRealPath().resolve("elsewhere/dangling"), directory.resolve("nowhere"));
        var projects = projectsLaunchedIn(clone);

        assertThat(projects.projectOf(dangling.resolve("C.java"))).isEqualTo(clone);
    }

    @Test
    void aFileWithNoGitFolderAboveItBelongsToTheProjectOfTheLaunch() {
        var projects = projectsLaunchedIn(clone);

        assertThat(projects.projectOf(elsewhere.resolve("D.java"))).isEqualTo(clone);
    }

    @Test
    void theProjectOfTheLaunchIsTheOneTheEnvironmentNamesOrTheWorkingDirectoryHas() {
        var named = projectsLaunchedIn(worktree);
        var fromTheFolder = projects(Map.of());

        assertThat(named.launched()).isEqualTo(worktree);
        assertThat(fromTheFolder.launched())
                .isEqualTo(SocketLocation.projectRoot(Map.of(), Path.of("").toAbsolutePath()));
    }

    @Test
    void everyProjectHasADaemonOfItsOwnOnTheSocketOfItsFolderBuiltOnceAndKept() {
        var projects = projectsLaunchedIn(clone);
        var environment = Map.of("XDG_RUNTIME_DIR", directory.toString());

        var inClone = projects.daemonOf(clone);
        var inWorktree = projects.daemonOf(worktree);

        assertThat(projects.daemonOf(clone)).isSameAs(inClone);
        assertThat(inClone.socket()).isNotEqualTo(inWorktree.socket());
        assertThat(inWorktree.socket())
                .isEqualTo(SocketLocation.resolve(withProject(environment, worktree), "1", directory));
    }

    @Test
    void aSocketThatTheEnvironmentReplacesIsTheSocketOfEveryProject() {
        var socket = directory.resolve("one.sock");
        var projects = projects(Map.of(SocketLocation.OVERRIDE, socket.toString()));

        assertThat(projects.daemonOf(clone).socket()).isEqualTo(socket);
        assertThat(projects.daemonOf(worktree).socket()).isEqualTo(socket);
    }

    private static Map<String, String> withProject(Map<String, String> environment, Path project) {
        var inProject = new HashMap<>(environment);
        inProject.put(SocketLocation.PROJECT, project.toString());

        return inProject;
    }
}
