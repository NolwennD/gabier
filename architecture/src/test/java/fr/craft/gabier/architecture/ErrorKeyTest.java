package fr.craft.gabier.architecture;

import static org.assertj.core.api.Assertions.*;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import fr.craft.gabier.shared.error.domain.ErrorKey;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The {@code ErrorKeyTest} of seed4j, which lives next to {@code ErrorKey} there and finds its implementations with the
 * Reflections library. Here it is in this project, which sees the classes of every module (a key may be defined in any
 * context) and has ArchUnit, so no new library is needed.
 */
class ErrorKeyTest {

    private static final String BASE_PACKAGE = "fr.craft.gabier";

    private static final Set<Class<? extends ErrorKey>> KEYS = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS).importPackages(BASE_PACKAGE).stream()
                    .filter(javaClass -> javaClass.isAssignableTo(ErrorKey.class))
                    .filter(javaClass -> !javaClass.isEquivalentTo(ErrorKey.class))
                    .<Class<? extends ErrorKey>>map(
                            javaClass -> javaClass.reflect().asSubclass(ErrorKey.class))
                    .collect(Collectors.toSet());

    @Test
    void shouldFindTheKeys() {
        // Without them the two tests below would pass for nothing.
        assertThat(KEYS).isNotEmpty();
    }

    @Test
    void shouldHaveOnlyEnumsImplementations() {
        KEYS.forEach(error -> assertThat(error.isEnum() || error.isInterface())
                .as(() -> "Implementations of " + ErrorKey.class.getName() + " must be enums and " + error.getName()
                        + " wasn't")
                .isTrue());
    }

    @Test
    void shouldNotGenerateBlankStringKey() {
        KEYS.forEach(key -> Stream.of(key.getEnumConstants())
                .forEach(enumKey -> assertThat(enumKey.get())
                        .as(() -> key + "." + enumKey + " generates a blank key")
                        .isNotBlank()));
    }
}
