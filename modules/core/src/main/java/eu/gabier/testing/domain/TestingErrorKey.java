package eu.gabier.testing.domain;

import eu.gabier.shared.error.domain.ErrorKey;

/** The keys of the refusals of the testing context, for the mapper that turns an exception into a problem report. */
public enum TestingErrorKey implements ErrorKey {
    COMPILATION_FAILED("testing-compilation-failed"),
    FILE_MISSING("testing-file-missing"),
    INVALID_TEST_FILTER("testing-invalid-test-filter"),
    NOT_IN_A_SOURCE_FOLDER("testing-not-in-a-source-folder"),
    READ_FAILED("testing-read-failed");

    private final String key;

    TestingErrorKey(String key) {
        this.key = key;
    }

    @Override
    public String get() {
        return key;
    }
}
