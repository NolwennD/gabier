package eu.gabier.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The clean-up of a whole file and the action on one range are two ways to the same rewrite: the pages of {@code
 * cleanup}, {@code actions} and {@code apply-action} each link to the other two, so that a reader of one finds the way
 * to the others.
 */
class CleanupPageLinksTest {

    private static final List<String> PAGES = List.of("cleanup", "actions", "apply-action");

    static Stream<String> pages() {
        return PAGES.stream();
    }

    @ParameterizedTest
    @MethodSource("pages")
    void eachPageLinksToTheTwoOthers(String page) throws IOException {
        var text = pageText(page);

        PAGES.stream()
                .filter(other -> !other.equals(page))
                .forEach(other -> assertThat(text)
                        .as("the page %s links to %s", page, other)
                        .contains("](" + other + ".md)"));
    }

    @Test
    void thePageOfCleanupSaysHowToChangeOnePlaceOnly() throws IOException {
        assertThat(pageText("cleanup"))
                .contains("applies to the whole file")
                .contains("for one place only")
                .contains("gabier actions")
                .contains("gabier apply-action")
                .contains("text block");
    }

    private static String pageText(String page) throws IOException {
        return Files.readString(
                Path.of(System.getProperty("gabier.docs.commands")).resolve(page + ".md"));
    }
}
