package fr.craft.gabier.protocol;

import fr.craft.gabier.shared.error.domain.ErrorKey;

/** The keys of the refusals of the protocol: the socket path and the lines of JSON-RPC. */
enum ProtocolErrorKey implements ErrorKey {
    INVALID_REQUEST("invalid-request"),
    INVALID_RESPONSE("invalid-response"),
    MALFORMED_JSON("malformed-json"),
    SOCKET_PATH_TOO_LONG("socket-path-too-long"),
    UNKNOWN_METHOD("unknown-method"),
    USER_UNKNOWN("user-unknown"),
    WORKSPACE_IN_USE("workspace-in-use");

    private final String key;

    ProtocolErrorKey(String key) {
        this.key = key;
    }

    @Override
    public String get() {
        return key;
    }
}
