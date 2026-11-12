import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** {@code rename}, {@code organize-imports} and {@code cleanup} against the daemon served by the fake language server. */
final class RewriteChecks extends Checks {

    RewriteChecks(Session session) {
        super(session);
    }

    /**
     * {@code gabier rename} against the daemon served by the fake language server: the dry run writes nothing, the
     * renames write the files of the project and list them with the diff, a refusal writes nothing.
     */
    void renameWithTheFakeServer() throws IOException, InterruptedException {
        Path project = directory.resolve("rename-project");
        Process process = startDaemon(fakeDaemon, project.toString());
        if (process == null) {
            failed();
            return;
        }
        Path source = project.resolve("src/main/java/demo");
        String greeter = "rename-project/src/main/java/demo/Greeter.java";
        String original = Files.readString(source.resolve("Greeter.java"));

        Run dryRun = gabier("rename", "--dry-run", "--token", greeter, "greet", "welcome");
        expect(
                dryRun.exit() == 0
                        && dryRun.err().isEmpty()
                        && dryRun.out().lines().findFirst().orElse("").equals("dry run, nothing written")
                        && dryRun.out().contains("modified " + source.resolve("Greeter.java"))
                        && dryRun.out().contains("+    public String welcome(String name) {")
                        && Files.readString(source.resolve("Greeter.java")).equals(original),
                "gabier rename --dry-run --token lists the files and the diff and writes nothing");

        Run noSymbol = gabier("rename", greeter, "5:1", "anything");
        expect(
                noSymbol.exit() == 2
                        && noSymbol.out().isEmpty()
                        && noSymbol.err().contains("(language-server-no-such-symbol; ")
                        && Files.readString(source.resolve("Greeter.java")).equals(original),
                "a rename where there is no symbol is refused as a problem object, exit 2, nothing written");
        Run outside = gabier("rename", "outside/Elsewhere.java", "1:1", "anything");
        expect(
                outside.exit() == 2 && outside.err().contains("(language-server-file-outside-project; "),
                "a rename of a file outside the project is refused as a problem object, exit 2");

        Run byPosition = gabier("rename", greeter, "6:19", "welcome");
        expect(
                byPosition.exit() == 0
                        && byPosition.out().contains("modified " + source.resolve("Greeter.java"))
                        && byPosition.out().contains("modified " + source.resolve("Main.java"))
                        && Files.readString(source.resolve("Greeter.java")).contains("public String welcome(")
                        && Files.readString(source.resolve("Main.java")).contains("greeter.welcome(\"world\")"),
                "gabier rename by position, from 1, writes the files that use the symbol and lists them");

        Run type = gabier("--json", "rename", greeter, "3:14", "Welcomer");
        expect(
                type.exit() == 0
                        && type.out()
                                .contains("\"renamed\":[{\"from\":\"" + source.resolve("Greeter.java") + "\",\"to\":\""
                                        + source.resolve("Welcomer.java") + "\"}]")
                        && type.out().contains("\"diff\":\"--- ")
                        && Files.exists(source.resolve("Welcomer.java"))
                        && !Files.exists(source.resolve("Greeter.java")),
                "gabier --json rename of a type gives the files moved and the diff, and moves the file");

        String ruler = "rename-project/src/main/java/legacy/tools/Ruler.java";
        Path tools = project.resolve("src/main/java/tools/Ruler.java");
        Run packageDryRun = gabier("rename", "--dry-run", ruler, "1:19", "tools");
        expect(
                packageDryRun.exit() == 0
                        && packageDryRun
                                .out()
                                .contains("renamed " + project.resolve("src/main/java/legacy/tools/Ruler.java") + " -> "
                                        + tools)
                        && Files.exists(project.resolve("src/main/java/legacy/tools/Ruler.java"))
                        && !Files.exists(tools),
                "gabier rename --dry-run on a package line lists the move of its files and writes nothing");
        Run packageRename = gabier("rename", ruler, "1:19", "tools");
        expect(
                packageRename.exit() == 0
                        && Files.readString(tools).startsWith("package tools;")
                        && !Files.exists(project.resolve("src/main/java/legacy"))
                        && Files.isDirectory(project.resolve("src/main/java")),
                "gabier rename on a package line moves its files to the folder of the new name and removes the"
                        + " folders it empties");
        gabier("stop");
        expect(ends(process), "the daemon served by the fake stops after the renames");
    }

    /**
     * {@code gabier organize-imports} against the daemon served by the fake language server: the dry run writes
     * nothing, the file is organized and listed with the diff, a file in order says so, a refusal writes nothing.
     */
    void organizeImportsWithTheFakeServer() throws IOException, InterruptedException {
        Path project = directory.resolve("organize-project");
        Process process = startDaemon(fakeDaemon, project.toString());
        if (process == null) {
            failed();
            return;
        }
        Path source = project.resolve("src/main/java/demo");
        String untidy = "organize-project/src/main/java/demo/Untidy.java";
        String original = Files.readString(source.resolve("Untidy.java"));

        Run dryRun = gabier("organize-imports", "--dry-run", untidy);
        expect(
                dryRun.exit() == 0
                        && dryRun.err().isEmpty()
                        && dryRun.out().lines().findFirst().orElse("").equals("dry run, nothing written")
                        && dryRun.out().contains("modified " + source.resolve("Untidy.java"))
                        && dryRun.out().contains("-import java.util.Set;")
                        && dryRun.out().contains("+import java.util.ArrayList;")
                        && Files.readString(source.resolve("Untidy.java")).equals(original),
                "gabier organize-imports --dry-run lists the file and the diff and writes nothing");

        Run outside = gabier("organize-imports", "outside/Elsewhere.java");
        expect(
                outside.exit() == 2
                        && outside.out().isEmpty()
                        && outside.err().contains("(language-server-file-outside-project; "),
                "organize-imports of a file outside the project is refused as a problem object, exit 2");

        Run organized = gabier("organize-imports", untidy);
        String text = Files.readString(source.resolve("Untidy.java"));
        expect(
                organized.exit() == 0
                        && organized.out().contains("modified " + source.resolve("Untidy.java"))
                        && text.contains("import java.util.ArrayList;")
                        && text.contains("import java.util.Collections;")
                        && !text.contains("import java.util.Set;")
                        && !text.contains(".*;"),
                "gabier organize-imports writes the file, with the missing imports added and the unused one removed");

        String tidy = "organize-project/src/main/java/demo/Tidy.java";
        Run inOrder = gabier("organize-imports", tidy);
        expect(
                inOrder.exit() == 0 && inOrder.out().equals("nothing to change"),
                "gabier organize-imports of a file whose imports are in order says there is nothing to change");

        Run json = gabier("--json", "organize-imports", tidy);
        expect(
                json.exit() == 0
                        && json.out()
                                .equals("{\"created\":[],\"renamed\":[],\"deleted\":[],\"modified\":[],\"diff\":\"\"}"),
                "gabier --json organize-imports gives the result as the daemon gave it");
        gabier("stop");
        expect(ends(process), "the daemon served by the fake stops after organizing the imports");
    }

    /**
     * {@code gabier cleanup} against the daemon served by the fake language server: the dry run writes nothing, the
     * concatenation of the file is rewritten into a text block, a file with nothing to clean says so, an identifier
     * the language server does not know and a file outside the project are refused as problem objects and write
     * nothing.
     */
    void cleanupWithTheFakeServer() throws IOException, InterruptedException {
        Path project = directory.resolve("cleanup-project");
        Process process = startDaemon(fakeDaemon, project.toString());
        if (process == null) {
            failed();
            return;
        }
        Path source = project.resolve("src/main/java/demo");
        String banner = "cleanup-project/src/main/java/demo/Banner.java";
        String original = Files.readString(source.resolve("Banner.java"));

        Run dryRun = gabier("cleanup", "--dry-run", banner, "stringConcatToTextBlock");
        expect(
                dryRun.exit() == 0
                        && dryRun.err().isEmpty()
                        && dryRun.out().lines().findFirst().orElse("").equals("dry run, nothing written")
                        && dryRun.out().contains("modified " + source.resolve("Banner.java"))
                        && dryRun.out().contains("+        return \"\"\"")
                        && Files.readString(source.resolve("Banner.java")).equals(original),
                "gabier cleanup --dry-run lists the file and the diff and writes nothing");

        Run unknown = gabier("cleanup", banner, "textBlocks");
        expect(
                unknown.exit() == 2
                        && unknown.out().isEmpty()
                        && unknown.err().contains("(editing-unknown-clean-up; ")
                        && Files.readString(source.resolve("Banner.java")).equals(original),
                "cleanup with an identifier the language server does not know is refused as a problem object, exit 2");

        Run outside = gabier("cleanup", "outside/Elsewhere.java", "stringConcatToTextBlock");
        expect(
                outside.exit() == 2
                        && outside.out().isEmpty()
                        && outside.err().contains("(language-server-file-outside-project; "),
                "cleanup of a file outside the project is refused as a problem object, exit 2");

        Run cleaned = gabier("cleanup", banner, "stringConcatToTextBlock");
        String text = Files.readString(source.resolve("Banner.java"));
        expect(
                cleaned.exit() == 0
                        && cleaned.out().contains("modified " + source.resolve("Banner.java"))
                        && text.contains("return \"\"\"")
                        && text.contains("Hello, \"world\"!")
                        && !text.contains("+ \""),
                "gabier cleanup writes the file, with the concatenation rewritten into a text block");

        Run clean = gabier("cleanup", "cleanup-project/src/main/java/demo/Plain.java", "stringConcatToTextBlock");
        expect(
                clean.exit() == 0 && clean.out().equals("nothing to change"),
                "gabier cleanup of a file with nothing to clean says there is nothing to change");
        gabier("stop");
        expect(ends(process), "the daemon served by the fake stops after the clean-up");
    }
}
