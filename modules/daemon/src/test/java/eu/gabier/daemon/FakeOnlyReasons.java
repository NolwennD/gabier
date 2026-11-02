package eu.gabier.daemon;

/** The reasons why a test of the adapter is {@code @FakeOnly}: what no replay on the real jdtls can show. */
final class FakeOnlyReasons {

    static final String NO_ANSWER_IN_TIME = "a real jdtls answers in time, and no request makes it late on demand";
    static final String INTERRUPTED_WHILE_WAITING =
            "the thread that waits is interrupted by the test, which no request to a real jdtls does";
    static final String JDTLS_DIED = "a real jdtls does not die, or fail to start, on demand";
    static final String JDTLS_FAILS =
            "a real jdtls declines or answers; a response error that is neither cannot be provoked";
    static final String MALFORMED_ANSWER =
            "a real jdtls answers what its handlers build; an answer that lacks a part of it, or edits another file,"
                    + " cannot be provoked";
    static final String NOTHING_IS_THE_ONLY_ANSWER =
            "a real jdtls (1.63) answers nothing, and no message, for the four extractions (the replay shows it); the"
                    + " other answers are shown here";

    private FakeOnlyReasons() {}
}
