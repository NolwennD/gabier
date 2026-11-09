package eu.gabier.cli.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class OperationsTest {

    @Test
    void declaresEachMethodOfTheDaemonOnce() {
        assertThat(Operations.all().stream().map(Operation::method))
                .containsExactly(
                        "status",
                        "start",
                        "stop",
                        "diagnostics",
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
                .filter(parameter -> parameter.type() == Parameter.Type.INTEGER)
                .toList();

        assertThat(positions)
                .isNotEmpty()
                .allSatisfy(parameter -> assertThat(parameter.description()).contains("counted from 1"));
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
}
