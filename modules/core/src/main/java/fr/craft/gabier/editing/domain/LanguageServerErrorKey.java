package fr.craft.gabier.editing.domain;

import fr.craft.gabier.shared.error.domain.ErrorKey;

/** The keys of the refusals of a language server, for the mapper that turns an exception into a problem report. */
public enum LanguageServerErrorKey implements ErrorKey {
    FILE_OUTSIDE_PROJECT("language-server-file-outside-project"),
    INVALID_NAME("language-server-invalid-name"),
    NO_SUCH_SYMBOL("language-server-no-such-symbol"),
    NOT_AN_EXPRESSION("language-server-not-an-expression"),
    NOT_READY("language-server-not-ready");

    private final String key;

    LanguageServerErrorKey(String key) {
        this.key = key;
    }

    @Override
    public String get() {
        return key;
    }
}
