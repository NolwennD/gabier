package eu.gabier.cli.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class OperationTest {

    private static final Parameter FILE = Parameter.string("file", "the file");

    @Test
    void namesItsParametersInTheOrderOfACall() {
        var operation = new Operation("move", "move it", "", List.of(), List.of(FILE, Parameter.bool("dryRun", "no")));

        assertThat(operation.parameterNames()).containsExactly("file", "dryRun");
    }

    @Test
    void refusesTwoParametersWithTheSameName() {
        assertThatThrownBy(() -> new Operation("move", "move it", "", List.of(), List.of(FILE, FILE)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("file");
    }

    @Test
    void refusesAMethodThatIsNotOneWord() {
        assertThatThrownBy(() -> new Operation("move it", "move it", "", List.of(), List.of()))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void refusesABlankRefusal() {
        assertThatThrownBy(() -> new Operation("move", "move it", "", List.of(" "), List.of()))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void refusesAParameterWithNoDescription() {
        assertThatThrownBy(() -> Parameter.string("file", " ")).isInstanceOf(RuntimeException.class);
    }

    @Test
    void keepsTheKindOfEachParameter() {
        assertThat(Stream.of(
                                Parameter.string("a", "text"),
                                Parameter.integer("b", "number"),
                                Parameter.bool("c", "flag"),
                                Parameter.array("d", "list"))
                        .map(Parameter::type))
                .containsExactly(
                        Parameter.Type.STRING, Parameter.Type.INTEGER, Parameter.Type.BOOLEAN, Parameter.Type.ARRAY);
    }
}
