package fr.craft.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    void refusesAFolderThatIsNotEmptyAndLeavesItsFilesAlone() throws IOException {
        var mine = Files.writeString(directory.resolve("Main.java"), "class Main {}");

        assertThatThrownBy(() -> FakeServedMain.serving(directory))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(directory.toString())
                .hasMessageContaining("not empty");

        assertThat(mine).hasContent("class Main {}");
        assertThat(directory.resolve(BROKEN)).doesNotExist();
    }

    @Test
    void refusesAPathThatIsAFile() throws IOException {
        var file = Files.writeString(directory.resolve("file"), "x");

        assertThatThrownBy(() -> FakeServedMain.serving(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(file.toString())
                .hasMessageContaining("not a folder");
    }
}
