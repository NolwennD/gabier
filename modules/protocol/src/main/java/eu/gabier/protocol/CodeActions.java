package eu.gabier.protocol;

import eu.gabier.shared.error.domain.Assert;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * The result of the {@code actions} method: what the language server offers on a range, in its order. The kinds and
 * the titles are the server's texts, which the result carries exactly: whatever prints them for a person goes through
 * {@code TerminalText.neutralise}.
 *
 * @param actions the actions, none when the server offers nothing there
 */
public record CodeActions(List<Action> actions) {

    public CodeActions {
        Assert.field("actions", actions).notNull().noNullElement();

        actions = List.copyOf(actions);
    }

    /**
     * One action, as the protocol carries it.
     *
     * @param kind the kind the server gives ({@code quickfix}, {@code refactor.extract.variable}, ...), empty when it
     *     gives none
     * @param title the title the server gives, which is how the action is asked for
     * @param command the command of gabier that does what the action does ({@code extract-variable}, ...), which is
     *     gabier's own text and not the server's; empty when the action has none
     */
    public record Action(String kind, String title, String command) {

        public Action {
            Assert.notNull("kind", kind);
            Assert.notNull("title", title);
            Assert.notNull("command", command);
        }

        /** An action that has no command of gabier. */
        public Action(String kind, String title) {
            this(kind, title, "");
        }

        private String toJson() {
            if (command.isEmpty()) {
                return """
                    {"kind":%s,"title":%s}\
                    """.formatted(Json.quote(kind), Json.quote(title));
            }

            return """
                {"kind":%s,"title":%s,"command":%s}\
                """.formatted(Json.quote(kind), Json.quote(title), Json.quote(command));
        }
    }

    /** The result as JSON, the members in a stable order. */
    public String toJson() {
        return actions.stream().map(Action::toJson).collect(Collectors.joining(",", "{\"actions\":[", "]}"));
    }

    /**
     * Reads the result of a {@code actions} response.
     *
     * @throws InvalidResponseException when a member is missing or has the wrong type
     */
    public static CodeActions of(Map<String, @Nullable Object> result) {
        if (!(result.get("actions") instanceof List<?> elements)) {
            throw invalid("The code actions have no list actions");
        }

        return new CodeActions(elements.stream().map(CodeActions::action).toList());
    }

    private static Action action(@Nullable Object element) {
        if (!(element instanceof Map<?, ?> members)) {
            throw invalid("A code action is not an object");
        }

        return new Action(
                text(members, "kind"),
                text(members, "title"),
                members.containsKey("command") ? text(members, "command") : "");
    }

    private static String text(Map<?, ?> members, String name) {
        if (members.get(name) instanceof String text) {
            return text;
        }

        throw invalid("The member " + name + " of a code action is not a string");
    }

    private static InvalidResponseException invalid(String message) {
        return InvalidResponseException.of(message, null);
    }
}
