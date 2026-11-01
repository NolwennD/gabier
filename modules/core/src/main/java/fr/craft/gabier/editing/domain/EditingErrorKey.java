package fr.craft.gabier.editing.domain;

import fr.craft.gabier.shared.error.domain.ErrorKey;

/** The keys of the refusals of the editing context, for the mapper that turns an exception into a problem report. */
public enum EditingErrorKey implements ErrorKey {
    FILE_EXISTS("editing-file-exists"),
    FILE_MISSING("editing-file-missing"),
    FOLDER_MISSING("editing-folder-missing"),
    INVALID_RANGE("editing-invalid-range"),
    NOT_A_PACKAGE_NAME("editing-not-a-package-name"),
    NOT_A_TYPE_NAME("editing-not-a-type-name"),
    OVERLAPPING_EDITS("editing-overlapping-edits"),
    READ_FAILED("editing-read-failed"),
    ROLLBACK_INCOMPLETE("editing-rollback-incomplete"),
    STALE_EDIT("editing-stale-edit"),
    TOKEN_NOT_FOUND("editing-token-not-found"),
    UNKNOWN_CLEAN_UP("editing-unknown-clean-up"),
    UNSUPPORTED_ENCODING("editing-unsupported-encoding"),
    WRITE_FAILED("editing-write-failed");

    private final String key;

    EditingErrorKey(String key) {
        this.key = key;
    }

    @Override
    public String get() {
        return key;
    }
}
