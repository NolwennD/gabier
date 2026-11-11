package eu.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The daemon served by the fake language server writes the project of the scenarios, and never over what exists. */
class FakeServedMainTest {

    private static final Path BROKEN = Path.of("src/main/java/demo/Broken.java");

    @TempDir
    Path directory;

    @Test
    void writesTheProjectOfTheScenariosInAFreshFolder() throws IOException {
        var project = directory.resolve("project");

        FakeServedMain.serving(project);

        assertThat(project.resolve(BROKEN)).isRegularFile();
    }

    @Test
    void writesTheProjectOfTheScenariosInAnEmptyFolder() throws IOException {
        FakeServedMain.serving(directory);

        assertThat(directory.resolve(BROKEN)).isRegularFile();
    }

    @Test
    void compilesTheShopOfTheSelectionOfTestsNextToTheScenariosWithItsFilters() throws IOException {
        FakeServedMain.serving(directory);

        var built = FakeServedMain.building(directory);

        assertThat(built.folders())
                .extracting(folder -> directory.relativize(folder.classes()))
                .containsExactly(Path.of("bin"), Path.of("test-bin"));
        assertThat(directory.resolve("bin/shop/Greeter.class")).isRegularFile();
        assertThat(directory.resolve("test-bin/shop/GreeterTest.class")).isRegularFile();
        assertThat(directory.resolve("infinitest.filters")).hasContent("exclude .*IT\n");
        assertThat(directory.resolve(BROKEN)).isRegularFile();
    }

    @Test
    void acceptsAFolderThatDoesNotExistYetAndAnEmptyOne() throws IOException {
        assertThat(FakeServedMain.refusal(directory.resolve("project"))).isEmpty();
        assertThat(FakeServedMain.refusal(directory)).isEmpty();
    }

    @Test
    void refusesAFolderThatIsNotEmptyAndLeavesItsFilesAlone() throws IOException {
        var mine = Files.writeString(directory.resolve("Main.java"), "class Main {}");

        assertThat(FakeServedMain.refusal(directory))
                .hasValueSatisfying(reason ->
                        assertThat(reason).contains(directory.toString()).contains("not empty"));

        assertThat(mine).hasContent("class Main {}");
        assertThat(directory.resolve(BROKEN)).doesNotExist();
    }

    @Test
    void refusesAPathThatIsAFile() throws IOException {
        var file = Files.writeString(directory.resolve("file"), "x");

        assertThat(FakeServedMain.refusal(file))
                .hasValueSatisfying(
                        reason -> assertThat(reason).contains(file.toString()).contains("not a folder"));
    }

    @Test
    void refusesALinkThatLeadsNowhereAsSomethingThatIsThere() throws IOException {
        // The link does not exist for a check that follows it, but writing the project through it would fail.
        var link = Files.createSymbolicLink(directory.resolve("link"), directory.resolve("nowhere"));

        assertThat(FakeServedMain.refusal(link))
                .hasValueSatisfying(
                        reason -> assertThat(reason).contains(link.toString()).contains("not a folder"));
        assertThat(directory.resolve("nowhere")).doesNotExist();
    }
}
