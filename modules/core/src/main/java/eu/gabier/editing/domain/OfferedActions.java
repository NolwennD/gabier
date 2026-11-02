package eu.gabier.editing.domain;

import eu.gabier.shared.error.domain.Assert;
import java.util.List;

/**
 * What a language server offers on a range, in the order it gives. The title of an action is its key for whoever asks
 * for it ({@link #titled}): the server's texts are opaque, so the only thing known about them is whether they are
 * equal.
 *
 * @param actions the actions, none when the server offers nothing there
 */
public record OfferedActions(List<OfferedAction> actions) {

    public OfferedActions {
        Assert.field("actions", actions).notNull().noNullElement();

        actions = List.copyOf(actions);
    }

    /**
     * The action with this title.
     *
     * @throws EditingException if the server offers no action with this title, or more than one (the message and the
     *     parameter {@code choices} give the kind and the title of those it offers, or of those that have the title)
     */
    public OfferedAction titled(String title) {
        Assert.notBlank("title", title);

        List<OfferedAction> found =
                actions.stream().filter(action -> action.title().equals(title)).toList();
        if (found.isEmpty()) {
            throw EditingException.noSuchCodeAction(title, describe(actions));
        }
        if (found.size() > 1) {
            throw EditingException.ambiguousCodeAction(title, describe(found));
        }

        return found.getFirst();
    }

    private static List<String> describe(List<OfferedAction> actions) {
        return actions.stream()
                .map(action -> action.kind().isEmpty() ? action.title() : action.kind() + ": " + action.title())
                .toList();
    }
}
