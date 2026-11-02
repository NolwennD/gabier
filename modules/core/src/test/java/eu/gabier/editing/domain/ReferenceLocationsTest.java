package eu.gabier.editing.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import eu.gabier.shared.error.domain.MissingMandatoryValueException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReferenceLocationsTest {

    private static final Location MAIN_LATE = new Location(Path.of("src/Main.java"), new Position(9, 4));
    private static final Location MAIN_EARLY = new Location(Path.of("src/Main.java"), new Position(2, 30));
    private static final Location MAIN_SAME_LINE = new Location(Path.of("src/Main.java"), new Position(2, 40));
    private static final Location GREETER = new Location(Path.of("src/Greeter.java"), new Position(7, 1));

    @Test
    void sortsTheReferencesByFileThenByPositionWhateverTheOrderTheServerGaveThem() {
        var locations = new ReferenceLocations(List.of(), List.of(MAIN_LATE, MAIN_SAME_LINE, GREETER, MAIN_EARLY));

        assertThat(locations.references()).containsExactly(GREETER, MAIN_EARLY, MAIN_SAME_LINE, MAIN_LATE);
    }

    @Test
    void sortsTheDeclarationsLikeTheReferences() {
        var locations = new ReferenceLocations(List.of(MAIN_LATE, GREETER), List.of());

        assertThat(locations.declarations()).containsExactly(GREETER, MAIN_LATE);
    }

    @Test
    void holdsNothingWhenNothingIsFound() {
        var locations = new ReferenceLocations(List.of(), List.of());

        assertThat(locations.declarations()).isEmpty();
        assertThat(locations.references()).isEmpty();
    }

    @Test
    void doesNotChangeWhenTheListItWasGivenDoes() {
        var given = new ArrayList<>(List.of(GREETER));
        var locations = new ReferenceLocations(List.of(), given);
        given.add(MAIN_EARLY);

        assertThat(locations.references()).containsExactly(GREETER);
    }

    @Test
    void rejectsAMissingList() {
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> new ReferenceLocations(null, List.of()))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("declarations"));
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> new ReferenceLocations(List.of(), null))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("references"));
    }

    @Test
    void aLocationRejectsAMissingFileOrPosition() {
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> new Location(null, new Position(0, 0)))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("file"));
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> new Location(Path.of("A.java"), null))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("position"));
    }
}
