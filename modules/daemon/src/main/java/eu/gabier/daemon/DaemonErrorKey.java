package eu.gabier.daemon;

import eu.gabier.shared.error.domain.ErrorKey;

/** The keys of the refusals of the daemon itself: its command line, its socket and the parameters of a request. */
enum DaemonErrorKey implements ErrorKey {
    ALREADY_RUNNING("daemon-already-running"),
    INVALID_PARAMETER("invalid-parameter"),
    JDTLS_START_FAILED("jdtls-start-failed"),
    LINE_TOO_LONG("line-too-long"),
    MISSING_PARAMETER("missing-parameter"),
    NO_BUILD_TO_IMPORT("no-build-to-import"),
    SOCKET_NOT_PRIVATE("socket-not-private"),
    SOCKET_UNAVAILABLE("socket-unavailable"),
    UNKNOWN_PARAMETER("unknown-parameter"),
    USAGE("usage-error");

    private final String key;

    DaemonErrorKey(String key) {
        this.key = key;
    }

    @Override
    public String get() {
        return key;
    }
}
