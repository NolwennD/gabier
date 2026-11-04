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
                "refactor.extract.constant", "extract-constant",
                "refactor.inline", "inline");

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

    @Test
    void namesNoCommandForAKindGabierHasNoCommandFor() {
        for (var kind : new String[] {
            "", "quickfix", "quickassist", "refactor", "refactor.introduce.parameter", "refactor.change.signature"
        }) {
            assertThat(new OfferedAction(kind, "A title").command()).as(kind).isEmpty();
        }
    }
}
