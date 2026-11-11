package eu.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.Watchable;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The events of the system for the folders registered with it, on the real file system of the machine (inotify on
 * Linux, which is where the delay of an edit is measured: the JDK polls slowly elsewhere). What the system does when it
 * runs out of watches or loses events cannot be asked of it, so {@link FolderWatchTest} scripts it.
 */
class NioFolderEventsTest {

    private static final Duration SECONDS = Duration.ofSeconds(10);

    private static final WatchEvent<Object> OVERFLOW = new WatchEvent<>() {
        @Override
        public Kind<Object> kind() {
            return StandardWatchEventKinds.OVERFLOW;
        }

        @Override
        public int count() {
            return 1;
        }

        @Override
        public Object context() {
            return "";
        }
    };

    @TempDir
    Path root;

    private final NioFolderEvents events = new NioFolderEvents();

    @AfterEach
    void giveTheSystemBack() {
        events.close();
    }

    @Test
    void tellsTheFileCreatedInARegisteredFolder() throws Exception {
        events.register(root);

        var file = Files.writeString(root.resolve("A.java"), "class A {}");

        assertThat(events.next(SECONDS)).hasValueSatisfying(batch -> {
            assertThat(batch.paths()).contains(file);
            assertThat(batch.overflow()).isFalse();
        });
    }

    @Test
    void tellsTheFileThatChangedAndTheOneThatWasDeleted() throws Exception {
        var changed = Files.writeString(root.resolve("A.java"), "class A {}");
        var deleted = Files.writeString(root.resolve("B.java"), "class B {}");
        events.register(root);

        Files.writeString(changed, "class A { int a; }");
        Files.delete(deleted);

        assertThat(allPathsUntilQuiet()).contains(changed, deleted);
    }

    @Test
    void doesNotTellWhatHappensInAFolderBelowARegisteredOneUntilItIsRegistered() throws Exception {
        var below = Files.createDirectories(root.resolve("below"));
        events.register(root);

        Files.writeString(below.resolve("A.java"), "class A {}");

        assertThat(events.next(Duration.ofMillis(300))).isEmpty();
    }

    @Test
    void tellsAFolderCreatedInARegisteredFolderAndThenWhatHappensInItOnceItIsRegistered() throws Exception {
        events.register(root);
        var created = Files.createDirectory(root.resolve("shop"));
        assertThat(events.next(SECONDS))
                .hasValueSatisfying(batch -> assertThat(batch.paths()).contains(created));

        events.register(created);
        var file = Files.writeString(created.resolve("Shop.java"), "class Shop {}");

        assertThat(allPathsUntilQuiet()).contains(file);
    }

    @Test
    void tellsTheFilesOfSeveralFoldersInOneBatchWhenTheirEventsAreAlreadyThere() throws Exception {
        var first = Files.createDirectory(root.resolve("first"));
        var second = Files.createDirectory(root.resolve("second"));
        events.register(first);
        events.register(second);

        var one = Files.writeString(first.resolve("A.java"), "class A {}");
        var other = Files.writeString(second.resolve("B.java"), "class B {}");
        Thread.sleep(300);

        assertThat(events.next(SECONDS))
                .hasValueSatisfying(batch -> assertThat(batch.paths()).contains(one, other));
    }

    @Test
    void saysThatTheSystemLostEventsAndKeepsTheOnesItToldAndReArmsTheFolder() {
        var paths = new HashSet<Path>();
        var key = new LostEvents(List.of(OVERFLOW, new Modified(Path.of("A.java"))));

        var lost = NioFolderEvents.take(key, root, paths);

        assertThat(lost).isTrue();
        assertThat(paths).containsExactly(root.resolve("A.java"));
        assertThat(key.reset).isTrue();
    }

    @Test
    void saysNothingWasLostWhenTheSystemTellsOnlyWhatHappened() {
        var paths = new HashSet<Path>();
        var key = new LostEvents(List.of(new Modified(Path.of("A.java"))));

        assertThat(NioFolderEvents.take(key, root, paths)).isFalse();
        assertThat(paths).containsExactly(root.resolve("A.java"));
    }

    @Test
    void saysNothingWhenNothingHappens() throws Exception {
        events.register(root);

        assertThat(events.next(Duration.ofMillis(100))).isEmpty();
    }

    @Test
    void saysNothingWhenNoFolderIsRegistered() throws Exception {
        assertThat(events.next(Duration.ofMillis(10))).isEmpty();
    }

    @Test
    void refusesAFolderThatIsNotThere() {
        assertThatThrownBy(() -> events.register(root.resolve("gone"))).isInstanceOf(NoSuchFileException.class);
    }

    @Test
    void closesWhateverWasRegisteredAndCanBeClosedTwice() throws IOException {
        events.register(root);

        events.close();
        events.close();
    }

    @Test
    void closesWhenNothingWasRegistered() {
        new NioFolderEvents().close();
    }

    /** What the system tells of a folder, as a key of the {@link java.nio.file.WatchService} would. */
    private static final class LostEvents implements WatchKey {

        private final List<WatchEvent<?>> told;
        private boolean reset;

        LostEvents(List<WatchEvent<?>> told) {
            this.told = told;
        }

        @Override
        public boolean isValid() {
            return true;
        }

        @Override
        public List<WatchEvent<?>> pollEvents() {
            return told;
        }

        @Override
        public boolean reset() {
            reset = true;

            return true;
        }

        @Override
        public void cancel() {}

        @Override
        public Watchable watchable() {
            throw new UnsupportedOperationException("not needed");
        }
    }

    private record Modified(Path name) implements WatchEvent<Path> {

        @Override
        public Kind<Path> kind() {
            return StandardWatchEventKinds.ENTRY_MODIFY;
        }

        @Override
        public int count() {
            return 1;
        }

        @Override
        public Path context() {
            return name;
        }
    }

    private java.util.Set<Path> allPathsUntilQuiet() throws InterruptedException {
        var paths = new java.util.HashSet<Path>();
        var batch = events.next(SECONDS);
        while (batch.isPresent()) {
            paths.addAll(batch.get().paths());
            batch = events.next(Duration.ofMillis(300));
        }

        return paths;
    }
}
