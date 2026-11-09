package eu.gabier.cli.infrastructure.primary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.gabier.cli.domain.Operation;
import eu.gabier.cli.domain.Operations;
import java.util.List;
import org.junit.jupiter.api.Test;

class CommandTest {

    private static final String EXAMPLE = "$ gabier cut a";

    private static Operation operation(String method, String notes, String... refusals) {
        return new Operation(method, "cut " + method, notes, List.of(refusals), List.of());
    }

    @Test
    void takesItsSummaryFromItsFirstOperation() {
        var command = Command.called("cut")
                .usage("gabier [--json] cut <file>")
                .example(EXAMPLE)
                .asksTheDaemon(operation("cut", ""), operation("cut-token", ""));

        assertThat(command.summary()).isEqualTo("cut cut");
    }

    @Test
    void joinsTheNotesOfItsOperationsAndSkipsTheEmptyOnes() {
        var command = Command.called("cut")
                .usage("gabier [--json] cut <file>")
                .example(EXAMPLE)
                .asksTheDaemon(operation("cut", "first"), operation("cut-b", ""), operation("cut-c", "third"));

        assertThat(command.notes()).isEqualTo("first\nthird");
    }

    @Test
    void putsTheRefusalsOfALaterOperationBeforeTheFirstOnesTheyShareWithAnEarlierOne() {
        var command = Command.called("cut")
                .usage("gabier [--json] cut <file>")
                .example(EXAMPLE)
                .asksTheDaemon(
                        operation("cut", "", "a: a", "b: b", "shared: s", "tail: t"),
                        operation("cut-token", "", "x: x", "y: y", "shared: s", "z: z"));

        assertThat(command.refusals())
                .containsExactly(
                        "a: a",
                        "b: b",
                        "x: x",
                        "y: y",
                        "shared: s",
                        "tail: t",
                        "z: z",
                        Refusal.USAGE,
                        Refusal.START_FAILED,
                        Refusal.CONNECTION_FAILED);
    }

    @Test
    void keepsTheRefusalsOfRenameInTheOrderOfItsHelp() {
        var keys = Rename.COMMAND.refusals().stream()
                .map(refusal -> refusal.substring(0, refusal.indexOf(':')))
                .toList();

        assertThat(keys)
                .containsExactly(
                        "language-server-no-such-symbol",
                        "language-server-invalid-name",
                        "language-server-empty-package",
                        "editing-token-not-found",
                        "editing-file-missing",
                        "language-server-file-outside-project",
                        "language-server-file-missing",
                        "language-server-not-a-file",
                        "editing-stale-edit",
                        "language-server-not-ready",
                        "usage-error",
                        "daemon-start-failed",
                        "daemon-connection-failed");
    }

    @Test
    void ofACommandThatAsksARunningDaemonEndsWithItsOwnRefusals() {
        assertThat(Stop.COMMAND.refusals())
                .containsExactly(Refusal.NOT_RUNNING, Refusal.USAGE, Refusal.CONNECTION_FAILED);
    }

    @Test
    void aCommandOfTheCliAloneHoldsItsOwnSummaryNotesAndRefusals() {
        var command = Command.called("cut")
                .usage("gabier [--json] cut <file>")
                .example(EXAMPLE)
                .standalone("cut it", Refusal.USAGE)
                .withNotes("careful");

        assertThat(command.summary()).isEqualTo("cut it");
        assertThat(command.notes()).isEqualTo("careful");
        assertThat(command.refusals()).containsExactly(Refusal.USAGE);
    }

    @Test
    void aCommandOfTheCliAloneHasNoOperation() {
        assertThat(Help.COMMAND.operations()).isEmpty();
        assertThat(InitAgent.COMMAND.operations()).isEmpty();
    }

    @Test
    void refusesNotesOnACommandThatHasOperations() {
        assertThatThrownBy(() -> Stop.COMMAND.withNotes("what stop says"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("stop");
    }

    @Test
    void refusesACommandThatHasOperationsAndASummaryOfItsOwn() {
        assertThatThrownBy(() -> new Command(
                        "stop",
                        "gabier [--json] stop",
                        EXAMPLE,
                        List.of(Operations.STOP),
                        "its own",
                        "",
                        List.of(Refusal.USAGE)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("operations");
    }

    @Test
    void refusesACommandThatHasOperationsAndNotesOfItsOwn() {
        assertThatThrownBy(() -> new Command(
                        "stop",
                        "gabier [--json] stop",
                        EXAMPLE,
                        List.of(Operations.STOP),
                        "",
                        "its own notes",
                        List.of(Refusal.USAGE)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("operations");
    }

    @Test
    void refusesACommandWithNoOperationAndNoSummary() {
        assertThatThrownBy(() -> Command.called("cut")
                        .usage("gabier [--json] cut <file>")
                        .example(EXAMPLE)
                        .standalone(" ", Refusal.USAGE))
                .isInstanceOf(RuntimeException.class);
    }
}
