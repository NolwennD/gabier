package eu.gabier.cli.infrastructure.primary;

import eu.gabier.protocol.DaemonStatus;
import eu.gabier.protocol.JdtlsState;
import eu.gabier.protocol.Json;
import eu.gabier.protocol.TerminalText;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

/**
 * {@code start}: starts the daemon of the project when none runs, and jdtls in it, then answers at once. With {@code
 * --wait} it answers once jdtls is ready, or refuses when jdtls failed or the time given passed; while it waits, the
 * state of jdtls is written on the standard error ({@link WaitNotice}), never on the standard output.
 */
final class Start implements Capability {

    static final Command COMMAND = Command.called("start")
            .summary("start the daemon and jdtls, so that the first command finds them ready")
            .usage("""
                gabier [--json] start
                gabier [--json] start --wait [--timeout=<seconds>]\
                """)
            .example("""
                $ gabier start
                gabier-daemon started, pid 4242, project /work/demo, jdtls starting; socket /run/user/1000/gabier/demo-6f1c0d4e.sock, log /run/user/1000/gabier/demo-6f1c0d4e.sock.log\
                """)
            .asksTheDaemon(
                    "jdtls-start-failed: with --wait, jdtls failed to start (the reason follows when the daemon knows"
                            + " it)",
                    "jdtls-start-timeout: with --wait, jdtls was not ready after the time given; it keeps starting,"
                            + " and the problem says where it was")
            .withNotes("""
                Start it first thing in a session, in the background, and work on something else: jdtls takes about a
                minute to start and import a Gradle project, and the first command that needs it waits for all of it.
                Without --wait the answer is at once: the daemon is started if none runs (its log is named then), jdtls
                starts in it unless it is already starting or ready, and the answer says where jdtls is. With --wait the
                answer comes when jdtls is ready, after at most --timeout seconds (300 by default); while it waits, a line
                on the standard error says what jdtls is doing, as for any command that waits for it. A jdtls that is
                ready is left alone. When the time passes, the refusal gives the last state of jdtls; jdtls goes on
                starting in the daemon.\
                """);

    static final String WAIT = "--wait";
    static final String TIMEOUT = "--timeout=";
    static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(5);

    /** How often a command that waits asks the daemon where jdtls is. */
    private static final Duration POLL = Duration.ofMillis(250);

    @Override
    public Command command() {
        return COMMAND;
    }

    @Override
    public void run(Invocation invocation) {
        var arguments = invocation.arguments();
        var waiting = arguments.has(WAIT);
        var timeout = timeout(arguments, waiting);
        arguments.withoutOptions(WAIT, TIMEOUT).exactly(0);

        var started = invocation.daemon().askStarting(command().commandName());
        var status = DaemonStatus.of(started.result().result());
        if (waiting) {
            status = untilReady(invocation, status, timeout);
        }
        var socket = invocation.daemon().socket();
        var log = started.daemonStarted() ? Optional.of(started.log()) : Optional.<Path>empty();
        invocation
                .out()
                .println(
                        invocation.json()
                                ? json(status, socket, log)
                                : text(status, socket, log, started.daemonStarted()));
    }

    /** The time to wait: the one given with {@code --timeout=}, which needs {@code --wait}, else the usual one. */
    private static Duration timeout(Arguments arguments, boolean waiting) {
        var given = arguments.valueOf(TIMEOUT);
        if (given.isEmpty()) {
            return DEFAULT_TIMEOUT;
        }
        if (!waiting) {
            throw UsageException.unsupportedArguments(arguments.all());
        }
        try {
            var seconds = Integer.parseInt(given.get());
            if (seconds >= 1) {
                return Duration.ofSeconds(seconds);
            }
        } catch (NumberFormatException notANumber) {
            // The same refusal as a number below one.
        }

        throw UsageException.invalidTimeout(arguments.all(), given.get());
    }

    /**
     * The status of the daemon once jdtls is ready, asked again until it is; a jdtls that failed or the time that
     * passed is a refusal. The state is written on the standard error while it takes long.
     */
    private static DaemonStatus untilReady(Invocation invocation, DaemonStatus first, Duration timeout) {
        var begun = System.nanoTime();
        var status = first;
        try (var notice = invocation.daemon().noticeWhileWaiting()) {
            while (status.jdtls() != JdtlsState.READY) {
                if (status.jdtls() == JdtlsState.FAILED) {
                    throw JdtlsWaitException.failed(status);
                }
                if (System.nanoTime() - begun >= timeout.toNanos()) {
                    throw JdtlsWaitException.timedOut(status, timeout);
                }
                status = DaemonStatus.of(invocation.daemon().statusAfter(POLL).result());
            }
        }

        return status;
    }

    private static String text(DaemonStatus status, Path socket, Optional<Path> log, boolean daemonStarted) {
        var failure = status.jdtlsFailure().map(reason -> " (" + reason + ")").orElse("");
        var line = "gabier-daemon " + (daemonStarted ? "started" : "running") + ", pid " + status.pid() + ", project "
                + status.project() + ", jdtls " + status.jdtls().text() + failure + "; socket " + socket
                + log.map(path -> ", log " + path).orElse("");

        return TerminalText.neutralise(line);
    }

    private static String json(DaemonStatus status, Path socket, Optional<Path> log) {
        var daemon = log.isPresent() ? "started" : "running";
        var failure = status.jdtlsFailure()
                .map(reason -> ",\"jdtlsFailure\":" + Json.quote(reason))
                .orElse("");

        return """
            {"daemon":%s,"version":%s,"pid":%d,"project":%s,"jdtls":%s%s,"socket":%s%s}\
            """.formatted(
                Json.quote(daemon),
                Json.quote(status.version()),
                status.pid(),
                Json.quote(status.project().toString()),
                Json.quote(status.jdtls().text()),
                failure,
                Json.quote(socket.toString()),
                log.map(path -> ",\"log\":" + Json.quote(path.toString())).orElse(""));
    }
}
