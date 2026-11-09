package eu.gabier.cli.infrastructure.primary;

import eu.gabier.cli.domain.Operation;
import java.util.Map;

/**
 * What the commands that extract a selection under a name share: {@code <command> [--dry-run] <file>
 * <line>:<column> <line>:<column> <name>}, each position from 1 (the end excluded), and the answer of the operation as
 * {@code rename} gives it.
 *
 * @param operation the operation that extracts
 */
record ExtractSelection(Operation operation) {

    void run(Invocation invocation) {
        var arguments = invocation.arguments();
        var operands = arguments.operands(4);
        var start = operands.position(1);
        var end = operands.position(2);
        ChangedFilesReply.ask(
                        invocation,
                        operation,
                        Map.of(
                                "file", operands.file(0).toString(),
                                "startLine", start.line(),
                                "startColumn", start.column(),
                                "endLine", end.line(),
                                "endColumn", end.column(),
                                "name", operands.operand(3),
                                "dryRun", arguments.dryRun()))
                .print();
    }
}
