package eu.gabier.cli.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.gabier.shared.error.domain.MissingMandatoryValueException;
import eu.gabier.shared.error.domain.StringWithWhitespacesException;
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
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("The parameter file is declared twice");
    }

    @Test
    void refusesAMethodThatIsNotOneWord() {
        assertThatThrownBy(() -> new Operation("move it", "move it", "", List.of(), List.of()))
                .isExactlyInstanceOf(StringWithWhitespacesException.class)
                .hasMessage("The field \"method\" contains at least one space");
    }

    @Test
    void refusesABlankRefusal() {
        assertThatThrownBy(() -> new Operation("move", "move it", "", List.of(" "), List.of()))
                .isExactlyInstanceOf(MissingMandatoryValueException.class)
                .hasMessage("The field \"refusal\" is mandatory and wasn't set (blank)");
    }

    @Test
    void refusesAParameterWithNoDescription() {
        assertThatThrownBy(() -> Parameter.string("file", " "))
                .isExactlyInstanceOf(MissingMandatoryValueException.class)
                .hasMessage("The field \"description\" is mandatory and wasn't set (blank)");
    }

    @Test
    void refusesAParameterWhoseNameIsNotOneWord() {
        assertThatThrownBy(() -> Parameter.string("new name", "the name"))
                .isExactlyInstanceOf(StringWithWhitespacesException.class)
                .hasMessage("The field \"name\" contains at least one space");
    }

    @Test
    void refusesABlankSummary() {
        assertThatThrownBy(() -> new Operation("move", " ", "", List.of(), List.of()))
                .isExactlyInstanceOf(MissingMandatoryValueException.class)
                .hasMessage("The field \"summary\" is mandatory and wasn't set (blank)");
    }

    @Test
    void keepsTheKindOfEachParameter() {
        assertThat(Stream.of(
                                Parameter.string("a", "text"),
                                Parameter.integer("b", "number"),
                                Parameter.position("c", "line"),
                                Parameter.bool("d", "flag"),
                                Parameter.array("e", "list"),
                                Parameter.path("f", "file"),
                                Parameter.paths("g", "files"))
                        .map(Parameter::type))
                .containsExactly(
                        Parameter.Type.STRING,
                        Parameter.Type.INTEGER,
                        Parameter.Type.POSITION,
                        Parameter.Type.BOOLEAN,
                        Parameter.Type.ARRAY,
                        Parameter.Type.PATH,
                        Parameter.Type.PATHS);
    }
}
