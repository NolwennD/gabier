package fr.craft.gabier.cli;

import java.util.Map;

/**
 * {@code move-type-to-file [--dry-run] <file> <line>:<column>}: the option may stand anywhere after the command. The
 * position is from 1 and goes to the daemon from 0. It answers as {@code rename} does: the files created and modified,
 * then the diff.
 */
final class MoveTypeToFile implements Capability {

    static final Command COMMAND = Command.called("move-type-to-file")
            .summary("make a nested type a top-level type in a file of its own")
            .usage("gabier [--json] move-type-to-file [--dry-run] <file> <line>:<column>")
            .example("""
                $ gabier move-type-to-file --dry-run src/main/java/demo/Shapes.java 6:25
                dry run, nothing written
                modified /work/demo/src/main/java/demo/app/Drawing.java
                modified /work/demo/src/main/java/demo/Shapes.java
                created /work/demo/src/main/java/demo/Circle.java
                ...\
                """)
            .asksTheDaemon(
                    "language-server-not-movable: no nested type can be moved at the position (outside any type, a"
                            + " top-level type, or a file of the name of the type exists in its package)",
                    Refusal.FILE_OUTSIDE_PROJECT,
                    Refusal.FILE_MISSING,
                    Refusal.NOT_A_FILE,
                    "editing-file-exists: the file to create appeared since the language server answered",
                    Refusal.STALE_EDIT_OF_FILES,
                    Refusal.NOT_READY)
            .withNotes("""
                The position is inside the type to move: the innermost type that encloses it moves.
                An inner class that uses its outer instance gets a field and a constructor parameter of the outer type.
                A JUnit @Nested test class can then no longer be instantiated by JUnit.
                To avoid it, first make the class independent of the outer instance,
                for example by having it extend the shared base class, then move it.
                Each annotation of the class, such as @Nested, stays on it after the move.\
                """);

    @Override
    public Command command() {
        return COMMAND;
    }

    @Override
    public void run(Invocation invocation) {
        var arguments = invocation.arguments();
        var operands = arguments.operands(2);
        var position = operands.position(1);
        ChangedFilesReply.ask(
                        invocation,
                        command().commandName(),
                        Map.of(
                                "file", operands.file(0).toString(),
                                "line", String.valueOf(position.line()),
                                "column", String.valueOf(position.column()),
                                "dryRun", String.valueOf(arguments.dryRun())))
                .print();
    }
}
