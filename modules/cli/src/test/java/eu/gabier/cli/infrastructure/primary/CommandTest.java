package eu.gabier.cli.infrastructure.primary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.gabier.cli.domain.Operation;
import eu.gabier.cli.domain.Operations;
import eu.gabier.shared.error.domain.MissingMandatoryValueException;
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
                .containsExactly(Refusal.NOT_RUNNING, Refusal.USAGE, Refusal.CONNECTION_FAILED, Refusal.STOPPING);
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
    void aCommandThatServesTheOperationsToAnotherFrontEndEndsOnlyWithAUsageErrorWhateverItsCallsMeet() {
        var command = Command.called("serve")
                .usage("gabier [--json] serve")
                .example(EXAMPLE)
                .servesTheOperations("serve them")
                .withNotes("a call that fails is a result");

        assertThat(command.summary()).isEqualTo("serve them");
        assertThat(command.operations()).isEmpty();
        assertThat(command.refusals()).containsExactly(Refusal.USAGE);
        assertThat(command.notes()).isEqualTo("a call that fails is a result");
    }

    @Test
    void aCommandOfTheCliAloneHasNoOperation() {
        assertThat(Help.COMMAND.operations()).isEmpty();
        assertThat(InitAgent.COMMAND.operations()).isEmpty();
    }

    @Test
    void putsTheNotesOfTheCommandLineAfterThoseOfItsOperations() {
        var command = Command.called("cut")
                .usage("gabier [--json] cut <file>")
                .example(EXAMPLE)
                .asksTheDaemon(operation("cut", "first"), operation("cut-b", ""), operation("cut-c", "third"))
                .withNotes("what a person types");

        assertThat(command.notes()).isEqualTo("first\nthird\nwhat a person types");
        assertThat(command.summary()).isEqualTo("cut cut");
    }

    @Test
    void givesTheNotesOfTheCommandLineAloneWhenItsOperationsHaveNone() {
        var command = Command.called("cut")
                .usage("gabier [--json] cut <file>")
                .example(EXAMPLE)
                .asksTheDaemon(operation("cut", ""))
                .withNotes("what a person types");

        assertThat(command.notes()).isEqualTo("what a person types");
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
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("The command stop has operations: they say what it does");
    }

    @Test
    void refusesACommandWithNoOperationAndNoSummary() {
        assertThatThrownBy(() -> Command.called("cut")
                        .usage("gabier [--json] cut <file>")
                        .example(EXAMPLE)
                        .standalone(" ", Refusal.USAGE))
                .isExactlyInstanceOf(MissingMandatoryValueException.class)
                .hasMessage("The field \"ownSummary\" is mandatory and wasn't set (blank)");
    }
}
