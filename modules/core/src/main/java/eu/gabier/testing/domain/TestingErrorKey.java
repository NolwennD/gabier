package eu.gabier.testing.domain;

import eu.gabier.shared.error.domain.ErrorKey;

/** The keys of the refusals of the testing context, for the mapper that turns an exception into a problem report. */
public enum TestingErrorKey implements ErrorKey {
    COMPILATION_FAILED("testing-compilation-failed"),
    FILE_MISSING("testing-file-missing"),
    INVALID_TEST_FILTER("testing-invalid-test-filter"),
    NO_SUCH_TEST_CLASS("testing-no-such-test-class"),
    NO_TEST_ENGINE("testing-no-test-engine"),
    NO_TEST_LAUNCHER("testing-no-test-launcher"),
    NOT_IN_A_SOURCE_FOLDER("testing-not-in-a-source-folder"),
    READ_FAILED("testing-read-failed"),
    RUN_FAILED("testing-run-failed"),
    TEST_TIMEOUT("testing-test-timeout");

    private final String key;

    TestingErrorKey(String key) {
        this.key = key;
    }

    @Override
    public String get() {
        return key;
    }
}
