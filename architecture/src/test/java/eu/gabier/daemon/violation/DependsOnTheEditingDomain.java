package eu.gabier.daemon.violation;

import eu.gabier.editing.domain.ChangeSet;

/** Fixture of {@code HexagonalArchTest}: a daemon class that depends on the editing domain, which it implements. */
public final class DependsOnTheEditingDomain {

    public Class<ChangeSet> changes() {
        return ChangeSet.class;
    }
}
