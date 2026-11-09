package eu.gabier.cli.infrastructure.primary;

import eu.gabier.cli.infrastructure.primary.mcp.McpServer;

/**
 * {@code mcp}: the MCP server of gabier on the standard input and output, for a host that speaks MCP ({@code claude mcp
 * add gabier -- gabier mcp}). It takes no argument and reads until the input is closed; the standard output holds the
 * messages of the protocol and nothing else.
 */
final class Mcp implements Capability {

    static final Command COMMAND = Command.called("mcp")
            .usage("gabier [--json] mcp")
            .example("""
                $ gabier mcp
                {"jsonrpc":"2.0","id":1,"method":"server/discover","params":{"_meta":{"io.modelcontextprotocol/protocolVersion":"2026-07-28","io.modelcontextprotocol/clientCapabilities":{}}}}
                {"jsonrpc":"2.0","id":1,"result":{"resultType":"complete","supportedVersions":["2026-07-28","2025-11-25"],...}}\
                """)
            .standalone(
                    "serve the operations of gabier as tools of MCP, on the standard input and output",
                    Refusal.USAGE,
                    Refusal.INVALID_VALUES,
                    Refusal.START_FAILED,
                    Refusal.CONNECTION_FAILED,
                    Refusal.NOT_RUNNING)
            .withNotes("""
                The server speaks MCP on the stdio transport: one JSON-RPC 2.0 message per line on the standard input and on
                the standard output, which carries nothing else. It answers the two eras of the protocol: server/discover
                and the metadata of every request (revision 2026-07-28), and initialize (revision 2025-11-25 and
                earlier). tools/list gives one tool per operation but start and stop, with the parameters of the operation
                as its input schema. tools/call runs the operation as the command line does (starting the daemon when
                none runs) and answers the text the command line prints, with the result of the daemon beside it; a
                refusal is a result with isError, holding the problem and the pointer to gabier help.\
                """);

    @Override
    public Command command() {
        return COMMAND;
    }

    @Override
    public void run(Invocation invocation) {
        invocation.arguments().exactly(0);
        new McpServer(invocation.operations(), Capabilities::commandOf).serve(invocation.in(), invocation.out());
    }
}
