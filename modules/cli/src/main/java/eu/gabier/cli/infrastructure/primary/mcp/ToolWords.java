package eu.gabier.cli.infrastructure.primary.mcp;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Words of the command line, said to a tool: a text written for a person at a terminal names a command as {@code gabier
 * <command>}, and what an agent calls is the tool of that name. A form of a command that is another tool ({@code gabier
 * rename --token}, {@code gabier diagnostics --all}) is that tool ({@code rename-token}, {@code diagnostics-all}), and
 * an option ({@code --dry-run}) is the boolean parameter of the same name set to true ({@code dryRun=true}); "then without {@code --dry-run}" says to call the tool again without
 * that parameter ({@code then call it again without `dryRun`}). Only a word that is a tool of the server is turned: the program named
 * in a sentence ({@code gabier has none}), or a command that is no tool ({@code gabier help}), stays as the text wrote
 * it, and so does an option given a value ({@code --for="a task"}).
 */
final class ToolWords {

    /** The program and the word after it, which may be the name of a command (lower case letters and hyphens). */
    private static final Pattern INVOCATION = Pattern.compile("\\bgabier ([a-z][a-z-]*)\\b");

    /** A command followed by the option that makes it another tool, which is the command and the word joined. */
    private static final Pattern FORM = Pattern.compile("\\bgabier ([a-z][a-z-]*) --(token|all)\\b");

    /**
     * What a text says after a dry run: "then without {@code --dry-run}". A tool has no flag to leave out; it is called
     * again with the parameter left out.
     */
    private static final Pattern THEN_WITHOUT = Pattern.compile("\\bthen without `--([a-z]+(?:-[a-z]+)*)`");

    /** An option with no value, not part of a longer word: the hyphens are those of the name of a flag. */
    private static final Pattern OPTION = Pattern.compile("(?<![\\w-])--([a-z]+(?:-[a-z]+)*)(?![\\w=-])");

    private ToolWords() {}

    /** The text with the commands, the forms and the options of the command line written as tools and parameters. */
    static String of(String text, Tools tools) {
        var forms = FORM.matcher(text).replaceAll(match -> "gabier " + match.group(1) + "-" + match.group(2));
        var invocations = INVOCATION
                .matcher(forms)
                .replaceAll(
                        match -> Matcher.quoteReplacement(tools.has(match.group(1)) ? match.group(1) : match.group()));

        var again = THEN_WITHOUT
                .matcher(invocations)
                .replaceAll(match -> "then call it again without `" + camelCase(match.group(1)) + "`");

        return OPTION.matcher(again).replaceAll(match -> camelCase(match.group(1)) + "=true");
    }

    private static String camelCase(String kebab) {
        var words = kebab.split("-");
        var camel = new StringBuilder(words[0]);
        for (var index = 1; index < words.length; index++) {
            camel.append(Character.toUpperCase(words[index].charAt(0))).append(words[index].substring(1));
        }

        return camel.toString();
    }
}
