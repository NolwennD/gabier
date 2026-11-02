package eu.gabier.editing.domain;

import eu.gabier.shared.error.domain.Assert;
import java.util.List;

/**
 * Where a symbol is used, and where it is declared when the declaration was asked for. The order of a language server's
 * search is not a promise, so both lists are put in the order of the files, then of the positions.
 *
 * @param declarations where the symbol is declared, none unless the declaration was asked for
 * @param references where the symbol is used
 */
public record ReferenceLocations(List<Location> declarations, List<Location> references) {

    public ReferenceLocations {
        Assert.field("declarations", declarations).notNull().noNullElement();
        Assert.field("references", references).notNull().noNullElement();

        declarations = declarations.stream().sorted().toList();
        references = references.stream().sorted().toList();
    }
}
