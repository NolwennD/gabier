package fr.craft.gabier.editing.domain;

import fr.craft.gabier.shared.error.domain.ErrorKey;

/** The keys of the refusals of a language server, for the mapper that turns an exception into a problem report. */
public enum LanguageServerErrorKey implements ErrorKey {
    AMBIGUOUS_IMPORTS("language-server-ambiguous-imports"),
    FILE_MISSING("language-server-file-missing"),
    FILE_OUTSIDE_PROJECT("language-server-file-outside-project"),
    INVALID_NAME("language-server-invalid-name"),
    NO_SUCH_SYMBOL("language-server-no-such-symbol"),
    NOT_CREATABLE("language-server-not-creatable"),
    NOT_INLINABLE("language-server-not-inlinable"),
    NOT_EXTRACTABLE("language-server-not-extractable"),
    NOT_A_FILE("language-server-not-a-file"),
    NOT_MOVABLE("language-server-not-movable"),
    NOT_MOVABLE_TO_PACKAGE("language-server-not-movable-to-package"),
    NOT_READY("language-server-not-ready"),
    UNRESOLVED_IMPORTS("language-server-unresolved-imports");

    private final String key;

    LanguageServerErrorKey(String key) {
        this.key = key;
    }

    @Override
    public String get() {
        return key;
    }
}
