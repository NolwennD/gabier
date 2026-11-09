package eu.gabier.cli.application;

import eu.gabier.shared.error.domain.ErrorKey;

/** The keys of the refusals of the application of the cli. */
enum OperationErrorKey implements ErrorKey {
    INVALID_VALUES("operation-invalid-values");

    private final String key;

    OperationErrorKey(String key) {
        this.key = key;
    }

    @Override
    public String get() {
        return key;
    }
}
