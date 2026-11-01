package fr.craft.gabier.cli;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

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
            List.of(
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
            List.of(
                    "language-server-no-such-symbol: no symbol at the position",
                    "language-server-invalid-name: the language server declines the new name",
                    "editing-token-not-found: the identifier is not in the file (--token)",
                    "language-server-file-outside-project: the file is not in the project the daemon serves",
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
            List.of(
                    "language-server-ambiguous-imports: several packages declare a type the file uses without"
                            + " importing it; the candidates are in the problem",
                    "language-server-unresolved-imports: jdtls would delete imports it reports an error on",
                    "language-server-file-outside-project: the file is not in the project the daemon serves",
                    "language-server-file-missing: the path is under the project and no file is there",
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
            List.of(
                    "language-server-not-an-expression: the selection is not an expression jdtls can extract",
                    "language-server-invalid-name: the name is not a Java identifier, or is a parameter of the method",
                    "language-server-file-outside-project: the file is not in the project the daemon serves",
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
            List.of(
                    "language-server-not-extractable: the selection is not statements or an expression jdtls can"
                            + " extract (half a statement, an if whose branch returns)",
                    "language-server-invalid-name: the name is not a Java identifier, or another method of the class"
                            + " has it with the same parameters",
                    "language-server-file-outside-project: the file is not in the project the daemon serves",
                    "language-server-not-ready: the Java language server is not ready")),
    STATUS(
            "status",
            "the project the daemon serves, and where jdtls is",
            "gabier [--json] status",
            """
            $ gabier status
            gabier-daemon 0.1, pid 4242, up 65s, project /work/demo, jdtls not-started\
            """,
            List.of("daemon-not-running: no daemon answers; status never starts one")),
    STOP(
            "stop",
            "stop the daemon",
            "gabier [--json] stop",
            """
            $ gabier stop
            gabier-daemon stopped\
            """,
            List.of("daemon-not-running: no daemon answers; stop never starts one")),
    HELP(
            "help",
            "list the commands, or explain one",
            """
            gabier [--json] help [<command>]
            gabier [--json] --help
            gabier [--json] -h\
            """,
            """
            $ gabier help rename
            gabier [--json] rename [--dry-run] <file> <line> <column> <new-name>
            ...\
            """,
            List.of("usage-error: the command is unknown (the problem lists the known ones)"));

    private final String commandName;
    private final String summary;
    private final String usage;
    private final String example;
    private final List<String> refusals;

    Command(String commandName, String summary, String usage, String example, List<String> refusals) {
        this.commandName = commandName;
        this.summary = summary;
        this.usage = usage;
        this.example = example;
        this.refusals = refusals;
    }

    /** The command whose name this is, if any. */
    static Optional<Command> named(String name) {
        return Arrays.stream(values())
                .filter(command -> command.commandName.equals(name))
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

    /** The refusals of the command, each as the key of its problem, a colon and what it means. */
    List<String> refusals() {
        return refusals;
    }
}
