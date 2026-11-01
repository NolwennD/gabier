package fr.craft.gabier.cli;

/**
 * The refusals that more than one command answers, each as the key of its problem, a colon and what it means. A
 * refusal of one command only is written in the class of that command.
 */
final class Refusal {

    static final String USAGE =
            "usage-error: the arguments are not those of the command (gabier help <command> gives its usage)";
    static final String START_FAILED = "daemon-start-failed: no daemon answered and gabier-daemon could not be started";
    static final String CONNECTION_FAILED =
            "daemon-connection-failed: the connection to the daemon was lost, or it hung up without answering";
    static final String NOT_RUNNING = "daemon-not-running: no daemon answers; the command never starts one";
    static final String FILE_OUTSIDE_PROJECT =
            "language-server-file-outside-project: the file is not in the project the daemon serves";
    static final String FILE_MISSING =
            "language-server-file-missing: the path is under the project and no file is there";
    static final String NOT_A_FILE = "language-server-not-a-file: the path is a folder";
    static final String NOT_READY = "language-server-not-ready: the Java language server is not ready";
    static final String STALE_EDIT_OF_A_FILE = "editing-stale-edit: the change cannot be applied to the file as it is";
    static final String STALE_EDIT_OF_FILES =
            "editing-stale-edit: the change cannot be applied to the files as they are";

    private Refusal() {}
}
