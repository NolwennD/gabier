package eu.gabier.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import eu.gabier.protocol.ProjectJdks.Binding;
import eu.gabier.shared.error.domain.MissingMandatoryValueException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProjectJdksTest {

    private static final ProjectJdks JDKS = new ProjectJdks(
            List.of(
                    new Binding("JavaSE-25", Path.of("/jdks/temurin-25"), "chosen in a file"),
                    new Binding("JavaSE-17", Path.of("/jdks/17"), "bound by jdtls")),
            List.of("ignored JavaSE-21 of a file: there is no JDK at /nowhere"));

    private static final String JDKS_JSON = """
        {"bindings":[\
        {"environment":"JavaSE-25","home":"/jdks/temurin-25","source":"chosen in a file"},\
        {"environment":"JavaSE-17","home":"/jdks/17","source":"bound by jdtls"}],\
        "warnings":["ignored JavaSE-21 of a file: there is no JDK at /nowhere"]}\
        """;

    private static Map<String, Object> binding(Object environment, Object home, Object source) {
        var binding = new HashMap<String, Object>();
        binding.put("environment", environment);
        binding.put("home", home);
        binding.put("source", source);

        return binding;
    }

    private static Map<String, Object> result() {
        var result = new HashMap<String, Object>();
        result.put(
                "bindings",
                List.of(
                        binding("JavaSE-25", "/jdks/temurin-25", "chosen in a file"),
                        binding("JavaSE-17", "/jdks/17", "bound by jdtls")));
        result.put("warnings", List.of("ignored JavaSE-21 of a file: there is no JDK at /nowhere"));

        return result;
    }

    @Test
    void saysNothingWhenNoProjectIsBoundToAJdk() {
        assertThat(ProjectJdks.NONE.isEmpty()).isTrue();
        assertThat(JDKS.isEmpty()).isFalse();
        assertThat(new ProjectJdks(List.of(), List.of("a warning")).isEmpty()).isFalse();
        assertThat(ProjectJdks.NONE.lines()).isEmpty();
    }

    @Test
    void isWrittenWithTheEnvironmentTheJdkAndWhoChoseIt() {
        assertThat(JDKS.toJson()).isEqualTo(JDKS_JSON);
        StrictJson.parse(JDKS.toJson());
    }

    @Test
    void isReadFromWhatItWrites() {
        var read = ((RpcResponse.Success) RpcResponse.parse(JsonRpc.success("1", JDKS.toJson()))).result();

        assertThat(ProjectJdks.of(read)).isEqualTo(JDKS);
    }

    @Test
    void isReadFromTheResult() {
        assertThat(ProjectJdks.of(result())).isEqualTo(JDKS);
    }

    @Test
    void writesOneLineForEachJdkAndOneForEachWarning() {
        assertThat(JDKS.lines())
                .containsExactly(
                        "JavaSE-25 /jdks/temurin-25 (chosen in a file)",
                        "JavaSE-17 /jdks/17 (bound by jdtls)",
                        "warning: ignored JavaSE-21 of a file: there is no JDK at /nowhere");
    }

    @Test
    void isRefusedWhenAMemberIsMissingOrOfAnotherShape() {
        var results = new ArrayList<Map<String, Object>>();
        for (String member : List.of("bindings", "warnings")) {
            var missing = result();
            missing.remove(member);
            results.add(missing);
        }
        var warningsOfAnotherShape = result();
        warningsOfAnotherShape.put("warnings", List.of(1));
        results.add(warningsOfAnotherShape);
        for (Object bindings : List.of(
                "JavaSE-25",
                List.of("JavaSE-25"),
                List.of(binding(25, "/jdk", "jdtls")),
                List.of(binding("JavaSE-25", 25, "jdtls")),
                List.of(binding("JavaSE-25", "a\0path", "jdtls")),
                List.of(binding("JavaSE-25", "/jdk", 25)))) {
            var wrong = result();
            wrong.put("bindings", bindings);
            results.add(wrong);
        }

        for (var wrong : results) {
            assertThatExceptionOfType(InvalidResponseException.class)
                    .as(wrong.toString())
                    .isThrownBy(() -> ProjectJdks.of(wrong));
        }
    }

    @Test
    void refusesABindingWithNoEnvironmentAndNoSource() {
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> new Binding(" ", Path.of("/jdk"), "bound by jdtls"));
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> new Binding("JavaSE-25", Path.of("/jdk"), " "));
    }
}
