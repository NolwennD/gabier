package fr.craft.gabier.editing.domain;

import fr.craft.gabier.shared.error.domain.ErrorKey;

/** The keys of the refusals of the editing context, for the mapper that turns an exception into a problem report. */
public enum EditingErrorKey implements ErrorKey {
    FILE_EXISTS("editing-file-exists"),
    FILE_MISSING("editing-file-missing"),
    INVALID_RANGE("editing-invalid-range"),
    OVERLAPPING_EDITS("editing-overlapping-edits"),
    READ_FAILED("editing-read-failed"),
    ROLLBACK_INCOMPLETE("editing-rollback-incomplete"),
    STALE_EDIT("editing-stale-edit"),
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
