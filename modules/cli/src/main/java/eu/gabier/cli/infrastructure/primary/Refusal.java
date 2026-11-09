package eu.gabier.cli.infrastructure.primary;

/**
 * The refusals that the command line itself answers, each as the key of its problem, a colon and what it means: the
 * ones that no operation of the daemon has. The refusals of the daemon are written with its operations.
 */
final class Refusal {

    static final String USAGE =
            "usage-error: the arguments are not those of the command (gabier help <command> gives its usage)";
    static final String START_FAILED = "daemon-start-failed: no daemon answered and gabier-daemon could not be started";
    static final String CONNECTION_FAILED =
            "daemon-connection-failed: the connection to the daemon was lost, or it hung up without answering";
    static final String STOPPING =
            "daemon-stopping: the daemon decided to stop because nobody used it and did not run the call;"
                    + " the next command starts a new daemon";
    static final String NOT_RUNNING = "daemon-not-running: no daemon answers; the command never starts one";

    static final String INVALID_VALUES =
            "operation-invalid-values: a value of the call is missing, is not a parameter of the operation, or is not"
                    + " of its kind";

    private Refusal() {}
}
