package eu.gabier.cli;

import eu.gabier.protocol.Problem;

/** The daemon answered with an error: its problem object is reported as it is, with the exit code of its category. */
final class DaemonRefusal extends RuntimeException {

    private final transient Problem problem;

    DaemonRefusal(Problem problem) {
        super(problem.detail());
        this.problem = problem;
    }

    Problem problem() {
        return problem;
    }
}
