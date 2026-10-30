package fr.craft.gabier.shared.error.infrastructure.primary;

import fr.craft.gabier.shared.error.domain.GeneratorException;

public final class GeneratorExceptionFactory {

    private GeneratorExceptionFactory() {}

    public static GeneratorException buildEmptyException() {
        return GeneratorException.builder(null).build();
    }
}
