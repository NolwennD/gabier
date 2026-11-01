package fr.craft.gabier.cli;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * The commands of the cli and their help text, written once, here: {@code gabier help} prints it, the dispatch of
 * {@link Capabilities} switches on it (so a command added here and given no capability does not compile), and the
 * usage error points to {@code gabier help}. A command without a summary, a usage, an example or a refusal does not
 * pass {@code CommandTest}.
 * To add a command, add its constant here, in the order {@code gabier help} lists them.
 */
enum Command {
    DIAGNOSTICS(
            "diagnostics",
            "the errors and warnings of a Java file, without a build",
            "gabier [--json] diagnostics <file>",
            """
            $ gabier diagnostics src/main/java/demo/Broken.java
            src/main/java/demo/Broken.java:5:16: error: Type mismatch: cannot convert from String to int\
            """,
            requestRefusals(
                    "language-server-file-outside-project: the file is not in the project the daemon serves",
                    "language-server-file-missing: the path is under the project and no file is there",
                    "language-server-not-a-file: the path is a folder",
                    "language-server-not-ready: the Java language server is not ready")),
    RENAME(
            "rename",
            "rename a symbol, and everything that refers to it, in every file",
            """
            gabier [--json] rename [--dry-run] <file> <line> <column> <new-name>
            gabier [--json] rename [--dry-run] --token <file> <identifier> <new-name>\
            """,
            """
            $ gabier rename --dry-run src/main/java/demo/Greeter.java 6 19 welcome
            dry run, nothing written
            modified /work/demo/src/main/java/demo/Greeter.java
            modified /work/demo/src/main/java/demo/Main.java
            ...\
            """,
            requestRefusals(
                    "language-server-no-such-symbol: no symbol at the position",
                    "language-server-invalid-name: the language server declines the new name",
                    "editing-token-not-found: the identifier is not in the file (--token)",
                    "editing-file-missing: with --token, no file is at the path",
                    "language-server-file-outside-project: the file is not in the project the daemon serves",
                    "language-server-file-missing: the path is under the project and no file is there",
                    "language-server-not-a-file: the path is a folder",
                    "editing-stale-edit: the change cannot be applied to the files as they are",
                    "language-server-not-ready: the Java language server is not ready")),
    ORGANIZE_IMPORTS(
            "organize-imports",
            "add the missing imports, remove the unused ones and order the rest, never with a star import",
            "gabier [--json] organize-imports [--dry-run] <file>",
            """
            $ gabier organize-imports src/main/java/demo/Tidy.java
            nothing to change\
            """,
            requestRefusals(
                    "language-server-ambiguous-imports: several packages declare a type the file uses without"
                            + " importing it; the candidates are in the problem",
                    "language-server-unresolved-imports: jdtls would delete imports it reports an error on",
                    "language-server-file-outside-project: the file is not in the project the daemon serves",
                    "language-server-file-missing: the path is under the project and no file is there",
                    "language-server-not-a-file: the path is a folder",
                    "editing-stale-edit: the change cannot be applied to the file as it is",
                    "language-server-not-ready: the Java language server is not ready")),
    EXTRACT_VARIABLE(
            "extract-variable",
            "turn an expression into a local variable",
            "gabier [--json] extract-variable [--dry-run] <file> <line>:<column> <line>:<column> <name>",
            """
            $ gabier extract-variable --dry-run src/main/java/demo/Greeter.java 7:26 7:48 joined
            dry run, nothing written
            modified /work/demo/src/main/java/demo/Greeter.java
            ...\
            """,
            requestRefusals(
                    "language-server-not-an-expression: the selection is not an expression jdtls can extract",
                    "language-server-invalid-name: the name is not a Java identifier, or is a parameter of the method",
                    "language-server-file-outside-project: the file is not in the project the daemon serves",
                    "language-server-file-missing: the path is under the project and no file is there",
                    "language-server-not-a-file: the path is a folder",
                    "editing-stale-edit: the change cannot be applied to the file as it is",
                    "language-server-not-ready: the Java language server is not ready")),
    EXTRACT_METHOD(
            "extract-method",
            "turn statements, or an expression, into a method",
            "gabier [--json] extract-method [--dry-run] <file> <line>:<column> <line>:<column> <name>",
            """
            $ gabier extract-method --dry-run src/main/java/demo/Totals.java 6:9 6:47 printCount
            dry run, nothing written
            modified /work/demo/src/main/java/demo/Totals.java
            ...\
            """,
            requestRefusals(
                    "language-server-not-extractable: the selection is not statements or an expression jdtls can"
                            + " extract (half a statement, an if whose branch returns)",
                    "language-server-invalid-name: the name is not a Java identifier, or another method of the class"
                            + " has it with the same parameters",
                    "language-server-file-outside-project: the file is not in the project the daemon serves",
                    "language-server-file-missing: the path is under the project and no file is there",
                    "language-server-not-a-file: the path is a folder",
                    "editing-stale-edit: the change cannot be applied to the file as it is",
                    "language-server-not-ready: the Java language server is not ready")),
    MOVE_TYPE_TO_FILE(
            "move-type-to-file",
            "make a nested type a top-level type in a file of its own",
            "gabier [--json] move-type-to-file [--dry-run] <file> <line>:<column>",
            """
            $ gabier move-type-to-file --dry-run src/main/java/demo/Shapes.java 6:25
            dry run, nothing written
            modified /work/demo/src/main/java/demo/app/Drawing.java
            modified /work/demo/src/main/java/demo/Shapes.java
            created /work/demo/src/main/java/demo/Circle.java
            ...\
            """,
            """
            The position is inside the type to move: the innermost type that encloses it moves.
            An inner class that uses its outer instance gets a field and a constructor parameter of the outer type.
            A JUnit @Nested test class can then no longer be instantiated by JUnit.
            To avoid it, first make the class independent of the outer instance,
            for example by having it extend the shared base class, then move it.\
            """,
            requestRefusals(
                    "language-server-not-movable: no nested type can be moved at the position (outside any type, a"
                            + " top-level type, or a file of the name of the type exists in its package)",
                    "language-server-file-outside-project: the file is not in the project the daemon serves",
                    "language-server-file-missing: the path is under the project and no file is there",
                    "language-server-not-a-file: the path is a folder",
                    "editing-file-exists: the file to create appeared since the language server answered",
                    "editing-stale-edit: the change cannot be applied to the files as they are",
                    "language-server-not-ready: the Java language server is not ready")),
    INLINE(
            "inline",
            "replace a local variable, a method or a constant by its value",
            "gabier [--json] inline [--dry-run] <file> <line>:<column>",
            """
            $ gabier inline --dry-run src/main/java/demo/Prices.java 4:22
            dry run, nothing written
            modified /work/demo/src/main/java/demo/Prices.java
            modified /work/demo/src/main/java/demo/Checkout.java
            ...\
            """,
            """
            The position is on the name of a local variable, a method or a constant.
            A local variable is inlined everywhere, whatever the position.
            A position on the declaration of a method or a constant inlines every use and removes it;
            a position on one use inlines that use only and keeps the declaration.\
            """,
            requestRefusals(
                    "language-server-not-inlinable: nothing the server can inline at the position (not on a variable,"
                            + " a method or a constant; a variable assigned more than once; a recursive or abstract"
                            + " method; a declaration that nothing uses)",
                    "language-server-file-outside-project: the file is not in the project the daemon serves",
                    "language-server-file-missing: the path is under the project and no file is there",
                    "language-server-not-a-file: the path is a folder",
                    "editing-stale-edit: the change cannot be applied to the files as they are",
                    "language-server-not-ready: the Java language server is not ready")),
    STATUS(
            "status",
            "the project the daemon serves, and where jdtls is",
            "gabier [--json] status",
            """
            $ gabier status
            gabier-daemon 0.1, pid 4242, up 65s, project /work/demo, jdtls not-started\
            """,
            runningDaemonRefusals()),
    STOP("stop", "stop the daemon", "gabier [--json] stop", """
        $ gabier stop
        gabier-daemon stopped\
        """, runningDaemonRefusals()),
    HELP("help", "list the commands, or explain one", """
        gabier [--json] help [<command>]
        gabier [--json] --help
        gabier [--json] -h\
        """, """
            $ gabier help rename
            gabier [--json] rename [--dry-run] <file> <line> <column> <new-name>
            ...\
            """, List.of(Refusal.USAGE));

    private final String commandName;
    private final String summary;
    private final String usage;
    private final String example;
    private final String notes;
    private final List<String> refusals;

    Command(String commandName, String summary, String usage, String example, List<String> refusals) {
        this(commandName, summary, usage, example, "", refusals);
    }

    Command(String commandName, String summary, String usage, String example, String notes, List<String> refusals) {
        this.commandName = commandName;
        this.summary = summary;
        this.usage = usage;
        this.example = example;
        this.notes = notes;
        this.refusals = refusals;
    }

    /** The refusals of a command that asks the daemon, starting it if need be: its own, then the ones of every such command. */
    private static List<String> requestRefusals(String... own) {
        return Stream.concat(
                        Arrays.stream(own), Stream.of(Refusal.USAGE, Refusal.START_FAILED, Refusal.CONNECTION_FAILED))
                .toList();
    }

    /** The refusals of a command that asks a daemon that is running and never starts one. */
    private static List<String> runningDaemonRefusals() {
        return List.of(Refusal.NOT_RUNNING, Refusal.USAGE, Refusal.CONNECTION_FAILED);
    }

    /** The command whose name this is, if any. */
    static Optional<Command> named(String name) {
        return Arrays.stream(values())
                .filter(command -> command.commandName.equals(name))
                .findFirst();
    }

    /** The command a word with one typo (a letter changed, added or removed, two swapped) was meant for, if any. */
    static Optional<Command> nearestTo(String word) {
        return Arrays.stream(values())
                .filter(command -> EditDistance.between(command.commandName, word) == 1)
                .findFirst();
    }

    /** The word that names it on the command line. */
    String commandName() {
        return commandName;
    }

    /** One line: what it does. */
    String summary() {
        return summary;
    }

    /** The command line, one line per form. */
    String usage() {
        return usage;
    }

    /** An example of a call and of its answer, the call on a line that starts with {@code $ }. */
    String example() {
        return example;
    }

    /** What a person must know before running the command, none (empty) for most of them. */
    String notes() {
        return notes;
    }

    /** The refusals of the command, each as the key of its problem, a colon and what it means. */
    List<String> refusals() {
        return refusals;
    }

    /** The refusals that more than one command answers, each as the key of its problem, a colon and what it means. */
    private static final class Refusal {

        static final String USAGE =
                "usage-error: the arguments are not those of the command (gabier help <command> gives its usage)";
        static final String START_FAILED =
                "daemon-start-failed: no daemon answered and gabier-daemon could not be started";
        static final String CONNECTION_FAILED =
                "daemon-connection-failed: the connection to the daemon was lost, or it hung up without answering";
        static final String NOT_RUNNING = "daemon-not-running: no daemon answers; the command never starts one";

        private Refusal() {}
    }
}
