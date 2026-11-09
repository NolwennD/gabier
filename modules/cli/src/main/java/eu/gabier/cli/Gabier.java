package eu.gabier.cli;

import eu.gabier.cli.infrastructure.primary.CommandLine;
import eu.gabier.cli.infrastructure.secondary.SocketDaemon;
import eu.gabier.shared.generation.ExcludeFromGeneratedCodeCoverage;
import java.io.PrintStream;
import java.time.Duration;
import java.util.Map;

/**
 * The entry point of the cli, and its composition root: the one class that builds the adapter of the daemon (the
 * socket) and hands it to the command line.
 */
public final class Gabier {

    private Gabier() {}

    @ExcludeFromGeneratedCodeCoverage(
            reason = "The entry point ends in System.exit, which a unit test cannot cover: "
                    + "all its logic is in run, which is tested")
    public static void main(String[] args) {
        System.exit(run(args, System.getenv(), System.out, System.err));
    }

    /** Runs the command line against the daemon of the project, and gives the exit code. */
    public static int run(String[] args, Map<String, String> environment, PrintStream out, PrintStream err) {
        return run(args, environment, out, err, SocketDaemon.NOTICE_DELAY);
    }

    /** As {@link #run(String[], Map, PrintStream, PrintStream)}, with the time a request waits before its notice. */
    public static int run(
            String[] args, Map<String, String> environment, PrintStream out, PrintStream err, Duration noticeDelay) {
        return CommandLine.run(args, out, err, new SocketDaemon(environment, err, noticeDelay));
    }
}
