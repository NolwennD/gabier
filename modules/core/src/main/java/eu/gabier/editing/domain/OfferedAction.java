package eu.gabier.editing.domain;

import eu.gabier.shared.error.domain.Assert;
import java.util.Map;
import java.util.Optional;

/**
 * An action a language server offers on a range: a fix for a problem, or a rewrite of the code. Both texts are the
 * server's, and gabier does not read them: the title is how the action is asked for, the kind says what sort of action
 * it is. The kind is the one thing read, to say which command of gabier does the same ({@link #command}).
 *
 * @param kind the kind of the action as the server gives it ({@code quickfix}, {@code refactor.extract.variable},
 *     {@code quickassist}, ...), empty when the server gave none
 * @param title what the server calls the action, which is not blank
 */
public record OfferedAction(String kind, String title) {

    /** The commands of gabier that do what a kind of action does, by kind. */
    private static final Map<String, String> COMMANDS = Map.of(
            "refactor.extract.variable", "extract-variable",
            "refactor.extract.field", "extract-field",
            "refactor.extract.function", "extract-method",
            "refactor.extract.constant", "extract-constant",
            "refactor.inline", "inline");

    public OfferedAction {
        Assert.notNull("kind", kind);
        Assert.notBlank("title", title);
    }

    /**
     * The command of gabier that does what this action does, which takes more than the title (the name of the variable
     * to extract, for one): it is read from the kind, never from the title.
     *
     * @return the name of the command, none for an action that has no command of gabier
     */
    public Optional<String> command() {
        return Optional.ofNullable(COMMANDS.get(kind));
    }
}
