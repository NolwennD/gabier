import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

/** The native cli alone: version, usage errors, help, the guide, init-agent and the socket of a project. */
final class CliChecks extends Support {

    /**
     * 0 when {@code gabier --version} prints {@code gabier <semantic version>} on the standard output, nothing on the
     * other, and exits 0: the native image carries the version the build was given ({@code 0.0.0-dev} with none), so it
     * reads its resource.
     */
    static int version(Path binary, Path directory) throws IOException, InterruptedException {
        Run run = runBinary(binary, directory, "--version");
        System.out.println("gabier --version -> \"" + run.out() + "\" (exit " + run.exit() + ")");

        return run.exit() == 0
                        && SEMANTIC_VERSION_LINE.matcher(run.out().strip()).matches()
                        && run.err().isEmpty()
                ? 0
                : 1;
    }

    /**
     * 0 when an unknown argument is refused as a problem object on the standard error (one line of text and the line
     * that points to the guide, or a JSON object with {@code --json}) with exit code 2 and nothing on the standard
     * output.
     */
    static int usageError(Path binary, Path directory, boolean json) throws IOException, InterruptedException {
        Run run = json ? runBinary(binary, directory, "--json", "--bogus") : runBinary(binary, directory, "--bogus");
        System.out.println("gabier " + (json ? "--json " : "") + "--bogus -> stdout \"" + run.out() + "\", stderr \""
                + run.err() + "\" (exit " + run.exit() + ")");
        boolean problem = json
                ? run.err().equals(USAGE_ERROR_JSON)
                : run.err().startsWith("Usage error: ")
                        && run.err().lines().skip(1).toList().equals(List.of(USAGE_ERROR_POINTER));
        long lines = json ? 1 : 2;

        return run.exit() == 2
                        && run.out().isEmpty()
                        && problem
                        && run.err().lines().count() == lines
                ? 0
                : 1;
    }

    /**
     * 0 when {@code gabier help}, {@code --help} and {@code -h} list the commands on the standard output with exit 0, and
     * {@code help rename} explains one and {@code help guide} prints the guide of the improvements from the resources of the
     * image, in an empty directory with no daemon and no clone.
     */
    static int help(Path binary, Path directory) throws IOException, InterruptedException {
        int failures = 0;
        for (String spelling : List.of("help", "--help", "-h")) {
            Run run = runBinary(binary, directory, spelling);
            System.out.println(
                    "gabier " + spelling + " -> " + run.out().lines().count() + " lines (exit " + run.exit() + ")");
            failures += run.exit() == 0 && run.err().isEmpty() && run.out().contains("  extract-variable  ") ? 0 : 1;
        }
        Run one = runBinary(binary, directory, "help", "rename");
        System.out.println("gabier help rename -> " + one.out().lines().count() + " lines (exit " + one.exit() + ")");

        failures += one.exit() == 0 && one.err().isEmpty() && one.out().contains("Refusals") ? 0 : 1;
        Run guide = runBinary(binary, directory, "help", "guide");
        System.out.println(
                "gabier help guide -> " + guide.out().lines().count() + " lines (exit " + guide.exit() + ")");

        failures += guide.exit() == 0
                        && guide.err().isEmpty()
                        && guide.out().startsWith("# Improving Java with gabier")
                        && guide.out().contains("stringConcatToTextBlock")
                        && guide.out().contains("By hand: ")
                ? 0
                : 1;
        Run agents = runBinary(binary, directory, "help", "agents");
        System.out.println(
                "gabier help agents -> " + agents.out().lines().count() + " lines (exit " + agents.exit() + ")");

        failures += agents.exit() == 0
                        && agents.err().isEmpty()
                        && agents.out().startsWith("## Java with gabier")
                        && agents.out().contains("`gabier start`")
                ? 0
                : 1;

        return failures + askTheGuide(binary, directory);
    }

    /**
     * 0 when {@code init-agent} works from the native image, with no daemon, in a temporary project folder it is run in: a
     * snippet creates {@code AGENTS.md} with the marked block (the instructions of the resource, so the resource is in the
     * image), a second run replaces the block in place and keeps the text around it, a rule cut with {@code --only} keeps
     * the bullets of the chosen command only, a dry run of a skill writes nothing, and a command that does not exist in
     * {@code --only} is refused with exit 2 and nothing is written.
     */
    static int initAgent(Path binary, Path directory) throws IOException, InterruptedException {
        Path project = Files.createDirectory(directory.resolve("init-agent-project"));
        Path agents = project.resolve("AGENTS.md");
        Run first = runBinary(binary, project, "init-agent", "snippet");
        System.out.println("gabier init-agent snippet -> \"" + first.out() + "\" (exit " + first.exit() + ")");
        String written = Files.exists(agents) ? Files.readString(agents) : "";
        Files.writeString(agents, "Before.\n\n" + written + "\nAfter.\n");
        Run second = runBinary(binary, project, "init-agent", "snippet");
        System.out.println(
                "gabier init-agent snippet (again) -> \"" + second.out() + "\" (exit " + second.exit() + ")");
        String replaced = Files.readString(agents);
        Run rule = runBinary(binary, project, "init-agent", "--only=rename", "rule");
        System.out.println("gabier init-agent --only=rename rule -> \"" + rule.out() + "\" (exit " + rule.exit() + ")");
        String ruleText = Files.exists(project.resolve(".claude/rules/gabier.md"))
                ? Files.readString(project.resolve(".claude/rules/gabier.md"))
                : "";
        Run dry = runBinary(binary, project, "init-agent", "--dry-run", "skill");
        System.out.println("gabier init-agent --dry-run skill -> "
                + dry.out().lines().count() + " lines (exit " + dry.exit() + ")");
        Run unknown = runBinary(binary, project, "init-agent", "--only=frobnicate", "skill");
        System.out.println("gabier init-agent --only=frobnicate skill -> \""
                + unknown.err().lines().findFirst().orElse("") + "\" (exit " + unknown.exit() + ")");

        int failures = first.exit() == 0
                        && first.err().isEmpty()
                        && first.out().equals("created " + project.toRealPath().resolve("AGENTS.md"))
                        && written.startsWith("<!-- gabier:start -->\n")
                        && written.contains("## Java with gabier")
                ? 0
                : 1;
        failures += second.exit() == 0
                        && second.out().startsWith("replaced the gabier block of ")
                        && replaced.equals("Before.\n\n" + written + "\nAfter.\n")
                ? 0
                : 1;
        failures += rule.exit() == 0
                        && ruleText.contains("`gabier rename`")
                        && !ruleText.contains("`gabier start`")
                        && ruleText.contains("# Java with gabier")
                ? 0
                : 1;
        failures += dry.exit() == 0
                        && dry.out().startsWith("dry run, nothing written\nwould create ")
                        && !Files.exists(project.resolve(".claude/skills"))
                ? 0
                : 1;

        return failures
                + (unknown.exit() == 2
                                && unknown.out().isEmpty()
                                && unknown.err().contains("init-agent-unknown-command")
                                && unknown.err().endsWith("Usage and other ways: gabier help init-agent")
                                && !Files.exists(project.resolve(".claude/skills"))
                        ? 0
                        : 1);
    }

    /**
     * 0 when {@code help --for=<task>} answers from the guide in the resources of the image, in an empty directory with
     * an empty environment: the clean-up for the text block task first, with its command and a confidence; the same
     * answer as JSON; the section to do by hand for a task jdtls does not offer; a command asked for by its task
     * ("rename this method" names {@code gabier rename}); and, for a task with no relation to the guide, the plain
     * word that nothing matches, with exit 0.
     */
    static int askTheGuide(Path binary, Path directory) throws IOException, InterruptedException {
        Run text = runBinary(binary, directory, "help", "--for=turn this string concatenation into a text block");
        System.out.println("gabier help --for=<text block task> -> "
                + text.out().lines().count() + " lines (exit " + text.exit() + ")");
        Run json = runBinary(
                binary, directory, "--json", "help", "--for=turn this string concatenation into a text block");
        System.out.println("gabier --json help --for=<text block task> -> "
                + json.out().length() + " characters (exit " + json.exit() + ")");
        Run byHand = runBinary(binary, directory, "help", "--for=invert this if");
        System.out.println("gabier help --for=<invert this if> -> "
                + byHand.out().lines().count() + " lines (exit " + byHand.exit() + ")");
        Run command = runBinary(binary, directory, "help", "--for=rename this method");
        System.out.println("gabier help --for=<rename this method> -> "
                + command.out().lines().count() + " lines (exit " + command.exit() + ")");
        Run nothing = runBinary(binary, directory, "help", "--for=how do I bake bread");
        System.out.println("gabier help --for=<bake bread> -> \""
                + nothing.out().lines().findFirst().orElse("") + "\" (exit " + nothing.exit() + ")");

        int failures = text.exit() == 0
                        && text.err().isEmpty()
                        && text.out()
                                .contains("\n1. A string built from literals with `+` and line breaks (confidence ")
                        && text.out().contains("stringConcatToTextBlock")
                ? 0
                : 1;
        failures += json.exit() == 0
                        && json.err().isEmpty()
                        && json.out()
                                .startsWith("{\"question\":\"turn this string concatenation into a text"
                                        + " block\",\"matches\":[{\"rank\":1,\"title\":\"A string built from"
                                        + " literals")
                        && json.out().contains("\"kind\":\"ask\"")
                ? 0
                : 1;
        failures += byHand.exit() == 0
                        && byHand.err().isEmpty()
                        && byHand.out().contains("\n1. An `if` whose branches must trade places (confidence ")
                        && byHand.out().contains("   By hand: ")
                ? 0
                : 1;

        failures += command.exit() == 0
                        && command.err().isEmpty()
                        && command.out().contains("\n1. A name to change everywhere (confidence ")
                        && command.out().contains("   Ask: `gabier rename ")
                ? 0
                : 1;

        return failures
                + (nothing.exit() == 0
                                && nothing.err().isEmpty()
                                && nothing.out().startsWith("Nothing in the guide matches \"how do I bake bread\".")
                        ? 0
                        : 1);
    }

    /**
     * 0 when the native {@code gabier}, with no {@code GABIER_SOCKET}, looks for the socket of the project it is run in:
     * the same one from the root of a worktree and from a subfolder of it, another one for another worktree, each named
     * after its folder, in the runtime directory. {@code status} starts no daemon, and its refusal names the socket.
     */
    static int socketOfTheProject(Path binary, Path directory) throws IOException, InterruptedException {
        Path runtime = Files.createDirectory(directory.resolve("runtime"));
        Path one = Files.createDirectories(directory.resolve("one-worktree"));
        Files.createFile(one.resolve(".git"));
        Path sub = Files.createDirectories(one.resolve("modules/daemon"));
        Path two = Files.createDirectories(directory.resolve("two-worktree"));
        Files.createFile(two.resolve(".git"));
        List<String> environment = List.of("XDG_RUNTIME_DIR", runtime.toString());
        Pattern named = Pattern.compile(
                "socket=(" + Pattern.quote(runtime.toRealPath() + "/gabier/") + "(\\S+)-[0-9a-f]{8}\\.sock)\\)");

        String[] sockets = new String[3];
        Path[] folders = {one, sub, two};
        String[] names = {"one-worktree", "one-worktree", "two-worktree"};
        int failures = 0;
        for (int i = 0; i < folders.length; i++) {
            Run run = runBinary(binary, folders[i], environment, "status");
            var match = named.matcher(run.err());
            boolean found = run.exit() == 2 && match.find() && match.group(2).equals(names[i]);
            System.out.println(
                    "gabier status in " + folders[i].getFileName() + " -> " + run.err() + " (exit " + run.exit() + ")");
            sockets[i] = found ? match.group(1) : "";
            failures += found ? 0 : 1;
        }

        return failures
                + (!sockets[0].isEmpty() && sockets[0].equals(sockets[1]) && !sockets[0].equals(sockets[2]) ? 0 : 1);
    }
}
