package eu.gabier.cli.infrastructure.primary;

/**
 * What {@code gabier mcp} tells a host about using its tools, in its handshake: the resource {@code mcp-instructions.md}
 * of the cli, beside {@code agents.md} (the native image keeps it, see {@code resource-config.json}). A tool is named in
 * it between backticks, which a test reads to keep the text in step with the tools the server lists.
 */
final class McpInstructions {

    private static final String RESOURCE = "mcp-instructions.md";

    private McpInstructions() {}

    /** The text, as written. */
    static String text() {
        return TextResource.read(RESOURCE, McpInstructions.class.getResourceAsStream(RESOURCE));
    }
}
