package eu.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ImportChoicesTest {

    private final ImportChoices choices = new ImportChoices();

    @Test
    void offersNothingUntilJdtlsAsks() {
        assertThat(choices.offered()).isEmpty();
    }

    @Test
    void keepsWhatJdtlsOfferedInTheOrderItOfferedItEachTypeOnce() {
        choices.offer(List.of("java.sql.Date", "java.util.Date"));
        choices.offer(List.of("java.awt.List", "java.util.List", "java.util.Date"));

        assertThat(choices.offered())
                .containsExactly("java.sql.Date", "java.util.Date", "java.awt.List", "java.util.List");
    }

    @Test
    void forgetsWhatWasOfferedWhenAskedTo() {
        choices.offer(List.of("java.sql.Date", "java.util.Date"));

        choices.clear();

        assertThat(choices.offered()).isEmpty();
    }
}
