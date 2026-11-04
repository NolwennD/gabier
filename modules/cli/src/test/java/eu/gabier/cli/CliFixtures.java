package eu.gabier.cli;

import eu.gabier.protocol.ChangedFiles;
import eu.gabier.protocol.Problem;
import eu.gabier.shared.error.domain.ErrorStatus;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** The refusal and the answers that the tests of the commands share, so that each says only what is its own. */
final class CliFixtures {

    /** A refusal of the daemon, as any command can get one. */
    static final Problem REFUSAL = new Problem(
            "urn:gabier:error:some-key",
            "Some key",
            "It went wrong",
            "some-key",
            Map.of("a", "1"),
            ErrorStatus.BAD_REQUEST);

    /** Two files modified, with the diff of each. */
    static final ChangedFiles MODIFIED = new ChangedFiles(
            List.of(),
            List.of(),
            List.of(),
            List.of(Path.of("/project/Greeter.java"), Path.of("/project/Main.java")),
            """
            --- /project/Greeter.java
            +++ /project/Greeter.java
            @@ -1 +1 @@
            -    public String greet(String name) {
            +    public String welcome(String name) {
            --- /project/Main.java
            +++ /project/Main.java
            @@ -1 +1 @@
            -        System.out.println(greeter.greet("world"));
            +        System.out.println(greeter.welcome("world"));
            """);

    /** A file created, one renamed, one deleted and one modified. */
    static final ChangedFiles IN_EVERY_WAY = new ChangedFiles(
            List.of(Path.of("/project/New.java")),
            List.of(new ChangedFiles.Moved(Path.of("/project/Greeter.java"), Path.of("/project/Welcomer.java"))),
            List.of(Path.of("/project/Gone.java")),
            List.of(Path.of("/project/Main.java")),
            "--- /project/Main.java\n+++ /project/Main.java\n");

    /** No file changed. */
    static final ChangedFiles NOTHING = new ChangedFiles(List.of(), List.of(), List.of(), List.of(), "");

    /** A file name and a diff that hold what a terminal would obey. */
    static final ChangedFiles HOSTILE = new ChangedFiles(
            List.of(),
            List.of(),
            List.of(),
            List.of(Path.of("/project/a\u001b[2Jb.java")),
            "--- /project/a\u001b[2Jb.java\n+forged\u001b[2J\n");

    /** Two warnings, the second with a text from a file of the project that a terminal would obey. */
    static final List<String> WARNINGS = List.of(
            "java.completion.importOrder: .settings/org.eclipse.jdt.ui.prefs says [\"#\", \"\"] and"
                    + " .vscode/settings.json says [\"java\"]: following .settings",
            "ignored .vscode/settings.json: \u001b[2Jforged");

    /** The settings of the imports of a project with no file for them, as the daemon writes them. */
    static final String IMPORTS = """
        {"importOrder":{"value":["java","javax","org","com"],"source":"default"},\
        "starThreshold":{"value":99,"source":"default"},\
        "staticStarThreshold":{"value":99,"source":"default"}}\
        """;

    private CliFixtures() {}

    /** What a command that changes files can answer: a change, every kind of change, and no change. */
    static Stream<ChangedFiles> answers() {
        return Stream.of(MODIFIED, IN_EVERY_WAY, NOTHING);
    }
}
