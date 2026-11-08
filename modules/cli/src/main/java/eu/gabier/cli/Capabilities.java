package eu.gabier.cli;

import eu.gabier.shared.error.domain.Assert;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** The capabilities of gabier, in the order of {@code gabier help}, and how a word on the command line finds one. */
final class Capabilities {

    private static final List<Capability> ALL = ordered(
            new Diagnostics(),
            new Rename(),
            new OrganizeImports(),
            new Cleanup(),
            new ExtractVariable(),
            new ExtractMethod(),
            new ExtractConstant(),
            new ExtractField(),
            new MoveTypeToFile(),
            new Move(),
            new Inline(),
            new Create(),
            new References(),
            new Actions(),
            new ApplyAction(),
            new Start(),
            new Status(),
            new Stop(),
            new InitAgent(),
            new Help());

    private Capabilities() {}

    /** Every capability, in the order of {@code gabier help}. */
    static List<Capability> all() {
        return ALL;
    }

    /** The capability of the command whose name this is, if any. */
    static Optional<Capability> named(String name) {
        return ALL.stream()
                .filter(capability -> capability.command().commandName().equals(name))
                .findFirst();
    }

    /** The capability of the command a word with one typo (a letter changed, added or removed, two swapped) was meant for. */
    static Optional<Capability> nearestTo(String word) {
        return ALL.stream()
                .filter(capability -> EditDistance.between(capability.command().commandName(), word) == 1)
                .findFirst();
    }

    private static List<Capability> ordered(Capability... capabilities) {
        var distinctNames = Arrays.stream(capabilities)
                .map(capability -> capability.command().commandName())
                .distinct()
                .count();
        // Two capabilities with one name make more capabilities than names.
        Assert.field("capabilities", List.of(capabilities)).maxSize((int) distinctNames);

        return List.of(capabilities);
    }
}
