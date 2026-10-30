package fr.craft.gabier.protocol;

/**
 * The exit code of the program, which a script reads: success, a request that gabier refused (a usage error included,
 * kept at 2 as it has always been), and an internal error. Documented in the README, so stable.
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
