package eu.gabier.editing.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class OfferedActionTest {

    @Test
    void namesTheCommandOfGabierThatDoesWhatTheKindOfTheActionSays() {
        var commands = Map.of(
                "refactor.extract.variable", "extract-variable",
                "refactor.extract.field", "extract-field",
                "refactor.extract.function", "extract-method",
                "refactor.extract.constant", "extract-constant");

        commands.forEach((kind, command) -> assertThat(new OfferedAction(kind, "Whatever the server says").command())
                .as(kind)
                .contains(command));
    }

    @Test
    void readsTheKindAndNotTheTitle() {
        assertThat(new OfferedAction("refactor.extract.variable", "Extract to local variable (replace all occurrences)")
                        .command())
                .contains("extract-variable");
        assertThat(new OfferedAction("quickassist", "Extract to local variable").command())
                .isEmpty();
    }

    /**
     * jdtls gives the kind {@code refactor.inline} to "Make Static" as well, which {@code gabier inline} does not do:
     * it replaces a variable, a method or a constant by its value. The three that it does are titled "Inline ...".
     */
    @Test
    void linksTheKindInlineOnlyToTheActionsThatInlineSomething() {
        assertThat(new OfferedAction("refactor.inline", "Inline Method").command())
                .contains("inline");
        assertThat(new OfferedAction("refactor.inline", "Inline Constant").command())
                .contains("inline");
        assertThat(new OfferedAction("refactor.inline", "Inline local variable").command())
                .contains("inline");
        assertThat(new OfferedAction("refactor.inline", "Make Static").command())
                .isEmpty();
        assertThat(new OfferedAction("refactor.inline", "Inlined").command()).isEmpty();
        assertThat(new OfferedAction("quickfix", "Inline Method").command()).isEmpty();
    }

    /**
     * Both extractions to a variable have the kind {@code refactor.extract.variable} and are linked to {@code
     * extract-variable}, which replaces the selected expression only: the command does not do what "(replace all
     * occurrences)" says, and its help says so (the link stays, because the command is the way to give a name).
     */
    @Test
    void linksBothExtractionsToAVariableToTheCommandThatReplacesOneExpression() {
        assertThat(new OfferedAction("refactor.extract.variable", "Extract to local variable").command())
                .contains("extract-variable");
        assertThat(new OfferedAction("refactor.extract.variable", "Extract to local variable (replace all occurrences)")
                        .command())
                .contains("extract-variable");
    }

    @Test
    void namesNoCommandForAKindGabierHasNoCommandFor() {
        for (var kind : new String[] {
            "", "quickfix", "quickassist", "refactor", "refactor.introduce.parameter", "refactor.change.signature"
        }) {
            assertThat(new OfferedAction(kind, "A title").command()).as(kind).isEmpty();
        }
    }
}
