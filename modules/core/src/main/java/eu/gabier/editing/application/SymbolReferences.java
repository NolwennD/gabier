package eu.gabier.editing.application;

import eu.gabier.editing.domain.Reference;
import eu.gabier.shared.error.domain.Assert;
import java.util.List;

/**
 * Where a symbol is used, with the line of source of each place, and where it is declared when that was asked for.
 *
 * @param declarations where the symbol is declared, none unless the declaration was asked for
 * @param references where the symbol is used, in the order of the files then of the positions
 */
public record SymbolReferences(List<Reference> declarations, List<Reference> references) {

    public SymbolReferences {
        Assert.field("declarations", declarations).notNull().noNullElement();
        Assert.field("references", references).notNull().noNullElement();

        declarations = List.copyOf(declarations);
        references = List.copyOf(references);
    }
}
