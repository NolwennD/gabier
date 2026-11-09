package eu.gabier.cli.infrastructure.primary;

import eu.gabier.cli.domain.Operations;
import eu.gabier.cli.infrastructure.primary.mcp.McpServer;
import eu.gabier.cli.infrastructure.primary.mcp.WarmUp;

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
            .servesTheOperations("serve the operations of gabier as tools of MCP, on the standard input and output")
            .withNotes("""
                The server speaks MCP on the stdio transport: one JSON-RPC 2.0 message per line on the standard input and on
                the standard output, which carries nothing else. It answers the two eras of the protocol: server/discover
                and the metadata of every request (revision 2026-07-28), and initialize (revision 2025-11-25 and
                earlier). tools/list gives one tool per operation but start and stop, with the parameters of the operation
                as its input schema. tools/call runs the operation as the command line does (starting the daemon when
                none runs, except for status, which starts none) and answers the text the command line prints, with the
                result of the daemon beside it.
                gabier mcp itself ends only with usage-error. A call that does not work is a result with isError, holding
                the problem and, for a refusal where the language server declined, the pointer to gabier help <command>.
                The keys of such a result are those of the operation called (gabier help <command>) and:
                  operation-invalid-values: a value is missing, is not a parameter of the operation, or is not of its kind
                  daemon-start-failed: no daemon answered and gabier-daemon could not be started
                  daemon-connection-failed: the connection to the daemon was lost, or it hung up without answering
                  daemon-not-running: a call of status when no daemon answers; status never starts one
                  internal-server-error: a failure gabier does not know, logged on the standard error with its cause\
                """);

    @Override
    public Command command() {
        return COMMAND;
    }

    @Override
    public void run(Invocation invocation) {
        invocation.arguments().exactly(0);
        var warmUp =
                WarmUp.inBackground(() -> invocation.daemon().askStarting(Operations.START.method()), invocation.err());
        new McpServer(
                        invocation.operations(),
                        Capabilities::commandOf,
                        invocation.err(),
                        McpInstructions.text(),
                        warmUp)
                .serve(invocation.in(), invocation.out());
    }
}
