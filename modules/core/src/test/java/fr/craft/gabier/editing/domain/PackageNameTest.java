package fr.craft.gabier.editing.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import fr.craft.gabier.shared.error.domain.ErrorStatus;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PackageNameTest {

    @Test
    void isWrittenWithDotsAndIsAFolderPathWithSlashes() {
        PackageName name = PackageName.of("demo.billing.invoices");

        assertThat(name.value()).isEqualTo("demo.billing.invoices");
        assertThat(name.folder()).isEqualTo(Path.of("demo", "billing", "invoices"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "demo",
                "demo.billing",
                "Demo",
                "_demo.$x",
                "café.été",
                "a1.b2",
                "com.example.record",
                "var",
                "a.sealed.yield.permits"
            })
    void acceptsNamesOfJavaIdentifiersSeparatedByDots(String text) {
        assertThat(PackageName.of(text).value()).isEqualTo(text);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                " ",
                ".",
                ".demo",
                "demo.",
                "demo..billing",
                "demo/billing",
                "demo billing",
                "demo.bill-ing",
                "1demo",
                "demo.2billing",
                "demo.class",
                "int.demo",
                "demo.null",
                "demo.true",
                "_",
                "demo.\n",
                "demo.\u001b[31m"
            })
    void refusesWhatIsNotAPackageName(String text) {
        assertThatExceptionOfType(EditingException.class)
                .isThrownBy(() -> PackageName.of(text))
                .satisfies(exception -> {
                    assertThat(exception.key()).isEqualTo(EditingErrorKey.NOT_A_PACKAGE_NAME);
                    assertThat(exception.status()).isEqualTo(ErrorStatus.BAD_REQUEST);
                    assertThat(exception.parameters()).isEqualTo(Map.of("package", text));
                });
    }
}
