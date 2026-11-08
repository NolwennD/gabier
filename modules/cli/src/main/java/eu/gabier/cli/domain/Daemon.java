package eu.gabier.cli.domain;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

/**
 * The daemon of the project, as the commands see it: a request is a method with named string parameters, its answer
 * is the members of a result. A refusal of the daemon is thrown as a {@link DaemonRefusal}, and a daemon that cannot
 * be reached or started fails with the exception of the adapter.
 */
public interface Daemon {

    /**
     * Asks the daemon a method, starting the daemon when nothing answers (a request is never left unanswered for want
     * of a daemon). While the request waits for a jdtls that is still starting, a line on the standard error says so.
     */
    DaemonReply ask(String method, Map<String, String> params);

    /**
     * Asks a daemon that runs, and starts none: it fails when nothing answers.
     *
     * @param awaitEnd whether to wait for the daemon to hang up, as a daemon that stops does
     */
    DaemonReply askRunning(String method, boolean awaitEnd);

    /** As {@link #ask}, with no parameter, and says whether this call had to start the daemon. No notice. */
    DaemonStart askStarting(String method);

    /**
     * The status of the daemon that runs, asked after a pause, on a connection of its own: the poll of a command that
     * waits for jdtls.
     */
    DaemonReply statusAfter(Duration pause);

    /**
     * Tells, on the standard error, why a request is slow, until it is closed: the state of jdtls, when it is still
     * starting.
     */
    Notice noticeWhileWaiting();

    /** Where the daemon of the project listens. */
    Path socket();

    /** The notice of a request that waits: nothing is written once it is closed. */
    interface Notice extends AutoCloseable {

        @Override
        void close();
    }
}
