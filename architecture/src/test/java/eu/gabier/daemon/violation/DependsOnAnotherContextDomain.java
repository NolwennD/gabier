package eu.gabier.daemon.violation;

import eu.gabier.protocol.domain.ForeignDomainType;

/** Fixture of {@code HexagonalArchTest}: a daemon class that depends on the domain of another context. */
public final class DependsOnAnotherContextDomain {

    public Class<ForeignDomainType> foreign() {
        return ForeignDomainType.class;
    }
}
