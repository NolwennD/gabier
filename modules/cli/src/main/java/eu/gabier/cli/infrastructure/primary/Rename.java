package eu.gabier.cli.infrastructure.primary;

import eu.gabier.cli.domain.Operations;
import java.util.Map;

/**
 * {@code rename [--dry-run] <file> <line>:<column> <new-name>} or {@code rename [--dry-run] --token <file>
 * <identifier> <new-name>}: the options may stand anywhere after the command. The position is from 1, as {@code
 * diagnostics} shows it, and so it stays until the runner of the operations sends it to the daemon. The text lists the files that changed, then the diff, with the
 * same neutralisation as every text that comes from the project; {@code --json} gives the result as the daemon gave it.
 */
final class Rename implements Capability {

    static final Command COMMAND = Command.called("rename")
            .usage("""
                gabier [--json] rename [--dry-run] <file> <line>:<column> <new-name>
                gabier [--json] rename [--dry-run] --token <file> <identifier> <new-name>\
                """)
            .example("""
                $ gabier rename --dry-run src/main/java/demo/Greeter.java 6:19 welcome
                dry run, nothing written
                modified /work/demo/src/main/java/demo/Greeter.java
                modified /work/demo/src/main/java/demo/Main.java
                ...\
                """)
            .asksTheDaemon(Operations.RENAME, Operations.RENAME_TOKEN)
            .withNotes("""
                --token names the symbol by the first occurrence of an identifier in the file, as a whole word (greet is
                not found in greeting). A comment or a string counts: the first occurrence may be in one.
                A rename sees the files that an earlier one wrote.\
                """);

    @Override
    public Command command() {
        return COMMAND;
    }

    @Override
    public void run(Invocation invocation) {
        var arguments = invocation.arguments();
        var dryRun = arguments.dryRun();
        ChangedFilesReply reply;
        if (arguments.has(Arguments.TOKEN)) {
            var operands = arguments.operands(3, Arguments.TOKEN);
            reply = ChangedFilesReply.ask(
                    invocation,
                    Operations.RENAME_TOKEN,
                    Map.of(
                            "file", operands.file(0).toString(),
                            "token", operands.operand(1),
                            "newName", operands.operand(2),
                            "dryRun", dryRun));
        } else {
            // The position is read before the count: the former form, `<line> <column> <new-name>`, has one operand
            // too many, and the refusal that names its first number is the one that tells what changed.
            var operands = arguments.atLeast(3);
            var position = operands.position(1);
            operands.exactly(3);
            reply = ChangedFilesReply.ask(
                    invocation,
                    Operations.RENAME,
                    Map.of(
                            "file", operands.file(0).toString(),
                            "line", position.line(),
                            "column", position.column(),
                            "newName", operands.operand(2),
                            "dryRun", dryRun));
        }
        reply.print();
    }
}
