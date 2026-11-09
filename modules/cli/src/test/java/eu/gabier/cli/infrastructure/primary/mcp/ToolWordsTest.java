package eu.gabier.cli.infrastructure.primary.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** A text written for the command line names a command as {@code gabier <command>}; to a tool it names the tool. */
class ToolWordsTest {

    private final Tools tools = Tools.served();

    @Test
    void turnsAnInvocationOfACommandIntoTheNameOfTheToolOfTheSameName() {
        assertThat(ToolWords.of(
                        "Ask: `gabier actions <file> <line>:<column>`, then `gabier apply-action --dry-run <file>`",
                        tools))
                .isEqualTo("Ask: `actions <file> <line>:<column>`, then `apply-action dryRun=true <file>`");
    }

    @Test
    void turnsAnOptionIntoTheBooleanParameterOfTheSameNameSetToTrue() {
        assertThat(ToolWords.of(
                        "`gabier references [--include-declaration] <file>`; then without `--dry-run`, and check",
                        tools))
                .isEqualTo("`references [includeDeclaration=true] <file>`; then without `dryRun=true`, and check");
    }

    @Test
    void turnsAFormOfACommandThatIsAnotherToolIntoThatTool() {
        assertThat(ToolWords.of(
                        "or `gabier rename --token <file> <identifier> <new-name>`; then `gabier rename --dry-run"
                                + " <file>`",
                        tools))
                .isEqualTo("or `rename-token <file> <identifier> <new-name>`; then `rename dryRun=true <file>`");
    }

    @Test
    void leavesAnOptionAloneThatIsGivenAValueOrIsPartOfAWord() {
        var text = "gabier help --for=\"a task\", a--b, x--y-z";

        assertThat(ToolWords.of(text, tools)).isEqualTo(text);
    }

    @Test
    void turnsAMentionThatIsNotBetweenBackticksToo() {
        assertThat(ToolWords.of("Run gabier rename-token, or gabier cleanup.", tools))
                .isEqualTo("Run rename-token, or cleanup.");
    }

    @Test
    void leavesAloneWhatIsNoToolOfTheServer() {
        var text = "See `gabier help guide`; gabier has no command for it, gabier start first, gabier renamed it.";

        assertThat(ToolWords.of(text, tools)).isEqualTo(text);
    }

    @Test
    void leavesATextThatNamesNoCommandAsItIs() {
        assertThat(ToolWords.of("By hand: invert the condition.", tools)).isEqualTo("By hand: invert the condition.");
    }
}
