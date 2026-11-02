package fr.craft.gabier.editing.domain;

import fr.craft.gabier.shared.error.domain.Assert;

/**
 * An action a language server offers on a range: a fix for a problem, or a rewrite of the code. Both texts are the
 * server's, and gabier does not read them: the title is how the action is asked for, the kind says what sort of action
 * it is.
 *
 * @param kind the kind of the action as the server gives it ({@code quickfix}, {@code refactor.extract.variable},
 *     {@code quickassist}, ...), empty when the server gave none
 * @param title what the server calls the action, which is not blank
 */
public record OfferedAction(String kind, String title) {

    public OfferedAction {
        Assert.notNull("kind", kind);
        Assert.notBlank("title", title);
    }
}
