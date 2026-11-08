package eu.gabier.cli.infrastructure.secondary;

import eu.gabier.shared.error.domain.ErrorKey;

/** The keys of the refusals of the cli when it cannot reach the daemon. */
enum DaemonErrorKey implements ErrorKey {
    DAEMON_CONNECTION_FAILED("daemon-connection-failed"),
    DAEMON_NOT_RUNNING("daemon-not-running"),
    DAEMON_START_FAILED("daemon-start-failed");

    private final String key;

    DaemonErrorKey(String key) {
        this.key = key;
    }

    @Override
    public String get() {
        return key;
    }
}
