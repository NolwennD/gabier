package eu.gabier.cli;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * {@code move [--dry-run] <file>... <package>}: the last operand is the package, in dotted form, the others are the
 * files; the option may stand anywhere after the command. The files go to the daemon as absolute paths, one per line, so
 * a file name with a line break is a usage error. It answers as {@code rename} does: the files created, moved and
 * modified, then the diff.
 */
final class Move implements Capability {

    static final Command COMMAND = Command.called("move")
            .summary("move Java classes to a package, creating the package when it does not exist")
            .usage("gabier [--json] move [--dry-run] <file>... <package>")
            .example("""
                $ gabier move --dry-run src/main/java/demo/Invoice.java demo.billing
                dry run, nothing written
                modified /work/demo/src/main/java/demo/app/Shop.java
                modified /work/demo/src/main/java/demo/Invoice.java
                renamed /work/demo/src/main/java/demo/Invoice.java -> /work/demo/src/main/java/demo/billing/Invoice.java
                ...\
                """)
            .asksTheDaemon(
                    """
                    language-server-not-movable-to-package: the files cannot go to the package (the reason says why:\
                     already in it, a file of that name there, not a Java source, in no source folder, in\
                     several source folders, the folder cannot be made, or the language server has no change\
                     to make)\
                    """,
                    "editing-not-a-package-name: the last operand is not a package name (the default package, a"
                            + " keyword, an empty part)",
                    Refusal.FILE_OUTSIDE_PROJECT,
                    Refusal.FILE_MISSING,
                    Refusal.NOT_A_FILE,
                    "editing-file-exists: a file to create appeared since the language server answered",
                    Refusal.STALE_EDIT_OF_FILES,
                    Refusal.NOT_READY)
            .withNotes("""
                The last operand is the package, such as demo.billing; the other operands are the files to move.
                The package is created, with its folder, when it does not exist; a dry run creates nothing.
                The default package cannot be the target.
                The files must be Java sources of one source folder of the project.
                The references and the imports of the project follow the moved classes, the fully qualified names
                too, and a nested type keeps its outer type (Invoice.Line). A name in a string literal or a comment
                is not rewritten.
                jdtls does not sort the imports it writes: run gabier organize-imports on the modified files.
                A member that was package-private in the old package, used from there, is no longer reachable.
                Run gabier diagnostics on the files that use it after the move.\
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
                        command().commandName(),
                        Map.of(
                                "files", files.stream().map(Path::toString).collect(Collectors.joining("\n")),
                                "package", operands.operand(last),
                                "dryRun", String.valueOf(arguments.dryRun())))
                .print();
    }
}
