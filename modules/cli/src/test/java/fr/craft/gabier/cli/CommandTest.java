package fr.craft.gabier.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class CommandTest {

    @Test
    void everyCommandHasAHelpText() {
        assertThat(Command.values()).allSatisfy(command -> {
            assertThat(command.commandName()).as("name").isNotBlank();
            assertThat(command.summary()).as(command.commandName() + " summary").isNotBlank();
            assertThat(command.usage()).as(command.commandName() + " usage").isNotBlank();
            assertThat(command.example()).as(command.commandName() + " example").isNotBlank();
            assertThat(command.refusals())
                    .as(command.commandName() + " refusals")
                    .isNotEmpty();
            assertThat(command.refusals())
                    .as(command.commandName() + " refusals")
                    .noneMatch(String::isBlank);
        });
    }

    @Test
    void theUsageOfACommandNamesIt() {
        assertThat(Command.values())
                .allSatisfy(
                        command -> assertThat(command.usage()).contains("gabier [--json] " + command.commandName()));
    }

    @Test
    void theExampleOfACommandRunsIt() {
        assertThat(Command.values())
                .allSatisfy(command -> assertThat(command.example()).startsWith("$ gabier "));
    }

    @Test
    void theNamesAreDistinct() {
        assertThat(Arrays.stream(Command.values()).map(Command::commandName)).doesNotHaveDuplicates();
    }

    @Test
    void aCommandIsFoundByItsName() {
        assertThat(Command.named("rename")).contains(Command.RENAME);
        assertThat(Command.named("organize-imports")).contains(Command.ORGANIZE_IMPORTS);
    }

    @Test
    void anUnknownNameIsNoCommand() {
        assertThat(Command.named("--version")).isEmpty();
        assertThat(Command.named("")).isEmpty();
    }
}
