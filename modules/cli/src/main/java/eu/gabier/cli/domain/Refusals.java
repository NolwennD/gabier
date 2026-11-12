package eu.gabier.cli.domain;

/**
 * The refusals of the daemon that more than one operation answers, each as the key of its problem, a colon and what it
 * means. A refusal of one operation only is written with that operation ({@link Operations}).
 */
public final class Refusals {

    public static final String FILE_OUTSIDE_PROJECT =
            "language-server-file-outside-project: the file is not in the project the daemon serves";
    public static final String FILE_MISSING =
            "language-server-file-missing: the path is under the project and no file is there";
    public static final String NOT_A_FILE = "language-server-not-a-file: the path is a folder";
    public static final String NOT_EXTRACTABLE =
            "language-server-not-extractable: the selection is not an expression or statements that can be extracted,"
                    + " and jdtls does not say why";
    public static final String NOT_READY = "language-server-not-ready: the Java language server is not ready";
    public static final String REQUEST_FAILED =
            "language-server-request-failed: the language server failed on the request with an error of its own, which"
                    + " the problem relays (its code and its message)";
    public static final String STALE_EDIT_OF_A_FILE =
            "editing-stale-edit: the change cannot be applied to the file as it is";
    public static final String STALE_EDIT_OF_FILES =
            "editing-stale-edit: the change cannot be applied to the files as they are";

    public static final String TESTING_COMPILATION_FAILED =
            "testing-compilation-failed: a Java file given, or one that changed, has an error the language server"
                    + " reported; the problem names the files, and diagnostics tells what";
    public static final String TESTING_INVALID_TEST_FILTER =
            "testing-invalid-test-filter: a pattern of infinitest.filters is not a regular expression; the"
                    + " problem gives the file, the line and the pattern";
    public static final String TESTING_READ_FAILED =
            "testing-read-failed: a class file, a jar or the file of filters could not be read (a class"
                    + " compiled for a Java newer than the daemon's is not read)";

    private Refusals() {}
}
