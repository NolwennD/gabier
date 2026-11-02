package fr.craft.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.craft.gabier.protocol.Directories;
import fr.craft.gabier.protocol.KeptWorkspaces;
import fr.craft.gabier.protocol.WorkspaceInUseException;
import fr.craft.gabier.protocol.WorkspaceLock;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Where jdtls keeps its workspace, and what becomes of it: temporary by default, kept when a folder is given. */
class JdtlsWorkspaceTest {

    private static final Duration GRACE = Duration.ofMinutes(5);

    @TempDir
    Path folder;

    private final List<String> log = new ArrayList<>();

    private Path temporaryFolder() throws IOException {
        return Files.createDirectories(folder.resolve("tmp"));
    }

    private Path project(String name) throws IOException {
        return Files.createDirectories(folder.resolve(name));
    }

    /** The folder of a daemon that is gone: what jdtls left, and the lock file nobody holds. */
    private Path leftBehind(Path parent, String name) throws IOException {
        Path data = Files.createDirectories(parent.resolve(name + "/.metadata/.plugins"))
                .getParent()
                .getParent();
        Files.writeString(data.resolve(".metadata/.plugins/state"), "imported");
        Files.createFile(data.resolve(".gabier-lock"));
        return data;
    }

    @Nested
    class Temporary {

        private JdtlsWorkspace workspace() throws IOException {
            return JdtlsWorkspace.temporary(temporaryFolder());
        }

        @Test
        void opensAFolderOfItsOwnInTheTemporaryFolder() throws IOException {
            var first = workspace().open(project("a"), GRACE, log::add);
            var second = workspace().open(project("a"), GRACE, log::add);

            assertThat(first.data()).isNotEqualTo(second.data()).isDirectory().hasParent(temporaryFolder());
            assertThat(first.data().getFileName().toString()).startsWith("gabier-jdtls-data-");
            assertThat(workspace().isKept()).isFalse();
        }

        @Test
        void locksTheFolderUntilItIsReleased() throws IOException {
            var opened = workspace().open(project("a"), GRACE, log::add);

            assertThat(WorkspaceLock.isHeld(opened.data())).isTrue();

            workspace().release(opened);

            assertThat(WorkspaceLock.isHeld(opened.data())).isFalse();
        }

        @Test
        void keepsTheLockOnItsOwnFolderForAnotherDaemonThatSweepsTheTemporaryFolder() throws Exception {
            var opened = workspace().open(project("a"), GRACE, log::add);

            assertThat(AnotherProcess.findsLocked(opened.data().resolve(".gabier-lock"), folder))
                    .as("the lock, seen by another process, after the sweep that opening did")
                    .isTrue();
            workspace().open(project("b"), GRACE, log::add);

            assertThat(opened.data()).isDirectory();
            assertThat(AnotherProcess.findsLocked(opened.data().resolve(".gabier-lock"), folder))
                    .isTrue();
        }

        @Test
        void usesTheTemporaryFolderOfTheSystemByDefault() throws IOException {
            var opened = JdtlsWorkspace.temporary().open(project("a"), GRACE, log::add);

            try {
                assertThat(opened.data()).hasParent(Path.of(System.getProperty("java.io.tmpdir")));
            } finally {
                JdtlsWorkspace.temporary().release(opened);
            }
        }

        @Test
        void removesTheFolderWhenItIsReleased() throws IOException {
            var opened = workspace().open(project("a"), GRACE, log::add);
            Files.writeString(opened.data().resolve("state"), "imported");

            workspace().release(opened);

            assertThat(opened.data()).doesNotExist();
        }

        @Test
        void removesTheDataFoldersOfTheDaemonsThatWereKilledButNotTheOneOfAJdtlsThatRuns() throws IOException {
            var killed = leftBehind(temporaryFolder(), "gabier-jdtls-data-killed");
            var running = leftBehind(temporaryFolder(), "gabier-jdtls-data-running");

            try (var held = WorkspaceLock.acquire(running).orElseThrow()) {
                workspace().open(project("a"), GRACE, log::add);

                assertThat(held).isNotNull();
            }

            assertThat(killed).doesNotExist();
            assertThat(running).isDirectory();
            assertThat(log).containsExactly("removed " + killed + ", which no jdtls uses any more");
        }

        @Test
        void takesTheGraceAsTheTimeAJdtlsMayTakeToStart() throws IOException {
            var starting = Files.createDirectories(temporaryFolder().resolve("gabier-jdtls-data-starting"));
            Files.setLastModifiedTime(starting, FileTime.from(Instant.now().minus(Duration.ofMinutes(10))));

            workspace().open(project("a"), Duration.ofMinutes(20), log::add);

            assertThat(starting).isDirectory();
        }
    }

    @Nested
    class Kept {

        private Path keptFolder() {
            return folder.resolve("kept");
        }

        private JdtlsWorkspace workspace() throws IOException {
            return JdtlsWorkspace.temporary(temporaryFolder()).keptIn(keptFolder());
        }

        @Test
        void isTheWorkspaceOfTheRootInTheKeptFolderThatTheCommandAlsoComputes() throws IOException {
            var opened = workspace().open(project("a"), GRACE, log::add);

            assertThat(opened.data()).isDirectory().isEqualTo(KeptWorkspaces.of(keptFolder(), project("a")));
            assertThat(workspace().isKept()).isTrue();
        }

        @Test
        void isTheSameFolderEachTimeForTheSameRootAndAnotherForAnotherRoot() throws IOException {
            var workspace = workspace();
            var first = workspace.open(project("a"), GRACE, log::add);
            workspace.release(first);

            var again = workspace.open(project("a"), GRACE, log::add);
            var other = workspace.open(project("b"), GRACE, log::add);

            assertThat(again.data()).isEqualTo(first.data());
            assertThat(other.data()).isNotEqualTo(first.data());
        }

        @Test
        void tellsWhichProjectItIsFor() throws IOException {
            var opened = workspace().open(project("a"), GRACE, log::add);

            assertThat(opened.data().resolve(".gabier-root"))
                    .hasContent(project("a").toString());
        }

        @Test
        void staysWithEverythingJdtlsLeftInItWhenItIsReleasedAndIsFoundAgain() throws IOException {
            var workspace = workspace();
            var opened = workspace.open(project("a"), GRACE, log::add);
            Files.createDirectories(opened.data().resolve(".metadata/.plugins"));
            Files.writeString(opened.data().resolve(".metadata/.plugins/state"), "imported");

            workspace.release(opened);
            var again = workspace.open(project("a"), GRACE, log::add);

            assertThat(WorkspaceLock.isHeld(again.data())).isTrue();
            assertThat(again.data().resolve(".metadata/.plugins/state")).hasContent("imported");
        }

        @Test
        void isRefusedWhileADaemonHoldsItsLockAndAvailableAgainOnceItReleasesIt() throws IOException {
            var workspace = workspace();
            var root = project("a");
            var first = workspace.open(root, GRACE, log::add);

            assertThatThrownBy(() -> workspace.open(root, GRACE, log::add))
                    .isInstanceOfSatisfying(WorkspaceInUseException.class, refusal -> {
                        assertThat(refusal.key().get()).isEqualTo("workspace-in-use");
                        assertThat(refusal.parameters())
                                .containsEntry("project", root.toString())
                                .containsEntry("workspace", first.data().toString());
                    });
            workspace.release(first);

            assertThat(workspace.open(root, GRACE, log::add).data()).isEqualTo(first.data());
        }

        @Test
        void refusingASecondDaemonLeavesTheLockOfTheFirstOneForAnotherProcess() throws Exception {
            var workspace = workspace();
            var root = project("a");
            var first = workspace.open(root, GRACE, log::add);

            assertThatThrownBy(() -> workspace.open(root, GRACE, log::add)).isInstanceOf(WorkspaceInUseException.class);

            assertThat(AnotherProcess.findsLocked(first.data().resolve(".gabier-lock"), folder))
                    .isTrue();
        }

        @Test
        void removesTheWorkspacesOfTheProjectsThatNoLongerExist() throws IOException {
            var workspace = workspace();
            var gone = workspace.open(project("gone"), GRACE, log::add);
            workspace.release(gone);
            Directories.delete(project("gone"));
            log.clear();

            var opened = workspace.open(project("a"), GRACE, log::add);

            assertThat(gone.data()).doesNotExist();
            assertThat(opened.data()).isDirectory();
            assertThat(log)
                    .containsExactly("removed " + gone.data() + ", whose project " + folder.resolve("gone")
                            + " no longer exists");
        }

        @Test
        void alsoRemovesTheDataFoldersOfTheDaemonsThatWereKilled() throws IOException {
            var killed = leftBehind(temporaryFolder(), "gabier-jdtls-data-killed");

            workspace().open(project("a"), GRACE, log::add);

            assertThat(killed).doesNotExist();
        }
    }
}
