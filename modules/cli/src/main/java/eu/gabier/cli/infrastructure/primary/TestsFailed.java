package eu.gabier.cli.infrastructure.primary;

/**
 * Some tests failed: the command printed what they did, and ends with the exit code of failed tests ({@code 3}). Not a
 * refusal: it carries no problem and prints nothing more.
 */
final class TestsFailed extends RuntimeException {

    private static final long serialVersionUID = 1L;

    TestsFailed() {
        super("tests failed", null, false, false);
    }
}
