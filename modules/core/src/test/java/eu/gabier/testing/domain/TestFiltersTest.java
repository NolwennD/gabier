package eu.gabier.testing.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TestFiltersTest {

    private static final Path FILE = Path.of("/project/infinitest.filters");

    private static TestFilters parse(String text) {
        return TestFilters.parse(FILE, text);
    }

    @Test
    void leavesNothingOutWithoutAFile() {
        assertThat(TestFilters.none().excludes("app.GreeterIT")).isFalse();
    }

    @Test
    void leavesNothingOutOfAnEmptyFile() {
        assertThat(parse("").excludes("app.GreeterIT")).isFalse();
    }

    @Test
    void leavesOutTheClassesAnExcludeLineMatchesInWhole() {
        var filters = parse("exclude .*IT\n");

        assertThat(filters.excludes("app.GreeterIT")).isTrue();
        assertThat(filters.excludes("app.Greeter")).isFalse();
        assertThat(filters.excludes("app.GreeterITCase")).isFalse();
    }

    @Test
    void takesALineThatIsNoDirectiveAsAnExcludeLine() {
        var filters = parse("app\\.slow\\..*\n");

        assertThat(filters.excludes("app.slow.BigTest")).isTrue();
        assertThat(filters.excludes("app.fast.SmallTest")).isFalse();
    }

    @Test
    void readsSeveralPatternsOnALineSeparatedByCommas() {
        var filters = parse("exclude .*IT , app\\.slow\\..*,,\n");

        assertThat(filters.excludes("app.GreeterIT")).isTrue();
        assertThat(filters.excludes("app.slow.BigTest")).isTrue();
        assertThat(filters.excludes("app.Greeter")).isFalse();
    }

    @Test
    void keepsOnlyTheClassesAnIncludeLineMatches() {
        var filters = parse("include app\\.fast\\..*\n");

        assertThat(filters.excludes("app.fast.SmallTest")).isFalse();
        assertThat(filters.excludes("app.slow.BigTest")).isTrue();
    }

    @Test
    void leavesOutWhatAnExcludeLineMatchesAmongTheIncluded() {
        var filters = parse("include app\\..*\nexclude .*IT\n");

        assertThat(filters.excludes("app.Greeter")).isFalse();
        assertThat(filters.excludes("app.GreeterIT")).isTrue();
        assertThat(filters.excludes("other.Greeter")).isTrue();
    }

    @Test
    void matchesTheBinaryNameOfANestedClass() {
        assertThat(parse("exclude .*\\$Slow\n").excludes("app.GreeterTest$Slow"))
                .isTrue();
    }

    @Test
    void ignoresTheCommentsAndTheBlankLines() {
        var filters = parse("# a comment\n! another\n\n   \nexclude .*IT\n");

        assertThat(filters.excludes("app.GreeterIT")).isTrue();
        assertThat(filters.excludes("app.Greeter")).isFalse();
        assertThat(filters.excludes("# a comment")).isFalse();
    }

    @Test
    void takesTheLinesOfGroupsAndListenersForNoPattern() {
        var filters = parse("""
            includeGroups fast
            excludeGroups slow
            testngListeners app.Listener
            ## groups=fast
            ## excluded-groups=slow
            ## listeners=app.Listener
            """);

        assertThat(filters.excludes("fast")).isFalse();
        assertThat(filters.excludes("slow")).isFalse();
        assertThat(filters.excludes("app.Listener")).isFalse();
        assertThat(filters.excludes("app.Greeter")).isFalse();
    }

    @Test
    void readsTheLinesOfAFileWrittenOnWindows() {
        var filters = parse("exclude .*IT\r\nexclude .*Slow\r\n");

        assertThat(filters.excludes("app.GreeterIT")).isTrue();
        assertThat(filters.excludes("app.GreeterSlow")).isTrue();
    }

    @Test
    void refusesAPatternThatIsNoRegularExpressionNamingTheFileAndTheLine() {
        assertThatExceptionOfType(TestingException.class)
                .isThrownBy(() -> parse("exclude .*IT\nexclude *IT\n"))
                .satisfies(exception -> {
                    assertThat(exception.key()).isEqualTo(TestingErrorKey.INVALID_TEST_FILTER);
                    assertThat(exception.parameters())
                            .isEqualTo(Map.of("file", FILE.toString(), "line", "2", "pattern", "*IT"));
                    assertThat(exception.getMessage())
                            .startsWith("The pattern *IT of " + FILE + ", line 2, is not a regular expression");
                });
    }
}
