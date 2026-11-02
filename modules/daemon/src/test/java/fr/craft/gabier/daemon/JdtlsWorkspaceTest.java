package fr.craft.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;

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
}
