package fr.craft.gabier.daemon;

import fr.craft.gabier.shared.error.domain.ErrorKey;

/** The keys of the refusals of the daemon itself: its command line and its socket. */
enum DaemonErrorKey implements ErrorKey {
    ALREADY_RUNNING("daemon-already-running"),
    SOCKET_NOT_PRIVATE("socket-not-private"),
    SOCKET_UNAVAILABLE("socket-unavailable"),
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
