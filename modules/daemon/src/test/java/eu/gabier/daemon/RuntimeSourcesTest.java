package eu.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;

import eu.gabier.daemon.JdtlsRuntimes.Jdk;
import eu.gabier.daemon.RuntimeSources.Choice;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The order of precedence of the ways of choosing a JDK, with sources of the test: no file, no variable, no jdtls. */
class RuntimeSourcesTest {

    private static final Path ROOT = Path.of("/work/project");
    private static final Jdk FIRST_25 = new Jdk("JavaSE-25", Path.of("/first/25"), "25.0.2");
    private static final Jdk SECOND_25 = new Jdk("JavaSE-25", Path.of("/second/25"), "25.0.4");
    private static final Jdk SECOND_17 = new Jdk("JavaSE-17", Path.of("/second/17"), "17.0.10");

    private static RuntimeSource source(String description, List<Jdk> jdks, String... warnings) {
        return new RuntimeSource() {
            @Override
            public String description() {
                return description;
            }

            @Override
            public Found read(Path root) {
                assertThat(root).isEqualTo(ROOT);

                return new Found(jdks, List.of(warnings));
            }
        };
    }

    @Test
    void chooseNothingWhenThereIsNoSource() {
        var chosen = new RuntimeSources(List.of()).read(ROOT);

        assertThat(chosen.choices()).isEmpty();
        assertThat(chosen.warnings()).isEmpty();
        assertThat(chosen.jdks()).isEmpty();
    }

    @Test
    void theFirstSourceThatChoosesAnEnvironmentWinsAndTheNextIsNotHeardOfForIt() {
        var first = source("chosen by the first", List.of(FIRST_25));
        var second = source("chosen by the second", List.of(SECOND_25, SECOND_17));

        var chosen = new RuntimeSources(List.of(first, second)).read(ROOT);

        assertThat(chosen.choices())
                .containsExactly(
                        new Choice(FIRST_25, "chosen by the first"), new Choice(SECOND_17, "chosen by the second"));
        assertThat(chosen.jdks()).containsExactly(FIRST_25, SECOND_17);
    }

    @Test
    void theOrderOfTheListIsTheOrderOfPrecedence() {
        var first = source("chosen by the first", List.of(FIRST_25));
        var second = source("chosen by the second", List.of(SECOND_25));

        assertThat(new RuntimeSources(List.of(first, second)).read(ROOT).jdks()).containsExactly(FIRST_25);
        assertThat(new RuntimeSources(List.of(second, first)).read(ROOT).jdks()).containsExactly(SECOND_25);
    }

    @Test
    void whatEverySourceFoundWrongIsToldAndALaterSourceStillSpeaksWhenAnEarlierOneChoseNothing() {
        var broken = source("chosen by the broken one", List.of(), "the first is broken");
        var working = source("chosen by the working one", List.of(SECOND_25), "the second is half broken");

        var chosen = new RuntimeSources(List.of(broken, working)).read(ROOT);

        assertThat(chosen.jdks()).containsExactly(SECOND_25);
        assertThat(chosen.warnings()).containsExactly("the first is broken", "the second is half broken");
    }
}
