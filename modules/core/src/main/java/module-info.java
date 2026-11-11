module eu.gabier.core {
    requires static org.jspecify;
    requires transitive eu.gabier.shared;

    exports eu.gabier.editing.application;
    exports eu.gabier.editing.domain;
    exports eu.gabier.editing.infrastructure.secondary;
    exports eu.gabier.testing.application;
    exports eu.gabier.testing.domain;
    exports eu.gabier.testing.infrastructure.secondary;
}
