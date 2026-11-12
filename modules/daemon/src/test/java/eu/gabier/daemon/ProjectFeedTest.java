package eu.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;

import eu.gabier.editing.domain.DiagnosedFile;
import eu.gabier.editing.domain.Diagnostic;
import eu.gabier.editing.domain.LanguageServerException;
import eu.gabier.editing.domain.Position;
import eu.gabier.protocol.Problem;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.IntFunction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The one reader of the project that serves every client that watches: it reads the problems of the project when the
 * disk changed, whatever the number of clients, offers the state to each of them, and stops, with the disk it follows,
 * when the last one leaves. The disk and the project are scripted: the real ones are the business of the adapters.
 */
class ProjectFeedTest {

    private static final Path FILE = Path.of("/project/A.java");

    private final AtomicInteger reads = new AtomicInteger();
    private final ScriptedDisk disk = new ScriptedDisk();
    private volatile IntFunction<List<DiagnosedFile>> project = read -> List.of();
    private final ProjectFeed feed = new ProjectFeed(this::read, () -> AffectedTestsUpdate.of(List.of()), () -> disk);

    private List<DiagnosedFile> read() {
        return project.apply(reads.incrementAndGet());
    }

    @AfterEach
    void stopFollowing() {
        feed.close();
    }

    /** The state of the project at the n-th read: a file with n problems. */
    private static List<DiagnosedFile> withProblems(int count) {
        var problems = new ArrayList<Diagnostic>();
        for (int index = 0; index < count; index++) {
            problems.add(new Diagnostic(Diagnostic.Severity.ERROR, new Position(index, 0), ""));
        }

        return List.of(new DiagnosedFile(FILE, problems));
    }

    /** A client: what it was offered, told and ended with. */
    private static final class Client implements Peer.Listener<List<DiagnosedFile>> {

        final List<List<DiagnosedFile>> states = new CopyOnWriteArrayList<>();
        final List<String> notices = new CopyOnWriteArrayList<>();
        final List<Problem> endings = new CopyOnWriteArrayList<>();

        @Override
        public void offer(List<DiagnosedFile> state) {
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

    @Test
    void readsTheProjectWhenItStartsFollowingAndOffersItToTheClient() {
        project = ProjectFeedTest::withProblems;
        var client = new Client();

        feed.subscribe(client);

        eventually(() -> !client.states.isEmpty(), "the first state");
        assertThat(client.states.getFirst()).isEqualTo(withProblems(1));
    }

    @Test
    void readsAgainWhenTheDiskChangedAndOffersTheNewStateAfterTheFirstOne() {
        project = ProjectFeedTest::withProblems;
        var client = new Client();
        feed.subscribe(client);
        eventually(() -> client.states.size() == 1, "the first state");

        disk.says(DiskChanges.Signal.CHANGED);

        eventually(() -> client.states.size() == 2, "the state after the change");
        assertThat(client.states.get(1)).isEqualTo(withProblems(2));
    }

    @Test
    void doesNotReadWhileTheDiskDoesNotChange() {
        var client = new Client();
        feed.subscribe(client);
        eventually(() -> reads.get() == 1, "the first read");
        int waits = disk.waits();

        eventually(() -> disk.waits() > waits + 5, "some quiet waits");

        assertThat(reads).hasValue(1);
    }

    @Test
    void readsOnceForAChangeWhateverTheNumberOfClientsAndOffersTheSameStateToEach() {
        project = ProjectFeedTest::withProblems;
        var first = new Client();
        var second = new Client();
        feed.subscribe(first);
        feed.subscribe(second);
        eventually(() -> !first.states.isEmpty() && !second.states.isEmpty(), "both clients told");
        int before = reads.get();

        disk.says(DiskChanges.Signal.CHANGED);

        eventually(() -> reads.get() == before + 1, "one read for the change");
        eventually(
                () -> first.states.getLast().equals(withProblems(before + 1))
                        && second.states.getLast().equals(withProblems(before + 1)),
                "both clients told of the change");
        assertThat(reads).hasValue(before + 1);
    }

    @Test
    void offersTheProjectAtOnceToAClientThatComesWhileAnotherIsFollowed() {
        project = ProjectFeedTest::withProblems;
        var first = new Client();
        feed.subscribe(first);
        eventually(() -> !first.states.isEmpty(), "the first client told");
        var second = new Client();

        feed.subscribe(second);

        eventually(() -> !second.states.isEmpty(), "the second client told, with no change on disk");
    }

    @Test
    void stopsWithTheDiskItFollowsWhenTheLastClientLeaves() {
        var first = new Client();
        var second = new Client();
        feed.subscribe(first);
        feed.subscribe(second);
        eventually(() -> !first.states.isEmpty() && !second.states.isEmpty(), "both clients told");

        feed.unsubscribe(first);
        assertThat(feed.following()).isTrue();
        assertThat(disk.closed()).isFalse();
        feed.unsubscribe(second);

        eventually(disk::closed, "the disk given back");
        eventually(() -> pumps() == 0, "the thread of the feed ended");
        assertThat(feed.following()).isFalse();
    }

    @Test
    void followsAgainForAClientThatComesAfterTheFeedStopped() {
        var first = new Client();
        feed.subscribe(first);
        eventually(() -> !first.states.isEmpty(), "told");
        feed.unsubscribe(first);
        eventually(() -> pumps() == 0, "stopped");
        int before = reads.get();
        var second = new Client();

        feed.subscribe(second);

        eventually(() -> !second.states.isEmpty(), "told again");
        assertThat(reads.get()).isGreaterThan(before);
        assertThat(feed.following()).isTrue();
    }

    @Test
    void doesNotOfferWhatWasReadBeforeTheFeedStoppedToAClientThatComesWhileTheFirstReadAfterItIsRunning()
            throws InterruptedException {
        var first = new Client();
        feed.subscribe(first);
        eventually(() -> !first.states.isEmpty(), "told");
        feed.unsubscribe(first);
        eventually(() -> pumps() == 0, "stopped");
        int before = reads.get();
        var release = new CountDownLatch(1);
        project = read -> {
            try {
                release.await();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }

            return withProblems(read);
        };
        var second = new Client();
        feed.subscribe(second);
        eventually(() -> reads.get() > before, "the new read started");
        var third = new Client();

        feed.subscribe(third);

        assertThat(third.states).isEmpty();
        release.countDown();
        eventually(() -> !second.states.isEmpty() && !third.states.isEmpty(), "both told by the new read");
        assertThat(third.states.getFirst()).isEqualTo(withProblems(before + 1));
    }

    @Test
    void tellsEveryClientThatTheDiskCannotBeFollowedAndReadsTheProjectAgain() {
        var first = new Client();
        var second = new Client();
        feed.subscribe(first);
        feed.subscribe(second);
        eventually(() -> !first.states.isEmpty() && !second.states.isEmpty(), "both clients told");
        int before = reads.get();

        disk.says(DiskChanges.Signal.LOST);

        eventually(() -> !first.notices.isEmpty() && !second.notices.isEmpty(), "both clients told");
        assertThat(first.notices).hasSize(1);
        assertThat(first.notices.getFirst()).contains("every second");
        eventually(() -> reads.get() > before, "the project read again");
    }

    @Test
    void endsEveryClientWithTheProblemWhenTheProjectCannotBeReadAndFollowsNoMore() {
        var first = new Client();
        var second = new Client();
        project = read -> {
            if (read > 1) {
                throw LanguageServerException.notReady();
            }

            return List.of();
        };
        feed.subscribe(first);
        feed.subscribe(second);
        eventually(() -> !first.states.isEmpty() && !second.states.isEmpty(), "both clients told");

        disk.says(DiskChanges.Signal.CHANGED);

        eventually(() -> !first.endings.isEmpty() && !second.endings.isEmpty(), "both clients told why");
        assertThat(first.endings.getFirst().key()).isEqualTo("language-server-not-ready");
        eventually(() -> pumps() == 0, "the thread of the feed ended");
        assertThat(disk.closed()).isTrue();
        assertThat(feed.following()).isFalse();
    }

    @Test
    void endsTheClientWithTheGenericProblemForAFailureThatIsNotARefusal() {
        project = read -> {
            throw new IllegalStateException("secret detail");
        };
        var client = new Client();

        feed.subscribe(client);

        eventually(() -> !client.endings.isEmpty(), "the client told why");
        assertThat(client.endings.getFirst().key()).isEqualTo("internal-server-error");
        assertThat(client.endings.getFirst().detail()).doesNotContain("secret");
    }

    @Test
    void endsTheClientWithTheProblemWhenTheDiskCannotBeFollowedAtAll() {
        var failing = new ProjectFeed(this::read, () -> AffectedTestsUpdate.of(List.of()), () -> {
            throw LanguageServerException.notReady();
        });
        var client = new Client();

        failing.subscribe(client);

        eventually(() -> !client.endings.isEmpty(), "the client told why");
        assertThat(client.endings.getFirst().key()).isEqualTo("language-server-not-ready");
        failing.close();
    }

    @Test
    void keepsTheInterruptOfTheThreadThatClosesItWhileItsThreadIsStillReading() {
        var release = new CountDownLatch(1);
        project = read -> {
            boolean interrupted = false;
            while (release.getCount() > 0) {
                try {
                    release.await();
                } catch (InterruptedException interruptedWhileReading) {
                    interrupted = true;
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }

            return List.of();
        };
        feed.subscribe(new Client());
        eventually(() -> reads.get() >= 1, "the read started");
        Thread.currentThread().interrupt();

        feed.close();

        assertThat(Thread.interrupted()).isTrue();
        release.countDown();
    }

    @Test
    void waitsForItsThreadWhenClosed() {
        feed.subscribe(new Client());
        eventually(() -> reads.get() >= 1, "started");

        feed.close();

        assertThat(pumps()).isZero();
        assertThat(disk.closed()).isTrue();
    }

    @Test
    void ignoresTheLeavingOfAClientItDoesNotKnow() {
        feed.unsubscribe(new Client());

        assertThat(feed.following()).isFalse();
    }
}
