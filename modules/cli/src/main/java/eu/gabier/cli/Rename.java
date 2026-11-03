package eu.gabier.cli;

import java.util.Map;

/**
 * {@code rename [--dry-run] <file> <line>:<column> <new-name>} or {@code rename [--dry-run] --token <file>
 * <identifier> <new-name>}: the options may stand anywhere after the command. The position is from 1, as {@code
 * diagnostics} shows it, and goes to the daemon from 0. The text lists the files that changed, then the diff, with the
 * same neutralisation as every text that comes from the project; {@code --json} gives the result as the daemon gave it.
 */
final class Rename implements Capability {

    static final Command COMMAND = Command.called("rename")
            .summary("rename a symbol, and everything that refers to it, in every file")
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
            .asksTheDaemon(
                    "language-server-no-such-symbol: no symbol at the position",
                    "language-server-invalid-name: the language server declines the new name",
                    "language-server-empty-package: the package named up to the position holds no class of its own,"
                            + " so there is nothing to rename (put the position on its last segment)",
                    "editing-token-not-found: the identifier is not in the file (--token)",
                    "editing-file-missing: with --token, no file is at the path",
                    Refusal.FILE_OUTSIDE_PROJECT,
                    Refusal.FILE_MISSING,
                    Refusal.NOT_A_FILE,
                    Refusal.STALE_EDIT_OF_FILES,
                    Refusal.NOT_READY)
            .withNotes("""
                A position on the name in a package line renames the package, with its imports and its references: its files
                move to the folder of the new name and the folders left empty are removed. Give the whole new name
                (demo.shop); the segment the position is on decides how much of the old name is renamed (on app in
                demo.app.sub, demo.app); a package with no class of its own (demo in demo.app) is refused as
                language-server-empty-package. Sub-packages keep their names, and the same package in another source set
                (the tests) is a rename of its own. A package that exists, or a name that is not a package name, is
                refused as language-server-invalid-name. module-info.java, string literals and build files are not
                changed: edit them as text.\
                """);

    @Override
    public Command command() {
        return COMMAND;
    }

    @Override
    public void run(Invocation invocation) {
        var arguments = invocation.arguments();
        var dryRun = String.valueOf(arguments.dryRun());
        ChangedFilesReply reply;
        if (arguments.has(Arguments.TOKEN)) {
            var operands = arguments.operands(3, Arguments.TOKEN);
            reply = ChangedFilesReply.ask(
                    invocation,
                    "rename-token",
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
                    command().commandName(),
                    Map.of(
                            "file", operands.file(0).toString(),
                            "line", String.valueOf(position.line()),
                            "column", String.valueOf(position.column()),
                            "newName", operands.operand(2),
                            "dryRun", dryRun));
        }
        reply.print();
    }
}
