package eu.gabier.daemon;

import eu.gabier.editing.domain.DiagnosedFile;
import eu.gabier.protocol.Problem;
import eu.gabier.protocol.ProblemMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * The one reader of the project, for every client that follows it: the clients that follow the problems ({@code watch},
 * {@code watch-all}) and those that follow the tests ({@code watch-affected-tests}). While at least one client listens,
 * one thread follows the disk ({@link DiskChanges}), one watcher for all of them, and, when the files changed, reads
 * what the clients follow once (whatever the number of clients) and offers it to each listener: the problems of the
 * project to the clients of the problems, the update of the tests to those of the tests. A kind that nobody follows is
 * not read: a read of the tests uses up the changes it answers for, so it is made only for a client that follows them.
 * The thread, and the disk it follows, end when the last listener of either kind leaves: nothing is left running when
 * nobody watches.
 *
 * <p>A client of the problems that comes while the project is followed is offered the last state at once; a client of
 * the tests is offered nothing before the next change, since an update is what changed and is not told twice.
 *
 * <p>A cycle of the read is the slow part (the project is built, about a second and a half on a project of five hundred
 * files), so the project is read when the disk changed and not at a fixed pace. If the system cannot follow the disk,
 * the listeners are told once ({@link Peer.Listener#say}) and the project is read each time the files look different.
 * If a read fails, the listeners of that kind are ended with the problem and the other kind goes on; if the disk cannot
 * be followed, every listener is ended and nothing is followed any more.
 */
final class ProjectFeed implements AutoCloseable {

    /** The name of the thread that follows the project, for who looks at the threads of the daemon. */
    static final String PUMP = "gabier-problems";

    /** How often the thread looks whether the last listener left, when the disk says nothing. */
    private static final Duration TICK = Duration.ofMillis(250);

    private final Supplier<? extends DiskChanges> disk;
    private final Object lock = new Object();
    private final Followers<List<DiagnosedFile>> problems;
    private final Followers<AffectedTestsUpdate> tests;
    private @Nullable Thread pump;

    /**
     * A feed that reads the project and follows its disk through these.
     *
     * @param readProblems the problems of the project now, once the server has built it
     * @param readTests the tests that what changed since the last answer affects, or the reason they cannot be told yet
     * @param disk starts to follow the disk: called by the thread, whenever the feed starts
     */
    ProjectFeed(
            Supplier<List<DiagnosedFile>> readProblems,
            Supplier<AffectedTestsUpdate> readTests,
            Supplier<? extends DiskChanges> disk) {
        this.disk = disk;
        this.problems = new Followers<>(readProblems, true);
        this.tests = new Followers<>(readTests, false);
    }

    /** From now on the listener is offered the problems of the project, the first state as soon as it is known. */
    void subscribe(Peer.Listener<List<DiagnosedFile>> listener) {
        synchronized (lock) {
            problems.add(listener);
            if (pump == null) {
                start();
            } else {
                // The thread already runs: the client is told what it read last, not only what comes next.
                problems.replayTo(listener);
            }
        }
    }

    /** The listener is offered nothing more. The feed ends once it has no listener left. */
    void unsubscribe(Peer.Listener<List<DiagnosedFile>> listener) {
        synchronized (lock) {
            problems.listeners.remove(listener);
        }
    }

    /** From now on the listener is offered the tests that each change affects, the first read as soon as it is made. */
    void subscribeTests(Peer.Listener<AffectedTestsUpdate> listener) {
        synchronized (lock) {
            tests.add(listener);
            start();
        }
    }

    /** The listener is offered nothing more. The feed ends once it has no listener left. */
    void unsubscribeTests(Peer.Listener<AffectedTestsUpdate> listener) {
        synchronized (lock) {
            tests.listeners.remove(listener);
        }
    }

    /** Whether the thread that follows the project runs. */
    boolean following() {
        synchronized (lock) {
            return pump != null;
        }
    }

    /** Ends the thread, whoever listens, and waits for it. */
    @Override
    public void close() {
        Thread running;
        synchronized (lock) {
            running = pump;
            problems.listeners.clear();
            tests.listeners.clear();
        }
        if (running == null) {
            return;
        }
        running.interrupt();
        try {
            running.join();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    /** Starts the thread unless it runs. Holding the lock. */
    private void start() {
        if (pump == null) {
            pump = Thread.ofPlatform().daemon().name(PUMP).start(this::follow);
        }
    }

    private void follow() {
        try (var changes = disk.get()) {
            var signal = DiskChanges.Signal.CHANGED;
            var starting = true;
            while (true) {
                if (signal != DiskChanges.Signal.QUIET) {
                    cycle(signal, starting);
                }
                starting = false;
                if (nobodyListens()) {
                    return;
                }
                signal = changes.await(TICK);
            }
        } catch (InterruptedException interrupted) {
            leave();
        } catch (RuntimeException failure) {
            endAll(ProblemMapper.toProblem(failure));
        }
    }

    /**
     * Reads what the clients follow and offers it; a kind that cannot be read is ended, so nobody is left waiting. The
     * cycle that starts the thread reads no test: a change on disk is what the tests are read for, and a client that
     * follows the tests was given those of the changes so far by the request that subscribed it.
     */
    private void cycle(DiskChanges.Signal signal, boolean starting) {
        if (signal == DiskChanges.Signal.LOST) {
            tellAll("The system cannot follow the files of the project: they are looked at every second from now on");
        }
        read(problems);
        if (!starting) {
            read(tests);
        }
    }

    private <S> void read(Followers<S> followed) {
        if (!listening(followed)) {
            return;
        }
        try {
            var state = followed.read.get();
            synchronized (lock) {
                followed.latest = followed.replaysLatest ? state : null;
                followed.listeners.forEach(listener -> listener.offer(state));
            }
        } catch (RuntimeException failure) {
            end(followed, ProblemMapper.toProblem(failure));
        }
    }

    private boolean listening(Followers<?> followed) {
        synchronized (lock) {
            return !followed.listeners.isEmpty();
        }
    }

    private void tellAll(String notice) {
        synchronized (lock) {
            problems.listeners.forEach(listener -> listener.say(notice));
            tests.listeners.forEach(listener -> listener.say(notice));
        }
    }

    private boolean nobodyListens() {
        synchronized (lock) {
            if (!problems.listeners.isEmpty() || !tests.listeners.isEmpty()) {
                return false;
            }
            leave();

            return true;
        }
    }

    /** Ends the listeners of one kind with the reason; the thread ends too when no listener of the other kind is left. */
    private <S> void end(Followers<S> followed, Problem reason) {
        List<Peer.Listener<S>> ended;
        synchronized (lock) {
            ended = new ArrayList<>(followed.listeners);
            followed.listeners.clear();
            followed.latest = null;
        }
        ended.forEach(listener -> listener.end(reason));
    }

    /** The disk cannot be followed: every listener is ended, and the thread with them. */
    private void endAll(Problem reason) {
        List<Peer.Listener<List<DiagnosedFile>>> endedProblems;
        List<Peer.Listener<AffectedTestsUpdate>> endedTests;
        synchronized (lock) {
            endedProblems = new ArrayList<>(problems.listeners);
            endedTests = new ArrayList<>(tests.listeners);
            problems.listeners.clear();
            tests.listeners.clear();
            leave();
        }
        endedProblems.forEach(listener -> listener.end(reason));
        endedTests.forEach(listener -> listener.end(reason));
    }

    /** The thread is about to end: the next listener starts another. */
    private void leave() {
        synchronized (lock) {
            pump = null;
            problems.latest = null;
        }
    }

    /**
     * The clients that follow one thing: how it is read, and what they are offered. Only used holding the lock of the
     * feed.
     */
    private static final class Followers<S> {

        final Supplier<S> read;
        final boolean replaysLatest;
        final Set<Peer.Listener<S>> listeners = new LinkedHashSet<>();

        @Nullable
        S latest;

        Followers(Supplier<S> read, boolean replaysLatest) {
            this.read = read;
            this.replaysLatest = replaysLatest;
        }

        void add(Peer.Listener<S> listener) {
            listeners.add(listener);
        }

        void replayTo(Peer.Listener<S> listener) {
            if (latest != null) {
                listener.offer(latest);
            }
        }
    }
}
