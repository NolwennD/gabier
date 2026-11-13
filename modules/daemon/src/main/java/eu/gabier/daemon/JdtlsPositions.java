package eu.gabier.daemon;

import eu.gabier.editing.domain.Position;
import eu.gabier.editing.domain.Range;

/** The positions and the ranges of the domain, and of LSP4J, which this package turns into each other. */
final class JdtlsPositions {

    private JdtlsPositions() {}

    static org.eclipse.lsp4j.Position lsp(Position position) {
        return new org.eclipse.lsp4j.Position(position.line(), position.column());
    }

    static org.eclipse.lsp4j.Range lsp(Range range) {
        return new org.eclipse.lsp4j.Range(
                new org.eclipse.lsp4j.Position(
                        range.start().line(), range.start().column()),
                new org.eclipse.lsp4j.Position(range.end().line(), range.end().column()));
    }

    static int compare(org.eclipse.lsp4j.Position first, org.eclipse.lsp4j.Position second) {
        return first.getLine() != second.getLine()
                ? Integer.compare(first.getLine(), second.getLine())
                : Integer.compare(first.getCharacter(), second.getCharacter());
    }
}
