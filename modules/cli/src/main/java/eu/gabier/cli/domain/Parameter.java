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

    /** A required parameter that holds a path, the text that names a file of the project. */
    public static Parameter path(String name, String description) {
        return new Parameter(name, Type.PATH, true, description);
    }

    /** A required parameter that holds a list of paths. */
    public static Parameter paths(String name, String description) {
        return new Parameter(name, Type.PATHS, true, description);
    }

    /** A required parameter that holds a whole number that is no position. */
    public static Parameter integer(String name, String description) {
        return new Parameter(name, Type.INTEGER, true, description);
    }

    /** A required parameter that holds a line or a column, counted from 1. */
    public static Parameter position(String name, String description) {
        return new Parameter(name, Type.POSITION, true, description);
    }

    /** A required parameter that holds {@code true} or {@code false}. */
    public static Parameter bool(String name, String description) {
        return new Parameter(name, Type.BOOLEAN, true, description);
    }

    /** A required parameter that holds a list of texts. */
    public static Parameter array(String name, String description) {
        return new Parameter(name, Type.ARRAY, true, description);
    }

    /**
     * The kinds of value a parameter holds. What a front end gives is the value of the kind; what the daemon reads is
     * always a string (every value of a request is, {@code docs/protocol.md}), which the front end of the operations
     * writes as each kind says.
     */
    public enum Type {
        /** A text, sent as it is. */
        STRING,
        /** A text that names a file: it is never empty (an empty path would be the working directory), sent as it is. */
        PATH,
        /** A list of paths, each as a {@link #PATH} is, sent as an {@link #ARRAY} is. */
        PATHS,
        /** A whole number that is no position, sent in decimal. */
        INTEGER,
        /** A line or a column, a whole number from 1; the daemon counts from 0, so it is sent less one. */
        POSITION,
        /** {@code true} or {@code false}, sent as that word. */
        BOOLEAN,
        /** A list of texts with no line break and none empty, sent as one string, its elements one to a line. */
        ARRAY
    }
}
