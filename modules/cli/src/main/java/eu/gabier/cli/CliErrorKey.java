package eu.gabier.cli;

import eu.gabier.shared.error.domain.ErrorKey;

/** The keys of the refusals of the command line itself. */
enum CliErrorKey implements ErrorKey {
    DAEMON_CONNECTION_FAILED("daemon-connection-failed"),
    DAEMON_NOT_RUNNING("daemon-not-running"),
    DAEMON_START_FAILED("daemon-start-failed"),
    JDTLS_START_FAILED("jdtls-start-failed"),
    JDTLS_START_TIMEOUT("jdtls-start-timeout"),
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
