package eu.gabier.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import eu.gabier.cli.domain.Operation;
import eu.gabier.cli.domain.Operations;
import eu.gabier.shared.error.domain.ErrorKey;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The help is the documentation of a command ({@code .claude/rules/help.md}), so the refusals it lists are checked
 * against the code, not against a page: each key is the key of a constant of an {@link ErrorKey} of the project.
 */
class RefusalKeysTest {

    private static final Set<String> KEYS_OF_THE_CODE = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS).importPackages("eu.gabier").stream()
                    .filter(javaClass -> javaClass.isEnum() && javaClass.isAssignableTo(ErrorKey.class))
                    .flatMap(javaClass -> Stream.of(javaClass.reflect().getEnumConstants()))
                    .map(constant -> ((ErrorKey) constant).get())
                    .collect(Collectors.toSet());

    @Test
    void shouldFindTheKeysOfTheCode() {
        // Without them the test below would pass for nothing.
        assertThat(KEYS_OF_THE_CODE).contains("language-server-not-movable-to-package");
    }

    @Test
    void shouldListInTheHelpOnlyKeysTheCodeDefines() {
        Operations.all()
                .forEach(operation -> assertThat(keysInTheHelpOf(operation))
                        .as("the refusals of %s", operation.method())
                        .isSubsetOf(KEYS_OF_THE_CODE));
    }

    private static Set<String> keysInTheHelpOf(Operation operation) {
        return operation.refusals().stream()
                .map(refusal -> refusal.substring(0, refusal.indexOf(':')))
                .collect(Collectors.toSet());
    }
}
