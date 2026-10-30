module fr.craft.gabier.core {
    requires static org.jspecify;
    requires transitive fr.craft.gabier.shared;

    exports fr.craft.gabier.editing.domain;
    exports fr.craft.gabier.editing.infrastructure.secondary;
}
