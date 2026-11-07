package eu.gabier.editing.domain;

import eu.gabier.shared.error.domain.Assert;
import java.util.Map;
import java.util.Optional;

/**
 * An action a language server offers on a range: a fix for a problem, or a rewrite of the code. Both texts are the
 * server's, and gabier does not read them: the title is how the action is asked for, the kind says what sort of action
 * it is. The kind is what is read, to say which command of gabier does the same ({@link #command}); the title is read
 * for the one kind that the server shares between actions that are not alike ({@link #INLINE}).
 *
 * @param kind the kind of the action as the server gives it ({@code quickfix}, {@code refactor.extract.variable},
 *     {@code quickassist}, ...), empty when the server gave none
 * @param title what the server calls the action, which is not blank
 */
public record OfferedAction(String kind, String title) {

    /**
     * The kind that jdtls gives to "Make Static" as well as to "Inline Method", "Inline Constant" and "Inline local
     * variable": only the last three are what {@code gabier inline} does.
     */
    private static final String INLINE = "refactor.inline";

    /** The start of the title of an action of the kind {@link #INLINE} that inlines something. */
    private static final String INLINING = "Inline ";

    /** The commands of gabier that do what a kind of action does, by kind. */
    private static final Map<String, String> COMMANDS = Map.of(
            "refactor.extract.variable",
            "extract-variable",
            "refactor.extract.field",
            "extract-field",
            "refactor.extract.function",
            "extract-method",
            "refactor.extract.constant",
            "extract-constant",
            INLINE,
            "inline");

    public OfferedAction {
        Assert.notNull("kind", kind);
        Assert.notBlank("title", title);
    }

    /**
     * The command of gabier that does what this action does, which takes more than the title (the name of the variable
     * to extract, for one): it is read from the kind, and from the title only when the kind is shared by actions that
     * the command does not all do ({@link #INLINE}). Both extractions to a variable, with and without "(replace all
     * occurrences)", have the command that replaces the selected expression only.
     *
     * @return the name of the command, none for an action that has no command of gabier
     */
    public Optional<String> command() {
        if (kind.equals(INLINE) && !title.startsWith(INLINING)) {
            return Optional.empty();
        }

        return Optional.ofNullable(COMMANDS.get(kind));
    }
}
