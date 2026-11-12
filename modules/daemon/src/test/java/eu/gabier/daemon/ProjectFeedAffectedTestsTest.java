package eu.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;

import eu.gabier.editing.domain.DiagnosedFile;
import eu.gabier.editing.domain.LanguageServerException;
import eu.gabier.protocol.Problem;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.IntFunction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The feed for the clients that follow the tests: the same thread and the same disk as for the clients that follow the
 * problems, and one read of the tests for a change, whatever the number of clients, which is made only while a client
 * follows them. The disk and the reads are scripted.
 */
class ProjectFeedAffectedTestsTest {

    private final AtomicInteger problemReads = new AtomicInteger();
    private final AtomicInteger testReads = new AtomicInteger();
    private final ScriptedDisk disk = new ScriptedDisk();
    private volatile IntFunction<List<DiagnosedFile>> problems = read -> List.of();
    private volatile IntFunction<AffectedTestsUpdate> tests = read -> AffectedTestsUpdate.of(List.of());
    private final ProjectFeed feed = new ProjectFeed(
            () -> problems.apply(problemReads.incrementAndGet()),
            () -> tests.apply(testReads.incrementAndGet()),
            () -> disk);

    @AfterEach
    void stopFollowing() {
        feed.close();
    }

    /** A client of the feed: what it was offered, told and ended with. */
    private static class Client<S> implements Peer.Listener<S> {

        final List<S> states = new CopyOnWriteArrayList<>();
        final List<String> notices = new CopyOnWriteArrayList<>();
        final List<Problem> endings = new CopyOnWriteArrayList<>();

        @Override
        public void offer(S state) {
            states.add(state);
        }

        @Override
        public void say(String notice) {
            notices.add(notice);
        }

        @Override
        public void end(Problem reason) {
            endings.add(reason);
        }
    }

    private static final class TestsClient extends Client<AffectedTestsUpdate> {}

    private static final class ProblemsClient extends Client<List<DiagnosedFile>> {}

    private static void eventually(BooleanSupplier condition, String what) {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("Waited 10 s for " + what);
            }
            try {
                Thread.sleep(5);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();

                throw new AssertionError("interrupted", interrupted);
            }
        }
    }

    private static long pumps() {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(thread -> thread.getName().equals(ProjectFeed.PUMP) && thread.isAlive())
                .count();
    }

    private static AffectedTestsUpdate testsOf(int read) {
        return AffectedTestsUpdate.of(List.of("app.Test" + read));
    }

    /** Waits until the thread follows the disk, so that what the disk says next is a change and not the start. */
    private void followingTheDisk() {
        eventually(() -> disk.waits() > 0, "the thread to follow the disk");
    }

    @Test
    void readsTheTestsWhenTheDiskChangedAndOffersWhatTheReadGaveToTheClient() {
        tests = ProjectFeedAffectedTestsTest::testsOf;
        var client = new TestsClient();
        feed.subscribeTests(client);
        followingTheDisk();
        assertThat(client.states).isEmpty();

        disk.says(DiskChanges.Signal.CHANGED);

        eventually(() -> client.states.size() == 1, "the update after the change");
        assertThat(client.states.getFirst()).isEqualTo(testsOf(1));
    }

    @Test
    void readsNoTestWhenItStartsToFollowBecauseTheClientWasGivenTheTestsAlready() {
        var client = new TestsClient();

        feed.subscribeTests(client);
        followingTheDisk();
        int waits = disk.waits();
        eventually(() -> disk.waits() > waits + 5, "some quiet waits");

        assertThat(testReads).hasValue(0);
    }

    @Test
    void readsOnceForAChangeWhateverTheNumberOfClientsAndOffersTheSameUpdateToEach() {
        tests = ProjectFeedAffectedTestsTest::testsOf;
        var first = new TestsClient();
        var second = new TestsClient();
        feed.subscribeTests(first);
        feed.subscribeTests(second);
        followingTheDisk();

        disk.says(DiskChanges.Signal.CHANGED);

        eventually(() -> !first.states.isEmpty() && !second.states.isEmpty(), "both clients told of the change");
        assertThat(first.states.getFirst()).isEqualTo(testsOf(1));
        assertThat(second.states.getFirst()).isEqualTo(testsOf(1));
        assertThat(testReads).hasValue(1);
    }

    @Test
    void doesNotOfferWhatWasReadBeforeToAClientThatComesWhileAnotherIsFollowed() {
        tests = ProjectFeedAffectedTestsTest::testsOf;
        var first = new TestsClient();
        feed.subscribeTests(first);
        followingTheDisk();
        disk.says(DiskChanges.Signal.CHANGED);
        eventually(() -> !first.states.isEmpty(), "the first client told");
        var second = new TestsClient();

        feed.subscribeTests(second);
        int waits = disk.waits();
        eventually(() -> disk.waits() > waits + 5, "some quiet waits");

        assertThat(second.states).isEmpty();
    }

    @Test
    void readsNoTestWhileOnlyTheProblemsAreFollowed() {
        var client = new ProblemsClient();

        feed.subscribe(client);
        eventually(() -> problemReads.get() == 1, "the problems read");
        disk.says(DiskChanges.Signal.CHANGED);
        eventually(() -> problemReads.get() == 2, "the problems read again");

        assertThat(testReads).hasValue(0);
    }

    @Test
    void readsNoProblemWhileOnlyTheTestsAreFollowed() {
        var client = new TestsClient();

        feed.subscribeTests(client);
        followingTheDisk();
        disk.says(DiskChanges.Signal.CHANGED);
        eventually(() -> testReads.get() == 1, "the tests read");

        assertThat(problemReads).hasValue(0);
    }

    @Test
    void followsTheDiskWithOneThreadForTheClientsOfBothKindsAndStopsWhenTheLastOfThemLeaves() {
        tests = ProjectFeedAffectedTestsTest::testsOf;
        var problemsClient = new ProblemsClient();
        var testsClient = new TestsClient();
        feed.subscribe(problemsClient);
        feed.subscribeTests(testsClient);
        eventually(() -> !problemsClient.states.isEmpty(), "the problems told");

        assertThat(pumps()).isEqualTo(1);
        feed.unsubscribe(problemsClient);
        disk.says(DiskChanges.Signal.CHANGED);
        eventually(() -> testsClient.states.size() == 1, "the tests still followed");
        assertThat(feed.following()).isTrue();
        assertThat(disk.closed()).isFalse();
        feed.unsubscribeTests(testsClient);

        eventually(disk::closed, "the disk given back");
        eventually(() -> pumps() == 0, "the thread of the feed ended");
        assertThat(feed.following()).isFalse();
    }

    @Test
    void keepsFollowingTheTestsWhenTheProblemsCannotBeReadAndTheOtherWayRound() {
        tests = ProjectFeedAffectedTestsTest::testsOf;
        var problemsClient = new ProblemsClient();
        var testsClient = new TestsClient();
        problems = read -> {
            if (read > 1) {
                throw LanguageServerException.notReady();
            }

            return List.of();
        };
        feed.subscribe(problemsClient);
        feed.subscribeTests(testsClient);
        eventually(() -> !problemsClient.states.isEmpty(), "the problems told");

        disk.says(DiskChanges.Signal.CHANGED);

        eventually(() -> !problemsClient.endings.isEmpty(), "the problems client told why");
        eventually(() -> testsClient.states.size() == 1, "the tests client told of the change");
        assertThat(testsClient.endings).isEmpty();
        assertThat(feed.following()).isTrue();
        tests = read -> {
            throw LanguageServerException.notReady();
        };

        disk.says(DiskChanges.Signal.CHANGED);

        eventually(() -> !testsClient.endings.isEmpty(), "the tests client told why");
        assertThat(testsClient.endings.getFirst().key()).isEqualTo("language-server-not-ready");
        eventually(() -> pumps() == 0, "the thread of the feed ended");
    }

    @Test
    void tellsTheClientsOfBothKindsThatTheDiskCannotBeFollowed() {
        var problemsClient = new ProblemsClient();
        var testsClient = new TestsClient();
        feed.subscribe(problemsClient);
        feed.subscribeTests(testsClient);
        eventually(() -> !problemsClient.states.isEmpty(), "the problems told");

        disk.says(DiskChanges.Signal.LOST);

        eventually(() -> !problemsClient.notices.isEmpty() && !testsClient.notices.isEmpty(), "both told");
        assertThat(testsClient.notices.getFirst()).contains("every second");
    }

    @Test
    void endsTheClientsOfBothKindsWhenTheDiskCannotBeFollowedAtAll() {
        var failing = new ProjectFeed(() -> List.of(), () -> AffectedTestsUpdate.of(List.of()), () -> {
            throw LanguageServerException.notReady();
        });
        var problemsClient = new ProblemsClient();
        var testsClient = new TestsClient();

        failing.subscribe(problemsClient);
        failing.subscribeTests(testsClient);

        eventually(() -> !problemsClient.endings.isEmpty() && !testsClient.endings.isEmpty(), "both told why");
        assertThat(testsClient.endings.getFirst().key()).isEqualTo("language-server-not-ready");
        failing.close();
    }

    /** What the system says about the folders of the project: the batches that the test hands over, together. */
    private static final class Saves implements FolderEvents {

        private final Deque<FolderEvents.Batch> batches = new ArrayDeque<>();
        private final AtomicInteger looks = new AtomicInteger();
        private final AtomicInteger fingerprint;

        /**
         * A watch whose files change with each save.
         *
         * @param fingerprint what the files look like: each save changes it, as a save changes the file
         */
        Saves(AtomicInteger fingerprint) {
            this.fingerprint = fingerprint;
        }

        /** How many times the watch looked for events. */
        int looks() {
            return looks.get();
        }

        /** Several saves that reach the watch before it looks again, as a rename or a formatter makes them. */
        synchronized void happen(Path... files) {
            for (var file : files) {
                batches.add(new FolderEvents.Batch(Set.of(file), false));
            }
        }

        @Override
        public void register(Path folder) {}

        @Override
        public synchronized Optional<FolderEvents.Batch> next(Duration timeout) {
            looks.incrementAndGet();
            var batch = Optional.ofNullable(batches.poll());
            batch.ifPresent(saved -> fingerprint.incrementAndGet());

            return batch;
        }

        @Override
        public void close() {}
    }

    @Test
    void readsTheTestsOnceForSeveralSavesThatComeCloseTogetherWhenItFollowsTheRealWatch(@TempDir Path root)
            throws IOException {
        var fingerprint = new AtomicInteger();
        var saves = new Saves(fingerprint);
        var watching = new ProjectFeed(
                List::of,
                () -> tests.apply(testReads.incrementAndGet()),
                () -> new FolderWatch(
                        root,
                        saves,
                        fingerprint::get,
                        file -> file.getFileName().toString().endsWith(".java"),
                        Duration.ofMillis(7),
                        Duration.ofMillis(9)));
        tests = ProjectFeedAffectedTestsTest::testsOf;
        var client = new TestsClient();
        try {
            watching.subscribeTests(client);
            eventually(() -> saves.looks() > 0, "the watch to follow the folders");
            var files = List.of("A.java", "B.java", "C.java").stream()
                    .map(name -> root.resolve(name))
                    .toArray(Path[]::new);
            for (var file : files) {
                Files.writeString(file, "class " + file.getFileName() + " {}");
            }

            saves.happen(files);

            eventually(() -> client.states.size() == 1, "the one update for the three saves");
            assertThat(testReads).hasValue(1);
        } finally {
            watching.close();
        }
    }

    @Test
    void ignoresTheLeavingOfATestsClientItDoesNotKnow() {
        feed.unsubscribeTests(new TestsClient());

        assertThat(feed.following()).isFalse();
    }
}
