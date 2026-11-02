package eu.gabier.editing.domain;

import eu.gabier.shared.error.domain.Assert;

/**
 * A problem that a language server reports in a source file.
 *
 * @param severity how serious it is
 * @param position where it starts (see {@link Position} for the convention: 0-based line and column)
 * @param message what the server says, as it says it
 */
public record Diagnostic(Severity severity, Position position, String message) {

    public Diagnostic {
        Assert.notNull("severity", severity);
        Assert.notNull("position", position);
        Assert.notNull("message", message);
    }

    /** The severities of the Language Server Protocol, from the most severe. */
    public enum Severity {
        ERROR,
        WARNING,
        INFORMATION,
        HINT
    }
}
