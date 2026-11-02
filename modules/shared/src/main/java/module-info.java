module eu.gabier.shared {
    requires static com.uber.nullaway.annotations;
    requires static org.jspecify;

    exports eu.gabier.shared.error.domain;
    exports eu.gabier.shared.generation;
}
