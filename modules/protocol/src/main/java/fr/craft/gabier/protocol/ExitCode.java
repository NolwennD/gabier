package fr.craft.gabier.protocol;

/**
 * The exit code of the program, which a script reads. The codes are stable, for {@code gabier} and for
 * {@code gabier-daemon}, and derived from the problem:
 *
 * <ul>
 *   <li>0: success;
 *   <li>1: internal error, gabier failed (a bug, or the environment), the request was not at fault;
 *   <li>2: request refused, a usage error (kept at 2 as it has always been), no daemon running, a socket path too long,
 *       any refusal of a request.
 * </ul>
 */
public enum ExitCode {
    SUCCESS(0),
    INTERNAL_ERROR(1),
    REFUSED(2);

    private final int value;

    ExitCode(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }

    /** The table: what the problem says about who is at fault gives the code. */
    public static ExitCode of(Problem problem) {
        return switch (problem.category()) {
            case BAD_REQUEST -> REFUSED;
            case INTERNAL_SERVER_ERROR -> INTERNAL_ERROR;
        };
    }
}
