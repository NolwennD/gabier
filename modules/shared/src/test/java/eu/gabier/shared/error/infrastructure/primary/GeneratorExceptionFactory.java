package eu.gabier.shared.error.infrastructure.primary;

import eu.gabier.shared.error.domain.GeneratorException;

public final class GeneratorExceptionFactory {

    private GeneratorExceptionFactory() {}

    public static GeneratorException buildEmptyException() {
        return GeneratorException.builder(null).build();
    }
}
