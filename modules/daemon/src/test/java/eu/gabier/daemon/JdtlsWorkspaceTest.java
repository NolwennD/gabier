package eu.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;

import eu.gabier.daemon.JdtlsRuntimes.Jdk;
import eu.gabier.daemon.WatchedFiles.Stamp;
import eu.gabier.protocol.WorkspaceLock;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Where jdtls keeps its workspace, and what becomes of it: a temporary folder, removed when it is released. */
class JdtlsWorkspaceTest {

    private static final Duration GRACE = Duration.ofMinutes(5);

    @TempDir
    Path folder;

    private final List<String> log = new ArrayList<>();

    private Path temporaryFolder() throws IOException {
        return Files.createDirectories(folder.resolve("tmp"));
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
            return new JdtlsWorkspace(temporaryFolder());
        }

        @Test
        void opensAFolderOfItsOwnInTheTemporaryFolder() throws IOException {
            var first = workspace().open(GRACE, log::add);
            var second = workspace().open(GRACE, log::add);

            assertThat(first.data()).isNotEqualTo(second.data()).isDirectory().hasParent(temporaryFolder());
            assertThat(first.data().getFileName().toString()).startsWith("gabier-jdtls-data-");
        }

        @Test
        void locksTheFolderUntilItIsReleased() throws IOException {
            var opened = workspace().open(GRACE, log::add);

            assertThat(WorkspaceLock.isHeld(opened.data())).isTrue();

            workspace().release(opened);

            assertThat(WorkspaceLock.isHeld(opened.data())).isFalse();
        }

        @Test
        void keepsTheLockOnItsOwnFolderForAnotherDaemonThatSweepsTheTemporaryFolder() throws Exception {
            var opened = workspace().open(GRACE, log::add);

            assertThat(AnotherProcess.findsLocked(opened.data().resolve(".gabier-lock"), folder))
                    .as("the lock, seen by another process, after the sweep that opening did")
                    .isTrue();
            workspace().open(GRACE, log::add);

            assertThat(opened.data()).isDirectory();
            assertThat(AnotherProcess.findsLocked(opened.data().resolve(".gabier-lock"), folder))
                    .isTrue();
        }

        @Test
        void usesTheTemporaryFolderOfTheSystemByDefault() throws IOException {
            var opened = JdtlsWorkspace.temporary().open(GRACE, log::add);

            try {
                assertThat(opened.data()).hasParent(Path.of(System.getProperty("java.io.tmpdir")));
            } finally {
                JdtlsWorkspace.temporary().release(opened);
            }
        }

        @Test
        void removesTheFolderWhenItIsReleased() throws IOException {
            var opened = workspace().open(GRACE, log::add);
            Files.writeString(opened.data().resolve("state"), "imported");

            workspace().release(opened);

            assertThat(opened.data()).doesNotExist();
        }

        @Test
        void removesTheDataFoldersOfTheDaemonsThatWereKilledButNotTheOneOfAJdtlsThatRuns() throws IOException {
            var killed = leftBehind(temporaryFolder(), "gabier-jdtls-data-killed");
            var running = leftBehind(temporaryFolder(), "gabier-jdtls-data-running");

            try (var held = WorkspaceLock.acquire(running).orElseThrow()) {
                workspace().open(GRACE, log::add);

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

            workspace().open(Duration.ofMinutes(20), log::add);

            assertThat(starting).isDirectory();
        }
    }

    @Nested
    class Kept {

        private Path project;
        private Path kept;
        private Path jdkHome;
        private Jdk jdk;

        @BeforeEach
        void aProjectBoundToAJdk() throws IOException {
            project = Files.createDirectories(folder.resolve("project"));
            kept = folder.resolve("cache/workspaces/project-1234");
            jdkHome = Files.createDirectories(folder.resolve("jdk"));
            Files.writeString(jdkHome.resolve("release"), """
                JAVA_VERSION="25.0.2"
                JAVA_RUNTIME_VERSION="25.0.2+10-LTS"
                """);
            jdk = Jdk.at(jdkHome).orElseThrow();
        }

        private JdtlsWorkspace workspace() throws IOException {
            return new JdtlsWorkspace(temporaryFolder());
        }

        private KeptWorkspace workspaceOf(String fingerprint) {
            return new KeptWorkspace(kept, fingerprint, project);
        }

        private JdtlsWorkspace.Opened open(String fingerprint) throws IOException {
            return workspace().open(workspaceOf(fingerprint), GRACE, log::add);
        }

        private JdtlsWorkspace.Remembered remembered(Jdk... runtimes) {
            return new JdtlsWorkspace.Remembered(
                    Map.of(project.resolve("A.java"), new Stamp(12, 34)), List.of(runtimes));
        }

        /** A run that ends cleanly: jdtls wrote its files, and the daemon kept the workspace. */
        private void aCleanRun(String fingerprint, JdtlsWorkspace.Remembered remembered) throws IOException {
            var opened = open(fingerprint);
            Files.writeString(opened.data().resolve("imported"), "yes");
            workspace().keep(opened, remembered);
            workspace().release(opened);
        }

        @Test
        void opensTheFolderOfTheProjectAndLocksItAndNamesItsProject() throws IOException {
            var opened = open("jdtls 1");

            assertThat(opened.data()).isEqualTo(kept).isDirectory();
            assertThat(opened.kept()).contains(workspaceOf("jdtls 1"));
            assertThat(opened.remembered()).isEmpty();
            assertThat(WorkspaceLock.isHeld(kept)).isTrue();
            assertThat(kept.resolve(".gabier-root")).hasContent(project.toString());
        }

        @Test
        void keepsTheFolderOfACleanRunAndGivesWhatItRememberedToTheNextOne() throws IOException {
            aCleanRun("jdtls 1", remembered(jdk));

            var opened = open("jdtls 1");

            assertThat(kept.resolve("imported")).hasContent("yes");
            assertThat(opened.remembered()).contains(remembered(jdk));
            assertThat(kept.resolve(".gabier-state")).doesNotExist();
        }

        @Test
        void emptiesTheFolderOfARunThatWasNotKept() throws IOException {
            var first = open("jdtls 1");
            Files.writeString(first.data().resolve("imported"), "half");
            workspace().release(first);

            var second = open("jdtls 1");

            assertThat(kept.resolve("imported")).doesNotExist();
            assertThat(second.remembered()).isEmpty();
        }

        @Test
        void emptiesTheFolderOfADaemonThatWasKilledWhileItRan() throws IOException {
            aCleanRun("jdtls 1", remembered());
            // The next run starts, which removes the state, and dies: nothing keeps the workspace.
            var killed = open("jdtls 1");
            Files.writeString(killed.data().resolve("half"), "half");
            killed.lock().close();

            var next = open("jdtls 1");

            assertThat(kept.resolve("imported")).doesNotExist();
            assertThat(kept.resolve("half")).doesNotExist();
            assertThat(next.remembered()).isEmpty();
        }

        @Test
        void emptiesTheFolderOfAnotherFingerprint() throws IOException {
            aCleanRun("jdtls 1 on jdk 25", remembered());

            var opened = open("jdtls 1 on jdk 27");

            assertThat(kept.resolve("imported")).doesNotExist();
            assertThat(opened.remembered()).isEmpty();
            assertThat(log)
                    .anyMatch(line -> line.contains("was made with jdtls 1 on jdk 25, not with jdtls 1 on jdk 27"));
        }

        @Test
        void emptiesTheFolderWhenTheJdkItWasBoundToIsNotTheSameAnyMore() throws IOException {
            aCleanRun("jdtls 1", remembered(jdk));
            Files.writeString(jdkHome.resolve("release"), """
                JAVA_VERSION="25.0.4"
                JAVA_RUNTIME_VERSION="25.0.4+7-LTS"
                """);

            var opened = open("jdtls 1");

            assertThat(opened.remembered()).isEmpty();
            assertThat(kept.resolve("imported")).doesNotExist();
            assertThat(log)
                    .anyMatch(line -> line.contains("was bound to " + jdkHome)
                            && line.contains("which is not the JDK it was (25.0.2+10-LTS) any more"));
        }

        @Test
        void emptiesTheFolderWhenTheJdkItWasBoundToIsGone() throws IOException {
            aCleanRun("jdtls 1", remembered(jdk));
            Files.delete(jdkHome.resolve("release"));

            assertThat(open("jdtls 1").remembered()).isEmpty();
        }

        @Test
        void emptiesAFolderWhoseStateItCannotRead() throws IOException {
            for (String state : List.of(
                    "not a state\n",
                    "gabier-workspace 1\njdtls 1\n",
                    "gabier-workspace 2\njdtls 1\n0\n",
                    "gabier-workspace 1\njdtls 1\nmany\n",
                    "gabier-workspace 1\njdtls 1\n-1\n",
                    "gabier-workspace 1\njdtls 1\n3\nonly one\n",
                    "gabier-workspace 1\njdtls 1\n1\nnot a jdk\n",
                    "gabier-workspace 1\njdtls 1\n0\n1\t2\n")) {
                aCleanRun("jdtls 1", remembered());
                Files.writeString(kept.resolve(".gabier-state"), state);
                log.clear();

                var opened = open("jdtls 1");

                assertThat(opened.remembered()).as(state).isEmpty();
                assertThat(kept.resolve("imported")).as(state).doesNotExist();
                assertThat(log).as(state).anyMatch(line -> line.contains("not readable"));
                workspace().release(opened);
            }
        }

        @Test
        void usesATemporaryWorkspaceWhenAnotherDaemonHoldsTheFolder() throws IOException {
            Files.createDirectories(kept);
            var held = WorkspaceLock.acquire(kept).orElseThrow();

            try {
                var opened = open("jdtls 1");

                assertThat(opened.kept()).isEmpty();
                assertThat(opened.data()).hasParent(temporaryFolder());
                assertThat(log).anyMatch(line -> line.contains("is used by another jdtls: a temporary one is used"));
            } finally {
                held.close();
            }
        }

        @Test
        void usesATemporaryWorkspaceWhileAJdtlsOfAKilledDaemonStillRunsOnTheFolder() throws Exception {
            aCleanRun("jdtls 1", remembered());
            Files.createDirectories(kept);
            // What is left of a killed daemon: a process of jdtls, with the folder after -data, that nobody holds the
            // lock of.
            Process orphan = new ProcessBuilder("sh", "-c", "read line", "jdtls", "-data", kept.toString()).start();

            try {
                var opened = open("jdtls 1");

                assertThat(opened.kept()).isEmpty();
                assertThat(opened.data()).hasParent(temporaryFolder());
                assertThat(kept.resolve("imported")).hasContent("yes");
                assertThat(WorkspaceLock.isHeld(kept)).isFalse();
                assertThat(log).anyMatch(line -> line.contains("is used by a jdtls that no daemon holds"));
            } finally {
                orphan.destroyForcibly();
            }
        }

        @Test
        void usesATemporaryWorkspaceWhenTheFolderCannotBeMade() throws IOException {
            Files.writeString(folder.resolve("cache"), "a file where the cache folder should be");

            var opened = open("jdtls 1");

            assertThat(opened.kept()).isEmpty();
            assertThat(opened.data()).hasParent(temporaryFolder());
            assertThat(log).anyMatch(line -> line.contains("cannot be used, a temporary one is used"));
        }

        @Test
        void releasesTheLockOfAFolderItCannotPrepareBeforeUsingATemporaryOne() throws IOException {
            // The name of the project cannot be written: it is a folder that holds something.
            Files.createDirectories(kept.resolve(".gabier-root/inside"));

            var opened = open("jdtls 1");

            assertThat(opened.kept()).isEmpty();
            assertThat(log).anyMatch(line -> line.contains("cannot be used, a temporary one is used"));
            assertThat(WorkspaceLock.isHeld(kept)).isFalse();
        }

        @Test
        void unlocksTheFolderWhenItIsReleasedAndKeepsItsNameForTheProject() throws IOException {
            var opened = open("jdtls 1");

            workspace().release(opened);

            assertThat(WorkspaceLock.isHeld(kept)).isFalse();
            assertThat(kept.resolve(".gabier-root")).exists();
        }

        @Test
        void removesTheKeptWorkspacesOfProjectsThatAreGoneButNotOtherWorkspaces() throws IOException {
            Path gone = folder.resolve("cache/workspaces/gone-1");
            aCleanRunOf(gone, folder.resolve("deleted-worktree"));
            Path other = folder.resolve("cache/workspaces/other-2");
            aCleanRunOf(other, project);

            open("jdtls 1");

            assertThat(gone).doesNotExist();
            assertThat(other).isDirectory();
            assertThat(log).contains("removed " + gone + ", the workspace of a project that is gone");
        }

        private void aCleanRunOf(Path folderOfProject, Path projectRoot) throws IOException {
            var opened = workspace().open(new KeptWorkspace(folderOfProject, "jdtls 1", projectRoot), GRACE, log::add);
            workspace().keep(opened, remembered());
            workspace().release(opened);
        }

        @Test
        void removesTheTemporaryWorkspacesOfKilledDaemonsAsATemporaryOpenDoes() throws IOException {
            var killed = leftBehind(temporaryFolder(), "gabier-jdtls-data-killed");

            open("jdtls 1");

            assertThat(killed).doesNotExist();
        }
    }
}
