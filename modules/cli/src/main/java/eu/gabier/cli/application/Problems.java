package eu.gabier.cli.application;

import eu.gabier.cli.domain.DaemonRefusal;
import eu.gabier.protocol.Problem;
import java.util.Optional;
import java.util.Set;

/**
 * How a refusal of the daemon is told, whatever front end tells it: as the problem object it is, and with the pointer
 * to {@code gabier help} that ends the refusals where the language server or the edit declined what was asked.
 */
public final class Problems {

    /** The keys where the language server or the edit declined what was asked: the help lists the other ways. */
    private static final Set<String> DECLINED_OPERATIONS = Set.of(
            "language-server-ambiguous-imports",
            "language-server-empty-package",
            "language-server-invalid-name",
            "language-server-no-such-symbol",
            "language-server-not-applicable",
            "language-server-not-creatable",
            "language-server-not-extractable",
            "language-server-not-inlinable",
            "language-server-not-movable",
            "language-server-not-movable-to-package",
            "language-server-unresolved-imports",
            "editing-ambiguous-code-action",
            "editing-no-such-code-action",
            "editing-unknown-clean-up");

    private Problems() {}

    /** The problem object of a refusal of the daemon, as the daemon gave it. */
    public static Problem of(DaemonRefusal refusal) {
        return new Problem(
                refusal.type(),
                refusal.title(),
                refusal.detail(),
                refusal.key(),
                refusal.parameters(),
                refusal.category());
    }

    /** The pointer after a refusal where the language server or the edit declined: there, to the help of the command. */
    public static Optional<String> pointer(Problem refusal, String command) {
        return DECLINED_OPERATIONS.contains(refusal.key()) ? Optional.of(pointerTo(command)) : Optional.empty();
    }

    /** The line that points to the usage of a command and the other ways to do what it does. */
    public static String pointerTo(String command) {
        return "Usage and other ways: gabier help " + command;
    }
}
