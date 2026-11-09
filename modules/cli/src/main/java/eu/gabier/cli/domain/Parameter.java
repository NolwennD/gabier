package eu.gabier.cli.domain;

import eu.gabier.shared.error.domain.Assert;

/**
 * One parameter of an {@link Operation}, as a front end shows it to whoever calls: its name, its type, whether it must
 * be given and what it means. A position (a line, a column) is counted from 1 here, as an editor shows it, and the
 * description says so; the daemon counts from 0, which the front end converts.
 *
 * @param name the name of the parameter, as the daemon reads it ({@code newName}, {@code startLine})
 * @param type what kind of value it holds
 * @param required whether it must be given
 * @param description what it means, in one sentence
 */
public record Parameter(String name, Type type, boolean required, String description) {

    public Parameter {
        Assert.field("name", name).notBlank().noWhitespace();
        Assert.notNull("type", type);
        Assert.notBlank("description", description);
    }

    /** A required parameter that holds a text. */
    public static Parameter string(String name, String description) {
        return new Parameter(name, Type.STRING, true, description);
    }

    /** A required parameter that holds a whole number. */
    public static Parameter integer(String name, String description) {
        return new Parameter(name, Type.INTEGER, true, description);
    }

    /** A required parameter that holds {@code true} or {@code false}. */
    public static Parameter bool(String name, String description) {
        return new Parameter(name, Type.BOOLEAN, true, description);
    }

    /** A required parameter that holds a list of texts. */
    public static Parameter array(String name, String description) {
        return new Parameter(name, Type.ARRAY, true, description);
    }

    /** The kinds of value a parameter holds. */
    public enum Type {
        STRING,
        INTEGER,
        BOOLEAN,
        ARRAY
    }
}
