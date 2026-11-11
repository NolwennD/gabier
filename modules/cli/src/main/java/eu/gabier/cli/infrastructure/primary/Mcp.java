package eu.gabier.cli.infrastructure.primary;

import eu.gabier.cli.application.ProjectOperations;
import eu.gabier.cli.domain.Guide;
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
                earlier). tools/list gives these tools, named as the operations, with the parameters of the operation as
                their input schema: diagnostics, diagnostics-all, rename, rename-token, organize-imports, cleanup, extract-variable,
                extract-method, extract-constant, extract-field, move-type-to-file, move, inline, create, references,
                actions, apply-action and status. The commands start and stop are not tools: the server starts the
                daemon of the launch project itself when the host connects, and an agent that stopped it would lose the
                tools that need it.
                The tool guide is added to them: it answers a task as gabier help --for does and asks no
                daemon. tools/call runs the operation as the command line does (starting the daemon when
                none runs, except for status, which starts none) and answers the text the command line prints, with the
                result of the daemon beside it. A path is absolute (a relative one is refused: the server does not know
                the folder the session works in) and the call goes to the daemon of the project of its file, the nearest
                folder above it with a .git, else the project of the launch; the files of one call must be in one project. A call that asks the daemon runs in a thread of its own: the server goes
                on reading while it waits (for the start of the daemon, the import of the project by jdtls, a slow
                answer), so ping and status are answered at once, the answers of the calls are written as they end (the
                id tells them apart), and a call that carries a progressToken is told every few seconds, with
                notifications/progress, where jdtls is. A call the daemon has not answered after 11 minutes ends as a
                failed call; a notifications/cancelled ends a call with no answer.
                gabier mcp itself ends only with usage-error. A call that does not work is a result with isError, holding
                the problem and, for a refusal where the language server declined, the pointer to gabier help <command>.
                The keys of such a result are those of the operation called (gabier help <command>) and:
                  operation-invalid-values: a value is missing, is not a parameter of the operation, or is not of its kind;
                    a path is empty or not absolute, or the files of the call are in two projects
                  daemon-start-failed: no daemon answered and gabier-daemon could not be started
                  daemon-connection-failed: the connection to the daemon was lost, or it hung up without answering
                  daemon-not-running: a call of status when no daemon answers; status never starts one
                  daemon-starting: a call of status when no daemon answers yet and the start that the handshake began is
                    still running: ask again in a moment
                  daemon-timeout: the daemon had not answered after 11 minutes; it may still be working on the call
                  internal-server-error: a failure gabier does not know, logged on the standard error with its cause\
                """);

    @Override
    public Command command() {
        return COMMAND;
    }

    @Override
    public void run(Invocation invocation) {
        invocation.arguments().exactly(0);
        var projects = invocation.projects();
        var launched = projects.daemonOf(projects.launched());
        var warmUp = WarmUp.inBackground(() -> launched.askStarting(Operations.START.method()), invocation.err());
        var backend = new McpServer.Backend(
                new ProjectOperations(projects), Capabilities::commandOf, new Guide(GuideTopic.text()));
        new McpServer(backend, invocation.err(), McpInstructions.text(), warmUp)
                .serve(invocation.in(), invocation.out());
    }
}
