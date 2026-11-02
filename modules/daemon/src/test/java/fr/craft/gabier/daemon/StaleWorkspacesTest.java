package fr.craft.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;

import fr.craft.gabier.protocol.WorkspaceLock;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The workspaces that nobody uses any more: the data folder of a daemon that was killed (in the temporary folder). Only
 * real folders and files are used, and no jdtls: the lock of a live jdtls is a lock this process holds.
 */
class StaleWorkspacesTest {

    private static final Duration GRACE = Duration.ofMinutes(5);
    private static final Instant LONG_AGO = Instant.now().minus(Duration.ofHours(2));

    @TempDir
    Path folder;

    private final List<String> log = new ArrayList<>();

    private UserPrincipal me() throws IOException {
        return Files.getOwner(folder);
    }

    /** What {@code getOwner} answers for the folder of somebody else. */
    private static UserPrincipal somebodyElse() {
        return () -> "somebody-else";
    }

    /** A workspace as jdtls leaves it: the lock file of Eclipse in {@code .metadata}, and some of its files. */
    private Path workspace(String name) throws IOException {
        Path workspace = Files.createDirectories(folder.resolve(name));
        Files.createDirectories(workspace.resolve(".metadata/.plugins"));
        Files.writeString(workspace.resolve(".metadata/.plugins/index"), "indexes");
        Files.createFile(workspace.resolve(".gabier-lock"));
        return workspace;
    }

    private static void age(Path workspace) throws IOException {
        Files.setLastModifiedTime(workspace, FileTime.from(LONG_AGO));
    }

    @Nested
    class OfKilledDaemons {

        private void sweep(UserPrincipal user) {
            StaleWorkspaces.removeKilled(folder, user, GRACE, log::add);
        }

        @Test
        void removesTheDataFolderThatNoLiveJdtlsUses() throws IOException {
            Path orphan = workspace("gabier-jdtls-data-1234");

            sweep(me());

            assertThat(orphan).doesNotExist();
            assertThat(log).containsExactly("removed " + orphan + ", which no jdtls uses any more");
        }

        @Test
        void keepsTheDataFolderOfAJdtlsThatRuns() throws IOException {
            Path running = workspace("gabier-jdtls-data-1234");

            try (var held = WorkspaceLock.acquire(running).orElseThrow()) {
                sweep(me());

                assertThat(held).isNotNull();
            }

            assertThat(running.resolve(".metadata/.plugins/index")).hasContent("indexes");
            assertThat(log).isEmpty();
        }

        @Test
        void keepsTheDataFolderThatIsNotTheUsers() throws IOException {
            Path another = workspace("gabier-jdtls-data-1234");

            sweep(somebodyElse());

            assertThat(another.resolve(".metadata/.plugins/index")).hasContent("indexes");
            assertThat(log).isEmpty();
        }

        @Test
        void keepsTheDataFolderOfAJdtlsThatDidNotTakeTheLockYet() throws IOException {
            Path starting = Files.createDirectories(folder.resolve("gabier-jdtls-data-1234"));

            sweep(me());

            assertThat(starting).isDirectory();
        }

        @Test
        void removesTheDataFolderThatNeverGotALockOnceItIsOlderThanTheTimeJdtlsTakesToStart() throws IOException {
            Path never = Files.createDirectories(folder.resolve("gabier-jdtls-data-1234"));
            age(never);

            sweep(me());

            assertThat(never).doesNotExist();
        }

        @Test
        void leavesAloneWhatIsNotADataFolderOfGabier() throws IOException {
            Path other = workspace("some-other-folder");
            Path file = Files.writeString(folder.resolve("gabier-jdtls-data-file"), "not a folder");

            sweep(me());

            assertThat(other).isDirectory();
            assertThat(file).hasContent("not a folder");
            assertThat(log).isEmpty();
        }

        @Test
        void neverFollowsALinkThatLooksLikeADataFolder() throws IOException {
            Path elsewhere = workspace("precious");
            Files.createSymbolicLink(folder.resolve("gabier-jdtls-data-1234"), elsewhere);

            sweep(me());

            assertThat(elsewhere.resolve(".metadata/.plugins/index")).hasContent("indexes");
            assertThat(log).isEmpty();
        }

        @Test
        void doesNothingWhereThereIsNoFolder() {
            StaleWorkspaces.removeKilled(folder.resolve("missing"), somebodyElse(), GRACE, log::add);

            assertThat(log).isEmpty();
        }

        @Test
        void tellsWhatItCannotRemoveAndGoesOnWithTheOthers() throws IOException {
            Path stuck = workspace("gabier-jdtls-data-1");
            Path locked = Files.createDirectories(stuck.resolve("locked"));
            Path other = workspace("gabier-jdtls-data-2");
            Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("---------"));

            try {
                sweep(me());
            } finally {
                Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("rwx------"));
            }

            assertThat(other).doesNotExist();
            assertThat(log).anySatisfy(line -> assertThat(line).startsWith("could not clean up " + stuck));
        }
    }
}
