package eu.gabier.cli.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class OperationsTest {

    /** An option of the command line ({@code --wait}), or a command of it ({@code gabier organize-imports}). */
    private static final Pattern COMMAND_LINE_WORD = Pattern.compile("--[a-z]|gabier [a-z]");

    @Test
    void declaresEachMethodOfTheDaemonOnce() {
        assertThat(Operations.all().stream().map(Operation::method))
                .containsExactly(
                        "status",
                        "start",
                        "stop",
                        "diagnostics",
                        "diagnostics-all",
                        "rename",
                        "rename-token",
                        "organize-imports",
                        "cleanup",
                        "extract-variable",
                        "extract-method",
                        "extract-constant",
                        "extract-field",
                        "move-type-to-file",
                        "move",
                        "inline",
                        "create",
                        "references",
                        "actions",
                        "apply-action");
    }

    @Test
    void countsEveryPositionFromOne() {
        var positions = Operations.all().stream()
                .flatMap(operation -> operation.parameters().stream())
                .filter(parameter -> parameter.type() == Parameter.Type.POSITION)
                .toList();

        assertThat(positions)
                .isNotEmpty()
                .allSatisfy(parameter -> assertThat(parameter.description()).contains("counted from 1"));
    }

    @Test
    void declaresEveryLineAndEveryColumnAsAPosition() {
        var linesAndColumns = Operations.all().stream()
                .flatMap(operation -> operation.parameters().stream())
                .filter(parameter -> parameter.name().matches("(start|end)?(Line|Column)|line|column"))
                .toList();

        assertThat(linesAndColumns)
                .isNotEmpty()
                .allSatisfy(parameter -> assertThat(parameter.type()).isEqualTo(Parameter.Type.POSITION));
    }

    @Test
    void asksForTheParametersOfARangeInTheOrderOfTheDaemon() {
        assertThat(Operations.APPLY_ACTION.parameterNames())
                .isEqualTo(List.of("file", "startLine", "startColumn", "endLine", "endColumn", "title", "dryRun"));
    }

    @Test
    void requiresEveryParameter() {
        assertThat(Operations.all().stream().flatMap(operation -> operation.parameters().stream()))
                .allSatisfy(parameter -> assertThat(parameter.required()).isTrue());
    }

    @Test
    void tellsThatRenamingTheNameOfAModuleIsATextEdit() {
        assertThat(Operations.RENAME.notes().replaceAll("\\s+", " "))
                .contains("the name of a module in a module-info.java")
                .contains("edit the module line and the requires that name the module as text");
        assertThat(Operations.RENAME.refusals())
                .anyMatch(refusal -> refusal.startsWith("language-server-request-failed"));
    }

    @Test
    void speaksOfNoWordOfTheCommandLineWhereAnotherFrontEndReadsIt() {
        var described = List.of(Operations.START, Operations.RENAME, Operations.RENAME_TOKEN, Operations.CLEANUP);

        assertThat(described).allSatisfy(operation -> {
            assertThat(operation.notes()).doesNotContainPattern(COMMAND_LINE_WORD);
            assertThat(operation.refusals())
                    .allSatisfy(refusal -> assertThat(refusal).doesNotContainPattern(COMMAND_LINE_WORD));
        });
    }
}
