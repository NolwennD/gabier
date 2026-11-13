package eu.gabier.cli.infrastructure.primary;

import eu.gabier.cli.domain.Operations;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * {@code move [--dry-run] <file>... <package>}: the last operand is the package, in dotted form, the others are the
 * files; the option may stand anywhere after the command. The files go to the daemon as absolute paths, one per line, so
 * a file name with a line break is a usage error. It answers as {@code rename} does: the files created, moved and
 * modified, then the diff.
 */
final class Move implements Capability {

    static final Command COMMAND = Command.called("move")
            .usage("gabier [--json] move [--dry-run] <file>... <package>")
            .example("""
                $ gabier move --dry-run src/main/java/demo/Invoice.java demo.billing
                dry run, nothing written
                modified /work/demo/src/main/java/demo/app/Shop.java
                modified /work/demo/src/main/java/demo/Invoice.java
                renamed /work/demo/src/main/java/demo/Invoice.java -> /work/demo/src/main/java/demo/billing/Invoice.java
                ...\
                """)
            .asksTheDaemon(Operations.MOVE)
            .withNotes("""
                With --json, the answer is the changed files.
                gabier organize-imports <file> orders the imports of a file, and gabier diagnostics <file> lists its\
                 problems.\
                """);

    @Override
    public Command command() {
        return COMMAND;
    }

    @Override
    public void run(Invocation invocation) {
        var arguments = invocation.arguments();
        var operands = arguments.atLeast(2);
        int last = operands.operands().size() - 1;
        List<Path> files = IntStream.range(0, last).mapToObj(operands::file).toList();
        if (files.stream()
                .anyMatch(file ->
                        file.toString().contains("\n") || file.toString().contains("\r"))) {
            throw UsageException.unsupportedArguments(arguments.all());
        }
        ChangedFilesReply.ask(
                        invocation,
                        Operations.MOVE,
                        Map.of(
                                "files", files.stream().map(Path::toString).toList(),
                                "package", operands.operand(last),
                                "dryRun", arguments.dryRun()))
                .print();
    }
}
