package fr.craft.gabier.daemon;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The types that jdtls could not choose an import for while it organized the imports of a file: it asks the client
 * ({@code java.action.organizeImports.chooseImports}, see {@link JdtlsClient}) when several packages declare a type the
 * file uses without importing it. The client declines to choose, so these are what the request has to report.
 */
final class ImportChoices {

    private final List<String> candidates = new CopyOnWriteArrayList<>();

    /** The qualified names of the types jdtls offered for one name. */
    void offer(List<String> offered) {
        candidates.addAll(offered);
    }

    /** Forgets what was offered: the next request starts afresh. */
    void clear() {
        candidates.clear();
    }

    /** What was offered since the last {@link #clear()}, each type once, the candidates of one name together. */
    List<String> offered() {
        return candidates.stream().distinct().toList();
    }
}
