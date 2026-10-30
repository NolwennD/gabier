module fr.craft.gabier.shared {
    requires static com.uber.nullaway.annotations;
    requires static org.jspecify;

    exports fr.craft.gabier.shared.error.domain;
    exports fr.craft.gabier.shared.generation;
}
