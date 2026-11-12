package eu.gabier.daemon;

import eu.gabier.editing.application.ApplyCodeAction;
import eu.gabier.editing.application.CleanUpFile;
import eu.gabier.editing.application.CreatePackageInfo;
import eu.gabier.editing.application.CreateType;
import eu.gabier.editing.application.Diagnose;
import eu.gabier.editing.application.DiagnoseProject;
import eu.gabier.editing.application.ExtractConstant;
import eu.gabier.editing.application.ExtractField;
import eu.gabier.editing.application.ExtractMethod;
import eu.gabier.editing.application.ExtractVariable;
import eu.gabier.editing.application.FindReferences;
import eu.gabier.editing.application.FollowProblems;
import eu.gabier.editing.application.Inline;
import eu.gabier.editing.application.ListCodeActions;
import eu.gabier.editing.application.MoveToPackage;
import eu.gabier.editing.application.MoveTypeToFile;
import eu.gabier.editing.application.OrganizeImports;
import eu.gabier.editing.application.Rename;
import eu.gabier.editing.infrastructure.secondary.NioFileStore;
import eu.gabier.protocol.DaemonStatus;
import eu.gabier.protocol.IdleStop;
import eu.gabier.protocol.JdtlsState;
import eu.gabier.protocol.ProtocolVersion;
import eu.gabier.shared.generation.ExcludeFromGeneratedCodeCoverage;
import eu.gabier.testing.application.AffectedTests;
import eu.gabier.testing.infrastructure.secondary.NioProjectFiles;
import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * The daemon on its Unix socket: accepts the clients, one virtual thread each, and answers their requests with its
 * method table (see {@link #DaemonServer}). There is one daemon per user, and it owns the socket: it takes it only
 * when the folder and the file are the user's own and nobody answers on them, and gives it back when it stops.
 */
final class DaemonServer implements AutoCloseable {

    /** How long the clients are given to end by themselves, once the project is closed, before they are cut. */
    private static final Duration DISCONNECT_GRACE = Duration.ofSeconds(5);

    private static final Duration POLL = Duration.ofMillis(10);

    private final Path socket;
    private final ServerSocketChannel listener;
    private final RequestHandler stoppingHandler = RequestHandler.refusing(DaemonStoppingException.idle());
    private RequestHandler handler;
    private final ServedProject project;
    private final ProjectFeed problems;
    private final Set<SocketChannel> connections = ConcurrentHashMap.newKeySet();
    private final CompletableFuture<Void> stopRequested = new CompletableFuture<>();
    private boolean closed;
    private final LongSupplier nanoClock;
    private Duration grace = DISCONNECT_GRACE;
    private Optional<IdleWatch> idle = Optional.empty();
    private IdleWatch.Pause pause = this::pauseUnlessStopping;
    private volatile Consumer<String> log = line -> {};

    private DaemonServer(
            Path socket, ServerSocketChannel listener, LongSupplier nanoClock, long pid, ServedProject project) {
        this.socket = socket;
        this.listener = listener;
        this.project = project;
        this.nanoClock = nanoClock;
        var startedAt = nanoClock.getAsLong();
        var rename = new Rename(project, new NioFileStore());
        var follow = new FollowProblems(project);
        var affectedTests = new AffectedTests(project, new NioProjectFiles(), project.root());
        var followedTests = new FollowedTests(affectedTests);
        problems = new ProjectFeed(follow::read, followedTests::read, project::watchDisk);
        // The method table: to add a method, add it here (.claude/skills/add-daemon-method/SKILL.md).
        handler = new RequestHandler(
                List.of(
                        new StatusMethod(() -> status(nanoClock, startedAt, pid)),
                        new StartMethod(project::warmUp, () -> status(nanoClock, startedAt, pid)),
                        new StopMethod(this::requestStop),
                        new DiagnosticsMethod(new Diagnose(project)),
                        new DiagnosticsAllMethod(new DiagnoseProject(project)),
                        new WatchMethod(follow, problems),
                        new WatchAllMethod(follow, problems),
                        new RenameMethod(rename),
                        new RenameTokenMethod(rename),
                        new OrganizeImportsMethod(new OrganizeImports(project, new NioFileStore()), project.root()),
                        new CleanUpMethod(new CleanUpFile(project, new NioFileStore())),
                        new ExtractVariableMethod(new ExtractVariable(project, new NioFileStore())),
                        new ExtractMethodMethod(new ExtractMethod(project, new NioFileStore())),
                        new ExtractConstantMethod(new ExtractConstant(project, new NioFileStore())),
                        new ExtractFieldMethod(new ExtractField(project, new NioFileStore())),
                        new MoveTypeToFileMethod(new MoveTypeToFile(project, new NioFileStore())),
                        new MoveMethod(new MoveToPackage(project, new NioFileStore())),
                        new InlineMethod(new Inline(project, new NioFileStore())),
                        new CreateTypeMethod(
                                new CreateType(project, new NioFileStore()),
                                new CreatePackageInfo(project, new NioFileStore())),
                        new CodeActionsMethod(new ListCodeActions(project)),
                        new ApplyCodeActionMethod(new ApplyCodeAction(project, new NioFileStore())),
                        new ReferencesMethod(new FindReferences(project, new NioFileStore())),
                        new AffectedTestsMethod(affectedTests),
                        new AffectedTestsChangedMethod(affectedTests),
                        new WatchAffectedTestsMethod(followedTests, problems)),
                line -> log.accept(line));
    }

    /**
     * Starts listening on the socket.
     *
     * @param socket the path of the socket, already measured by {@code SocketLocation}
     * @param uid the numeric id of the user, as text
     * @param nanoClock a monotonic clock in nanoseconds ({@code System::nanoTime}) that the uptime is measured on, never
     *     the wall clock, which a user can set back; replaced by a hand-made one in the tests
     * @param project the project served: its language server is the one the methods of the catalogue ask, and it is
     *     closed when the daemon stops
     * @throws DaemonSocketException when the socket is not the daemon's to take, or cannot be set up
     */
    static DaemonServer open(Path socket, String uid, LongSupplier nanoClock, long pid, ServedProject project) {
        return open(socket, new SocketGuard(uid), nanoClock, pid, project);
    }

    /**
     * Starts listening on the socket, with the guard that judges and restricts it. A daemon that does not start leaves
     * no listener and no socket file behind: what it bound, it gives back.
     */
    static DaemonServer open(Path socket, SocketGuard guard, LongSupplier nanoClock, long pid, ServedProject project) {
        try {
            guard.prepareFolder(Objects.requireNonNull(socket.getParent(), "the socket is in a folder"));
            guard.clearStale(socket);
            var listener = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
            listener.bind(UnixDomainSocketAddress.of(socket));
            restrictOrGiveBack(guard, socket, listener);

            return new DaemonServer(socket, listener, nanoClock, pid, project);
        } catch (IOException e) {
            throw DaemonSocketException.unavailable(socket, e);
        }
    }

    /**
     * Makes the socket private, or, when that fails, closes the listener and removes the socket file it just bound and
     * rethrows the failure, to which what could not be given back is added as suppressed.
     */
    private static void restrictOrGiveBack(SocketGuard guard, Path socket, ServerSocketChannel listener)
            throws IOException {
        try {
            guard.restrict(socket);
        } catch (IOException | RuntimeException failure) {
            try (listener) {
                Files.deleteIfExists(socket);
            } catch (IOException cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }

    /**
     * Serves until a client asks the daemon to stop, until it has been idle for the delay (see {@link
     * #stoppingWhenIdleFor}), or until it is closed, then closes it: the same close in the three cases, which ends
     * jdtls and removes its workspace.
     */
    void serve() throws IOException {
        Thread.ofVirtual().name("gabier-accept").start(this::acceptClients);
        idle.ifPresent(
                watch -> Thread.ofVirtual().name("gabier-idle").start(() -> watch.watch(pause, this::requestStop)));
        stopRequested.join();
        close();
    }

    /** Serves a client the daemon counts as connected (see {@link IdleWatch#connected}), whatever ends its serving. */
    private void serve(SocketChannel client) {
        try {
            Connection.serve(Channels.newInputStream(client), Channels.newOutputStream(client), handler);
        } finally {
            // The idle watch is told first: a client that sees its connection end finds the daemon already counting.
            idle.ifPresent(IdleWatch::disconnected);
            connections.remove(client);
        }
    }

    /** Serves a client that reached the daemon once it decided to stop: every request is answered, none is run. */
    private void refuse(SocketChannel client) {
        try {
            Connection.serve(Channels.newInputStream(client), Channels.newOutputStream(client), stoppingHandler);
        } finally {
            connections.remove(client);
        }
    }

    /** Waits for the time asked, or until the daemon is told to stop; false once it is. */
    private boolean pauseUnlessStopping(Duration duration) {
        return stopRequested
                .thenApply(stopping -> false)
                .completeOnTimeout(true, duration.toNanos(), TimeUnit.NANOSECONDS)
                .join();
    }

    /**
     * Stops listening, removes the socket, ends the language server of the project (jdtls, started or still
     * starting) and disconnects the clients still there. They are disconnected last, so that a client that waits for
     * its connection to end knows the socket is gone and that no process of the daemon is left, and without cutting
     * the answer of a request that was waiting for the language server: that request ends when the project is closed,
     * and its answer is written before its connection ends (bounded by the grace, see {@link #disconnect}).
     */
    @Override
    public synchronized void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        stopRequested.complete(null);
        try {
            listener.close();
            Files.deleteIfExists(socket);
        } finally {
            problems.close();
            try {
                project.close();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally {
                disconnect();
            }
        }
    }

    /**
     * Lets each client end its conversation: no new line is read, the answer being written (the one a request that
     * waited for jdtls gets when the project is closed) goes out, then the connection ends by itself. A connection
     * that is still there once the grace is over, or when this thread is interrupted, is closed.
     */
    private void disconnect() throws IOException {
        connections.forEach(DaemonServer::stopReading);
        var deadline = System.nanoTime() + grace.toNanos();
        try {
            while (!connections.isEmpty() && System.nanoTime() < deadline) {
                Thread.sleep(POLL);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } finally {
            for (var connection : connections) {
                connection.close();
            }
        }
    }

    @ExcludeFromGeneratedCodeCoverage(
            reason = "A connection can end by itself between the snapshot of the set and this call, and no test can"
                    + " place its close in that gap: the failure is the wanted outcome, so it is ignored")
    private static void stopReading(SocketChannel connection) {
        try {
            connection.shutdownInput();
        } catch (IOException alreadyClosed) {
            // It ended by itself, which is what is asked.
        }
    }

    /** The thread that closes the daemon when the JVM is stopped by a signal (SIGTERM, SIGINT). */
    Thread shutdownHook() {
        return new Thread(this::closeQuietly, "gabier-shutdown");
    }

    private void closeQuietly() {
        try {
            close();
        } catch (IOException e) {
            // The JVM is going down: a socket that cannot be removed is a stale one for the next daemon to remove.
        }
    }

    private DaemonStatus status(LongSupplier nanoClock, long startedAt, long pid) {
        var state = project.state();
        // The reason is only said of a jdtls that is failed, whatever the project knows of an earlier failure.
        var failure = state == JdtlsState.FAILED ? project.jdtlsFailure() : Optional.<String>empty();

        return new DaemonStatus(
                ProtocolVersion.CURRENT.toString(),
                pid,
                Duration.ofNanos(nanoClock.getAsLong() - startedAt),
                project.root(),
                state,
                failure,
                ImportPreferences.read(project.root()).settings(),
                // Only a jdtls that is starting is doing anything to tell, whatever the project knows of an earlier
                // start.
                state == JdtlsState.STARTING ? project.jdtlsProgress() : Optional.empty(),
                idle.map(watch -> new IdleStop(watch.timeout(), watch.left())));
    }

    /** How long the clients are given to end by themselves when the daemon stops; for a test to shorten. */
    DaemonServer disconnectingAfter(Duration grace) {
        this.grace = grace;

        return this;
    }

    /**
     * Makes the daemon stop by itself, the way {@code stop} does, once no client has been connected for this long (a
     * connection is a call in progress: see {@link IdleWatch}); a daemon that is not told so never stops by itself.
     */
    DaemonServer stoppingWhenIdleFor(Duration delay) {
        idle = Optional.of(new IdleWatch(nanoClock, delay));

        return this;
    }

    /**
     * Where the daemon writes what it says of a request that failed ({@link FailureLog}); a daemon that is not told
     * writes it nowhere.
     */
    DaemonServer loggingTo(Consumer<String> log) {
        this.log = log;

        return this;
    }

    /** What answers the requests of the clients; for a test to replace by a handler whose methods it chooses. */
    DaemonServer handlingWith(RequestHandler handler) {
        this.handler = handler;

        return this;
    }

    /** How the idle watch waits; for a test to replace by a wait it holds. */
    DaemonServer pausingWith(IdleWatch.Pause pause) {
        this.pause = pause;

        return this;
    }

    /** Whether the daemon was told to stop, by a client or by itself; for a test to wait for. */
    boolean stopRequested() {
        return stopRequested.isDone();
    }

    /** How many clients are connected; for a test to wait for the end of a connection. */
    int connectionCount() {
        return connections.size();
    }

    private void requestStop() {
        stopRequested.complete(null);
    }

    private void acceptClients() {
        try {
            while (true) {
                var client = listener.accept();
                connections.add(client);
                if (idle.map(IdleWatch::connected).orElse(true)) {
                    Thread.ofVirtual().start(() -> serve(client));
                } else {
                    // The daemon has decided to stop: this client is told so and nothing it sends is run.
                    Thread.ofVirtual().start(() -> refuse(client));
                }
            }
        } catch (IOException closedOrFailed) {
            // The listener is closed when the daemon stops. If it failed instead, the daemon cannot go on either.
            requestStop();
        }
    }
}
