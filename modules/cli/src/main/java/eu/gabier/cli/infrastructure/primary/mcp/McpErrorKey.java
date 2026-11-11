package eu.gabier.cli.infrastructure.primary.mcp;

import eu.gabier.shared.error.domain.ErrorKey;

/** The keys of the failed calls that only the server of MCP knows. */
enum McpErrorKey implements ErrorKey {
    DAEMON_STARTING("daemon-starting"),
    DAEMON_TIMEOUT("daemon-timeout");

    private final String key;

    McpErrorKey(String key) {
        this.key = key;
    }

    @Override
    public String get() {
        return key;
    }
}
