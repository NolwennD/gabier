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
 * The one reader of the problems of the project, for every client that watches. While at least one client listens, a
 * thread follows the disk ({@link DiskChanges}) and, when the files changed, reads the problems of the project once
 * (whatever the number of clients) and offers the state to each listener. A client that comes while the project is
 * followed is offered the last state at once. The thread, and the disk it follows, end when the last listener leaves:
 * nothing is left running when nobody watches.
 *
 * <p>A cycle of the read is the slow part (the project is built, about a second and a half on a project of five hundred
 * files), so the project is read when the disk changed and not at a fixed pace. If the system cannot follow the disk,
 * the listeners are told once ({@link Peer.Listener#say}) and the project is read each time the files look different.
 * If the project cannot be read, every listener is ended with the problem and nothing is followed any more.
 */
final class ProblemFeed implements AutoCloseable {

    /** The name of the thread that follows the project, for who looks at the threads of the daemon. */
    static final String PUMP = "gabier-problems";

    /** How often the thread looks whether the last listener left, when the disk says nothing. */
    private static final Duration TICK = Duration.ofMillis(250);

    private final Supplier<List<DiagnosedFile>> read;
    private final Supplier<? extends DiskChanges> disk;
    private final Object lock = new Object();
    private final Set<Peer.Listener> listeners = new LinkedHashSet<>();
    private @Nullable Thread pump;
    private @Nullable List<DiagnosedFile> latest;

    /**
     * A feed that reads the project and follows its disk through these.
     *
     * @param read the problems of the project now, once the server has built it
     * @param disk starts to follow the disk: called by the thread, whenever the feed starts
     */
    ProblemFeed(Supplier<List<DiagnosedFile>> read, Supplier<? extends DiskChanges> disk) {
        this.read = read;
        this.disk = disk;
    }

    /** From now on the listener is offered the problems of the project, the first state as soon as it is known. */
    void subscribe(Peer.Listener listener) {
        synchronized (lock) {
            listeners.add(listener);
            if (pump == null) {
                pump = Thread.ofPlatform().daemon().name(PUMP).start(this::follow);
            } else if (latest != null) {
                listener.offer(latest);
            }
        }
    }

    /** The listener is offered nothing more. The feed ends once it has no listener left. */
    void unsubscribe(Peer.Listener listener) {
        synchronized (lock) {
            listeners.remove(listener);
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
            listeners.clear();
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

    private void follow() {
        try (var changes = disk.get()) {
            var signal = DiskChanges.Signal.CHANGED;
            while (true) {
                if (signal != DiskChanges.Signal.QUIET) {
                    cycle(signal);
                }
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

    /** Reads the project and offers it; when it cannot be read the listeners are ended, so nobody is left. */
    private void cycle(DiskChanges.Signal signal) {
        if (signal == DiskChanges.Signal.LOST) {
            tellAll("The system cannot follow the files of the project: they are looked at every second from now on");
        }
        try {
            var state = read.get();
            synchronized (lock) {
                latest = state;
                listeners.forEach(listener -> listener.offer(state));
            }
        } catch (RuntimeException failure) {
            endAll(ProblemMapper.toProblem(failure));
        }
    }

    private void tellAll(String notice) {
        synchronized (lock) {
            listeners.forEach(listener -> listener.say(notice));
        }
    }

    private boolean nobodyListens() {
        synchronized (lock) {
            if (!listeners.isEmpty()) {
                return false;
            }
            leave();

            return true;
        }
    }

    private void endAll(Problem reason) {
        List<Peer.Listener> ended;
        synchronized (lock) {
            ended = new ArrayList<>(listeners);
            listeners.clear();
            leave();
        }
        ended.forEach(listener -> listener.end(reason));
    }

    /** The thread is about to end: the next listener starts another. */
    private void leave() {
        synchronized (lock) {
            pump = null;
            latest = null;
        }
    }
}
