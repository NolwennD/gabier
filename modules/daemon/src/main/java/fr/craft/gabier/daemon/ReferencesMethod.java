package fr.craft.gabier.daemon;

import fr.craft.gabier.editing.application.FindReferences;
import fr.craft.gabier.editing.application.SymbolReferences;
import fr.craft.gabier.editing.domain.Position;
import fr.craft.gabier.editing.domain.Reference;
import fr.craft.gabier.protocol.FoundReferences;
import fr.craft.gabier.protocol.FoundReferences.Place;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@code references}: where the symbol at a position of a file is used, given as the parameters {@code file}
 * (absolute: the daemon has no working directory that means anything to the client), {@code line} and {@code column}
 * (from 0, as the protocol counts) and {@code includeDeclaration} ({@code true} or {@code false}). The result lists the
 * places with the line of source of each, and the declaration apart ({@code FoundReferences}). Nothing is written. The
 * refusals of the language server (not ready, file outside its project, no symbol at the position) and of the reading
 * of the files are the problem objects they are.
 */
record ReferencesMethod(FindReferences find) implements DaemonMethod {

    private static final String LINE = "line";
    private static final String COLUMN = "column";

    @Override
    public String name() {
        return "references";
    }

    @Override
    public Set<String> parameters() {
        return Set.of(ChangeRequest.FILE, LINE, COLUMN, ChangeRequest.INCLUDE_DECLARATION);
    }

    @Override
    public Outcome call(Map<String, String> params) {
        var request = new ChangeRequest(name(), params);
        SymbolReferences found = find.find(
                request.file(),
                new Position(request.number(LINE), request.number(COLUMN)),
                request.flag(ChangeRequest.INCLUDE_DECLARATION));

        return Outcome.of(new FoundReferences(places(found.declarations()), places(found.references())).toJson());
    }

    private static List<Place> places(List<Reference> references) {
        return references.stream()
                .map(reference -> new Place(
                        reference.file().toString(),
                        reference.position().line(),
                        reference.position().column(),
                        reference.source()))
                .toList();
    }
}
