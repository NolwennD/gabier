package fr.craft.gabier.cli;

import java.util.Map;

/**
 * {@code create [--dry-run] <file> <class|interface|record|enum>}: the option may stand anywhere after the command. The
 * kind goes to the daemon as it is typed, and the daemon refuses a word that is not one of the four. It answers as
 * {@code rename} does: the file created, then the diff.
 */
final class Create implements Capability {

    static final Command COMMAND = Command.called("create")
            .summary("create a file that holds an empty class, interface, record or enum")
            .usage("gabier [--json] create [--dry-run] <file> <class|interface|record|enum>")
            .example("""
                $ gabier create --dry-run src/main/java/demo/Widget.java class
                dry run, nothing written
                created /work/demo/src/main/java/demo/Widget.java

                --- /dev/null
                +++ /work/demo/src/main/java/demo/Widget.java
                @@ -0,0 +1,5 @@
                +package demo;
                +
                +public class Widget {
                +
                +}\
                """)
            .asksTheDaemon(
                    "editing-file-exists: a file is already there; nothing is overwritten",
                    "editing-folder-missing: the folder of the file does not exist; create it first, gabier creates"
                            + " no folder",
                    "editing-not-a-type-name: the name of the file, without .java, is not the name of a type",
                    "language-server-not-creatable: the file is under no source folder of the project, or the Java"
                            + " level of the project has no such type",
                    Refusal.FILE_OUTSIDE_PROJECT,
                    "invalid-parameter: the kind is not class, interface, record or enum",
                    Refusal.NOT_READY)
            .withNotes("""
                The name of the type is the name of the file; the package line comes from the folder of the file.
                A file in the default package has no package line.
                The new type has no comment and an empty body.
                The folder of the file must exist: gabier creates a file, not a folder.\
                """);

    @Override
    public Command command() {
        return COMMAND;
    }

    @Override
    public void run(Invocation invocation) {
        var arguments = invocation.arguments();
        var operands = arguments.operands(2);
        ChangedFilesReply.ask(
                        invocation,
                        command().commandName(),
                        Map.of(
                                "file", operands.file(0).toString(),
                                "kind", operands.operand(1),
                                "dryRun", String.valueOf(arguments.dryRun())))
                .print();
    }
}
