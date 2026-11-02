package eu.gabier.editing.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import eu.gabier.shared.error.domain.ErrorStatus;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TypeNameTest {

    @Test
    void isTheNameOfTheFileWithoutItsExtension() {
        assertThat(TypeName.of(Path.of("src/main/java/demo/Greeter.java")).value())
                .isEqualTo("Greeter");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Greeter.java", "greeter.java", "_Greeter.java", "$Greeter.java", "Café.java", "A1.java"})
    void acceptsTheNameOfAJavaIdentifier(String fileName) {
        assertThat(TypeName.of(Path.of("src", fileName)).value()).isEqualTo(fileName.replace(".java", ""));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "Greeter.txt",
                "Greeter",
                "Greeter.JAVA",
                ".java",
                "1Greeter.java",
                "Greeter-Impl.java",
                "Greeter Impl.java",
                "module-info.java",
                "package-info.java",
                "class.java",
                "int.java",
                "null.java",
                "true.java",
                "_.java",
                "var.java",
                "record.java",
                "yield.java",
                "sealed.java",
                "permits.java"
            })
    void refusesAFileThatCannotNameAType(String fileName) {
        Path file = Path.of("src/main/java/demo", fileName);

        assertThatExceptionOfType(EditingException.class)
                .isThrownBy(() -> TypeName.of(file))
                .satisfies(exception -> {
                    assertThat(exception.key()).isEqualTo(EditingErrorKey.NOT_A_TYPE_NAME);
                    assertThat(exception.status()).isEqualTo(ErrorStatus.BAD_REQUEST);
                    assertThat(exception.parameters()).isEqualTo(Map.of("file", file.toString()));
                });
    }
}
