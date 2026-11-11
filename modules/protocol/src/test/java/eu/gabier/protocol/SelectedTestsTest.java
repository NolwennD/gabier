package eu.gabier.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import eu.gabier.shared.error.domain.MissingMandatoryValueException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SelectedTestsTest {

    private static SelectedTests readBack(SelectedTests selected) {
        var response = (RpcResponse.Success) RpcResponse.parse(JsonRpc.success("1", selected.toJson()));

        return SelectedTests.of(response.result());
    }

    @Test
    void isWrittenAsTheListOfTheNamesInTheOrderGiven() {
        var json = new SelectedTests(List.of("app.GreeterTest", "app.Outer$InnerTest", "b.\"Odd\"\nTest")).toJson();

        assertThat(json).isEqualTo("""
            {"tests":["app.GreeterTest","app.Outer$InnerTest","b.\\"Odd\\"\\nTest"]}\
            """);
        StrictJson.parse(json);
    }

    @Test
    void isAnEmptyListWhenNoTestIsAffected() {
        assertThat(new SelectedTests(List.of()).toJson()).isEqualTo("{\"tests\":[]}");
    }

    @Test
    void isReadBackAsItWasWritten() {
        var selected = new SelectedTests(List.of("app.AnotherTest", "app.GreeterTest"));

        assertThat(readBack(selected)).isEqualTo(selected);
        assertThat(readBack(new SelectedTests(List.of()))).isEqualTo(new SelectedTests(List.of()));
    }

    @Test
    void isRefusedWhenTheListIsMissingOrNotAList() {
        for (var invalid : List.of(Map.<String, Object>of(), Map.<String, Object>of("tests", "app.GreeterTest"))) {
            assertThatExceptionOfType(InvalidResponseException.class).isThrownBy(() -> SelectedTests.of(invalid));
        }
    }

    @Test
    void isRefusedWhenATestIsNotAName() {
        assertThatExceptionOfType(InvalidResponseException.class)
                .isThrownBy(() -> SelectedTests.of(Map.of("tests", List.of("app.GreeterTest", 7))));
    }

    @Test
    void isRefusedWithoutItsTests() {
        assertThatExceptionOfType(MissingMandatoryValueException.class).isThrownBy(() -> new SelectedTests(null));
    }
}
