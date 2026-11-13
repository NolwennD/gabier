package eu.gabier.cli.infrastructure.primary;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * The clean-up of a whole file and the action on one range are two ways to the same rewrite: the help of {@code cleanup}
 * says how to change one place only, so that a reader of it finds the way to {@code actions} and {@code apply-action}.
 */
class CleanupHelpTest {

    @Test
    void saysHowToChangeOnePlaceOnly() {
        assertThat(Cleanup.COMMAND.notes())
                .contains("applies to the whole file")
                .contains("for one place only")
                .contains("actions")
                .contains("apply-action")
                .contains("text block");
    }
}
