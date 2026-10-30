package fr.craft.gabier.cli;

import fr.craft.gabier.shared.error.domain.ErrorKey;

/** The keys of the refusals of the command line itself. */
enum CliErrorKey implements ErrorKey {
    USAGE("usage-error");

    private final String key;

    CliErrorKey(String key) {
        this.key = key;
    }

    @Override
    public String get() {
        return key;
    }
}
