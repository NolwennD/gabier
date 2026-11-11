package eu.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * How the daemon follows the disk while a client watches: the folders are registered with the system, an event on a file
 * jdtls watches whose content changed is a change, and a system that cannot follow (too many watches, events lost) is
 * told, once, and replaced by a look at the files at a fixed pace. The events come from a scripted source: no system
 * watch here, what the real one does is {@link NioFolderEventsTest}.
 */
class FolderWatchTest {

    private static final Duration TIMEOUT = Duration.ofMillis(5);

    @TempDir
    Path root;

    private final Events events = new Events();
    private final AtomicInteger fingerprint = new AtomicInteger();

    /** The files the daemon watches are the Java files, as the globs of jdtls say. */
    private FolderWatch watch() {
        return new FolderWatch(
                root,
                events,
                fingerprint::get,
                file -> file.getFileName().toString().endsWith(".java"),
                TIMEOUT,
                TIMEOUT);
    }

    private Path folder(String name) throws IOException {
        return Files.createDirectories(root.resolve(name));
    }

    /** What the system says: batches of paths, then silence; a folder or a limit can make it fail. */
    private static final class Events implements FolderEvents {

        private final Deque<FolderEvents.Batch> batches = new ArrayDeque<>();
        private final List<Path> registered = new ArrayList<>();
        private final Set<Path> refused = new java.util.HashSet<>();
        private final List<Duration> waited = new ArrayList<>();
        private boolean closed;

        void happens(Path... paths) {
            batches.add(new FolderEvents.Batch(Set.of(paths), false));
        }

        void overflows() {
            batches.add(new FolderEvents.Batch(Set.of(), true));
        }

        @Override
        public void register(Path folder) throws IOException {
            if (refused.contains(folder)) {
                throw new IOException("User limit of inotify watches reached");
            }
            registered.add(folder);
        }

        @Override
        public Optional<FolderEvents.Batch> next(Duration timeout) {
            waited.add(timeout);

            return Optional.ofNullable(batches.poll());
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    @BeforeEach
    void aProjectWithTwoFolders() throws IOException {
        Files.createDirectories(root.resolve("src/main/java/demo"));
        Files.createDirectories(root.resolve("src/test/java"));
    }

    @Test
    void registersTheProjectFolderAndEveryFolderUnderIt() throws Exception {
        try (var watch = watch()) {
            watch.await(TIMEOUT);
        }

        assertThat(events.registered)
                .containsExactlyInAnyOrder(
                        root,
                        root.resolve("src"),
                        root.resolve("src/main"),
                        root.resolve("src/main/java"),
                        root.resolve("src/main/java/demo"),
                        root.resolve("src/test"),
                        root.resolve("src/test/java"));
    }

    @Test
    void doesNotRegisterALinkToAFolderAsTheScanOfWatchedFilesDoesNotFollowIt() throws Exception {
        var elsewhere = Files.createDirectories(root.resolveSibling(root.getFileName() + "-elsewhere"));
        Files.createSymbolicLink(root.resolve("linked"), elsewhere);

        try (var watch = watch()) {
            watch.await(TIMEOUT);
        }

        assertThat(events.registered).doesNotContain(root.resolve("linked"), elsewhere);
    }

    @Test
    void saysNothingChangedWhenNothingHappens() throws Exception {
        try (var watch = watch()) {
            assertThat(watch.await(TIMEOUT)).isEqualTo(DiskChanges.Signal.QUIET);
        }
    }

    @Test
    void saysTheDiskChangedWhenAWatchedFileChangedItsContent() throws Exception {
        try (var watch = watch()) {
            events.happens(root.resolve("src/main/java/demo/A.java"));
            fingerprint.incrementAndGet();

            assertThat(watch.await(TIMEOUT)).isEqualTo(DiskChanges.Signal.CHANGED);
            assertThat(watch.await(TIMEOUT)).isEqualTo(DiskChanges.Signal.QUIET);
        }
    }

    @Test
    void ignoresAnEventOnAFileJdtlsDoesNotWatch() throws Exception {
        try (var watch = watch()) {
            events.happens(root.resolve("src/main/java/demo/notes.txt"));
            fingerprint.incrementAndGet();

            assertThat(watch.await(TIMEOUT)).isEqualTo(DiskChanges.Signal.QUIET);
        }
    }

    @Test
    void ignoresAnEventOnAFileThatLooksTheSameAsBefore() throws Exception {
        try (var watch = watch()) {
            events.happens(root.resolve("src/main/java/demo/A.java"));

            assertThat(watch.await(TIMEOUT)).isEqualTo(DiskChanges.Signal.QUIET);
        }
    }

    @Test
    void saysOnceForABurstOfEventsSuchAsTheFilesOfARename() throws Exception {
        try (var watch = watch()) {
            events.happens(root.resolve("src/main/java/demo/A.java"));
            events.happens(root.resolve("src/main/java/demo/B.java"));
            events.happens(root.resolve("src/test/java/BTest.java"));
            fingerprint.incrementAndGet();

            assertThat(watch.await(TIMEOUT)).isEqualTo(DiskChanges.Signal.CHANGED);
            assertThat(watch.await(TIMEOUT)).isEqualTo(DiskChanges.Signal.QUIET);
        }
    }

    @Test
    void waitsForTheEventsToStopForTheDebounceAfterTheFirstOne() throws Exception {
        var file = Files.writeString(root.resolve("A.java"), "class A {}");
        try (var watch = new FolderWatch(
                root, events, fingerprint::get, path -> true, Duration.ofMillis(7), Duration.ofMillis(9))) {
            events.happens(file);

            watch.await(Duration.ofMillis(11));

            assertThat(events.waited).containsExactly(Duration.ofMillis(11), Duration.ofMillis(7));
        }
    }

    @Test
    void registersAFolderOnceItsCreationIsToldAndTheFoldersInIt() throws Exception {
        try (var watch = watch()) {
            watch.await(TIMEOUT);
            var created = folder("src/main/java/demo/shop/deep");
            events.registered.clear();
            events.happens(root.resolve("src/main/java/demo/shop"));
            fingerprint.incrementAndGet();

            assertThat(watch.await(TIMEOUT)).isEqualTo(DiskChanges.Signal.CHANGED);
            assertThat(events.registered).containsExactlyInAnyOrder(root.resolve("src/main/java/demo/shop"), created);
        }
    }

    @Test
    void aFolderThatCannotBeRegisteredLeavesTheWatchPollingAndSaysSoOnceBeforeAnyChange() throws Exception {
        events.refused.add(root.resolve("src/main/java"));

        try (var watch = watch()) {
            assertThat(watch.await(TIMEOUT)).isEqualTo(DiskChanges.Signal.LOST);
            assertThat(watch.await(TIMEOUT)).isEqualTo(DiskChanges.Signal.QUIET);
            fingerprint.incrementAndGet();
            assertThat(watch.await(TIMEOUT)).isEqualTo(DiskChanges.Signal.CHANGED);
            assertThat(watch.await(TIMEOUT)).isEqualTo(DiskChanges.Signal.QUIET);
        }
    }

    @Test
    void eventsThatWereLostLeaveTheWatchPollingAndSaySoOnce() throws Exception {
        try (var watch = watch()) {
            events.overflows();

            assertThat(watch.await(TIMEOUT)).isEqualTo(DiskChanges.Signal.LOST);
            assertThat(watch.await(TIMEOUT)).isEqualTo(DiskChanges.Signal.QUIET);
            fingerprint.incrementAndGet();
            assertThat(watch.await(TIMEOUT)).isEqualTo(DiskChanges.Signal.CHANGED);
        }
    }

    @Test
    void givesTheRegistrationsBackAsSoonAsTheSystemCannotFollow() throws Exception {
        try (var watch = watch()) {
            events.overflows();
            watch.await(TIMEOUT);

            assertThat(events.closed).isTrue();
        }
    }

    @Test
    void aFolderThatCannotBeRegisteredOnceItIsCreatedLeavesTheWatchPolling() throws Exception {
        try (var watch = watch()) {
            watch.await(TIMEOUT);
            var created = folder("src/main/java/other");
            events.refused.add(created);
            events.happens(created);

            assertThat(watch.await(TIMEOUT)).isEqualTo(DiskChanges.Signal.LOST);
        }
    }

    @Test
    void followsTheRestOfTheProjectWhenAFolderCanBeListedButNotEnteredAndDoesNotLeaveItPolling() throws Exception {
        assumeThat(System.getProperty("user.name"))
                .as("root enters every folder")
                .isNotEqualTo("root");
        var listed = folder("src/main/java/listed");
        Files.writeString(listed.resolve("A.java"), "class A {}");
        Files.setPosixFilePermissions(listed, PosixFilePermissions.fromString("r--r--r--"));
        try (var watch = watch()) {
            assertThat(watch.await(TIMEOUT)).isEqualTo(DiskChanges.Signal.QUIET);
            assertThat(events.registered).contains(listed, root.resolve("src/test/java"));
        } finally {
            Files.setPosixFilePermissions(listed, PosixFilePermissions.fromString("rwxr-xr-x"));
        }
    }

    @Test
    void givesTheSystemBackWhenClosed() throws Exception {
        watch().close();

        assertThat(events.closed).isTrue();
    }
}
