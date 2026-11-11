package eu.gabier.cli.infrastructure.primary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import eu.gabier.shared.error.domain.GeneratorException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class ArgumentsTest {

    private static Stream<org.junit.jupiter.params.provider.Arguments> dryRunPlaces() {
        return Stream.of(
                arguments(List.of("organize-imports", "--dry-run", "A.java"), true),
                arguments(List.of("organize-imports", "A.java", "--dry-run"), true),
                arguments(List.of("organize-imports", "A.java"), false));
    }

    @ParameterizedTest
    @MethodSource("dryRunPlaces")
    void theDryRunOptionMayStandAnywhereAfterTheCommandAndIsNoOperand(List<String> words, boolean dryRun) {
        var arguments = Arguments.of(words);

        assertThat(arguments.dryRun()).isEqualTo(dryRun);
        assertThat(arguments.operands(1).operand(0)).isEqualTo("A.java");
    }

    @Test
    void anOptionWithAValueIsReadAnywhereAfterTheCommandTheFirstOneCounting() {
        var arguments = Arguments.of(List.of("start", "--wait", "--timeout=30", "--timeout=5"));

        assertThat(arguments.valueOf("--timeout=")).contains("30");
        assertThat(arguments.valueOf("--for=")).isEmpty();
        assertThat(Arguments.of(List.of("--timeout=3")).valueOf("--timeout=")).isEmpty();
    }

    @Test
    void theOptionsTakenOutOfTheOperandsAreThoseNamedAndTheValuedOnesWhateverTheValue() {
        var arguments = Arguments.of(List.of("start", "--wait", "--timeout=30", "--dry-run", "x"));

        assertThat(arguments.withoutOptions("--wait", "--timeout=").operands()).containsExactly("--dry-run", "x");
        assertThat(arguments.withoutOptions("--timeout").operands())
                .containsExactly("--wait", "--timeout=30", "--dry-run", "x");
    }

    @Test
    void theDryRunOptionMayStandInTheMiddle() {
        var arguments = Arguments.of(List.of("rename", "A.java", "--dry-run", "3", "4", "x"));

        assertThat(arguments.dryRun()).isTrue();
        assertThat(arguments.operands(4).operands()).containsExactly("A.java", "3", "4", "x");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2})
    void anOperandMissingOrInExcessIsAUsageErrorNamingTheArguments(int count) {
        var arguments = Arguments.of(List.of("organize-imports", "--dry-run", "A.java"));

        assertThatThrownBy(() -> arguments.operands(count))
                .isInstanceOf(UsageException.class)
                .satisfies(failure -> assertThat(((GeneratorException) failure).parameters())
                        .containsEntry("arguments", "organize-imports --dry-run A.java"));
    }

    @Test
    void theOptionsNamedByTheCommandAreNoOperandsEither() {
        var arguments = Arguments.of(List.of("rename", "--token", "A.java", "x", "--dry-run", "y"));

        assertThat(arguments.has("--token")).isTrue();
        assertThat(arguments.operands(3, "--token").operands()).containsExactly("A.java", "x", "y");
        assertThatThrownBy(() -> arguments.operands(3)).isInstanceOf(UsageException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void atLeastKeepsTheOperandsOnceTheDryRunIsOutWhenThereAreThatManyOrMore(int count) {
        var arguments = Arguments.of(List.of("move", "A.java", "--dry-run", "B.java", "demo.p"));

        assertThat(arguments.atLeast(count).operands()).containsExactly("A.java", "B.java", "demo.p");
    }

    @Test
    void fewerOperandsThanTheMinimumAreAUsageErrorNamingTheArguments() {
        var arguments = Arguments.of(List.of("move", "--dry-run", "A.java"));

        assertThat(arguments.atLeast(1).operands()).containsExactly("A.java");
        assertThatThrownBy(() -> arguments.atLeast(2))
                .isInstanceOf(UsageException.class)
                .satisfies(failure -> assertThat(((GeneratorException) failure).parameters())
                        .containsEntry("arguments", "move --dry-run A.java"));
    }

    @Test
    void atLeastOneKeepsEveryOperandAndRefusesNoneAndTheDryRunAsAUsageError() {
        assertThat(Arguments.of(List.of("diagnostics", "A.java", "B.java"))
                        .atLeastOne()
                        .operands())
                .containsExactly("A.java", "B.java");
        assertThatThrownBy(() -> Arguments.of(List.of("diagnostics")).atLeastOne())
                .isInstanceOf(UsageException.class);
        assertThatThrownBy(() -> Arguments.of(List.of("diagnostics", "--dry-run", "A.java"))
                        .atLeastOne())
                .isInstanceOf(UsageException.class)
                .satisfies(failure -> assertThat(((GeneratorException) failure).parameters())
                        .containsEntry("arguments", "diagnostics --dry-run A.java"));
    }

    @Test
    void withoutAnOptionTakesOnlyThatOptionOutOfTheOperands() {
        var arguments = Arguments.of(List.of("references", "--include-declaration", "A.java", "3:4"));

        assertThat(arguments.without("--include-declaration").operands()).containsExactly("A.java", "3:4");
        assertThat(arguments.has("--include-declaration")).isTrue();
        assertThat(Arguments.of(List.of("references", "--dry-run", "A.java", "3:4"))
                        .without("--include-declaration")
                        .operands())
                .containsExactly("--dry-run", "A.java", "3:4");
    }

    @Test
    void anOptionNotGivenIsNotThere() {
        assertThat(Arguments.of(List.of("rename", "A.java")).has("--token")).isFalse();
    }

    @Test
    void exactlyKeepsEveryWordAsAnOperand() {
        var arguments = Arguments.of(List.of("diagnostics", "--dry-run"));

        assertThat(arguments.exactly(1).operand(0)).isEqualTo("--dry-run");
        assertThatThrownBy(() -> arguments.exactly(0)).isInstanceOf(UsageException.class);
        assertThatThrownBy(() -> arguments.exactly(2)).isInstanceOf(UsageException.class);
    }

    @Test
    void aFileIsAnAbsolutePathNotCleaned() {
        var arguments = Arguments.of(List.of("diagnostics", "src/../A.java"));

        assertThat(arguments.file(0)).isEqualTo(Path.of("src/../A.java").toAbsolutePath());
    }

    @Test
    void aPositionIsCountedFromOneAndKeptFromOneForTheRunnerToSendFromZero() {
        var arguments = Arguments.of(List.of("extract-method", "A.java", "3:5", "1:2"));

        assertThat(arguments.position(1)).isEqualTo(new Arguments.Position(3, 5));
        assertThat(arguments.position(2)).isEqualTo(new Arguments.Position(1, 2));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0:1", "1:0", "-1:1", "1:-1", "x:1", "1:x", "1:2:3", "1", "1:", ":1", ""})
    void aPositionThatIsNotTwoNumbersFromOneIsAUsageError(String position) {
        var arguments = Arguments.of(List.of("extract-method", "A.java", position));

        assertThatThrownBy(() -> arguments.position(1))
                .isInstanceOf(UsageException.class)
                .satisfies(failure -> assertThat(((GeneratorException) failure).parameters())
                        .containsEntry("arguments", "extract-method A.java " + position)
                        .containsEntry("position", position));
    }
}
