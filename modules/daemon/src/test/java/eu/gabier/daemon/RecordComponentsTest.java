package eu.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.Test;

/** Reads the semantic tokens as jdtls gives them: five numbers by token, the line and the column relative to the last. */
class RecordComponentsTest {

    private static final int RECORD_COMPONENT = 16;

    /**
     * The tokens jdtls 1.63 answers for {@code package demo;}, a blank line and {@code public record Plain(String file,
     * int line) {} }: the package name, {@code public}, {@code record}, {@code Plain} (the type {@code record}),
     * {@code String} (a class), then {@code file} and {@code line}, both of the type {@code recordComponent}.
     */
    private static final List<Integer> TOKENS = List.of(
            0, 8, 4, 0, 0, 2, 0, 6, 11, 0, 0, 7, 6, 11, 0, 0, 7, 5, 15, 84, 0, 6, 6, 1, 68, 0, 7, 4, 16, 16, 0, 10, 4,
            16, 16);

    @Test
    void tellsTheNameOfAComponentFromItsPlaceInTheHeader() {
        assertThat(RecordComponents.areDeclaredAt(TOKENS, RECORD_COMPONENT, new Position(2, 27)))
                .isTrue();
        assertThat(RecordComponents.areDeclaredAt(TOKENS, RECORD_COMPONENT, new Position(2, 37)))
                .isTrue();
    }

    @Test
    void doesNotTakeAnotherTokenForAComponent() {
        assertThat(RecordComponents.areDeclaredAt(TOKENS, RECORD_COMPONENT, new Position(2, 14)))
                .isFalse();
        assertThat(RecordComponents.areDeclaredAt(TOKENS, RECORD_COMPONENT, new Position(2, 20)))
                .isFalse();
    }

    @Test
    void wantsTheTokenToStartAtThePlace() {
        assertThat(RecordComponents.areDeclaredAt(TOKENS, RECORD_COMPONENT, new Position(2, 28)))
                .isFalse();
        assertThat(RecordComponents.areDeclaredAt(TOKENS, RECORD_COMPONENT, new Position(3, 27)))
                .isFalse();
    }

    @Test
    void findsNothingInAnEmptyListOfTokens() {
        assertThat(RecordComponents.areDeclaredAt(List.of(), RECORD_COMPONENT, new Position(2, 27)))
                .isFalse();
    }
}
