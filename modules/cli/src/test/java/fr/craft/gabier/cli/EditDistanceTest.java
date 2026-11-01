package fr.craft.gabier.cli;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class EditDistanceTest {

    @ParameterizedTest(name = "{0} to {1} is {2}")
    @CsvSource({
        "rename, rename, 0",
        "rename, renane, 1",
        "rename, renamee, 1",
        "rename, renam, 1",
        "rename, ename, 1",
        "rename, reanme, 1",
        "rename, rnemae, 2",
        "rename, stop, 6",
        "rename, '', 6",
        "'', rename, 6",
        "ab, ba, 1",
        "ca, abc, 3"
    })
    void aLetterChangedAddedRemovedOrTwoSwappedCountsOne(String from, String to, int distance) {
        assertThat(EditDistance.between(from, to)).isEqualTo(distance);
    }
}
