package eu.gabier.editing.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import eu.gabier.shared.error.domain.ErrorStatus;
import eu.gabier.shared.error.domain.MissingMandatoryValueException;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PackageInfoFileTest {

    @Test
    void isAFileNamedPackageInfoWhateverItsFolder() {
        Path file = Path.of("src/main/java/demo/package-info.java");

        assertThat(PackageInfoFile.of(file).path()).isEqualTo(file);
        assertThat(PackageInfoFile.of(Path.of("package-info.java")).path()).isEqualTo(Path.of("package-info.java"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "Widget.java",
                "package-info",
                "package-info.txt",
                "Package-info.java",
                "package_info.java",
                "module-info.java",
                "mypackage-info.java"
            })
    void refusesAFileThatIsNotNamedPackageInfo(String fileName) {
        Path file = Path.of("src/main/java/demo", fileName);

        assertThatExceptionOfType(EditingException.class)
                .isThrownBy(() -> PackageInfoFile.of(file))
                .satisfies(exception -> {
                    assertThat(exception.key()).isEqualTo(EditingErrorKey.NOT_A_PACKAGE_INFO);
                    assertThat(exception.status()).isEqualTo(ErrorStatus.BAD_REQUEST);
                    assertThat(exception.parameters()).isEqualTo(Map.of("file", file.toString()));
                    assertThat(exception.getMessage())
                            .isEqualTo("The file " + file
                                    + " cannot be the description of a package: it must be named package-info.java");
                });
    }

    @Test
    void requiresAFile() {
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> PackageInfoFile.of(null))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("file"));
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> new PackageInfoFile(null))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("path"));
    }
}
