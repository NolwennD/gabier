package eu.gabier.testing.infrastructure.secondary;

import static org.assertj.core.api.Assertions.assertThat;

import eu.gabier.testing.fixtures.CompiledSources;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NioProjectFilesTest {

    private static final Map<String, String> SOURCES = Map.of(
            "app/Greeter.java", "package app; public class Greeter { public static class Inner {} }",
            "app/deep/Deeper.java", "package app.deep; public class Deeper {}");

    @TempDir
    Path directory;

    private final NioProjectFiles files = new NioProjectFiles();

    @Test
    void listsTheClassFilesOfAFolderWhateverTheDepthInTheOrderOfTheirPaths() throws IOException {
        Path classes = directory.resolve("bin");
        CompiledSources.compile(SOURCES, classes, List.of());
        Files.writeString(classes.resolve("notes.txt"), "not a class");

        assertThat(files.classFilesIn(classes))
                .containsExactly(
                        classes.resolve("app/Greeter$Inner.class"),
                        classes.resolve("app/Greeter.class"),
                        classes.resolve("app/deep/Deeper.class"));
    }

    @Test
    void listsNoFolderThatIsNamedLikeAClassFile() throws IOException {
        Path classes = directory.resolve("bin");
        Files.createDirectories(classes.resolve("app/Odd.class"));

        assertThat(files.classFilesIn(classes)).isEmpty();
    }

    @Test
    void listsNoClassFileOfAFolderThatDoesNotExist() throws IOException {
        assertThat(files.classFilesIn(directory.resolve("missing"))).isEmpty();
    }

    @Test
    void readsTheBytesOfAClassFile() throws IOException {
        Path classes = directory.resolve("bin");
        CompiledSources.compile(SOURCES, classes, List.of());

        assertThat(files.read(classes.resolve("app/Greeter.class")))
                .isEqualTo(Files.readAllBytes(classes.resolve("app/Greeter.class")));
    }

    @Test
    void readsAClassOfAJarByItsBinaryName() throws IOException {
        Path jar = CompiledSources.jar(directory.resolve("lib.jar"), SOURCES);
        Path classes = directory.resolve("bin");
        CompiledSources.compile(SOURCES, classes, List.of());

        assertThat(files.readFromLibrary(jar, "app.Greeter$Inner"))
                .hasValue(Files.readAllBytes(classes.resolve("app/Greeter$Inner.class")));
    }

    @Test
    void findsNoClassThatAJarDoesNotHold() throws IOException {
        Path jar = CompiledSources.jar(directory.resolve("lib.jar"), SOURCES);

        assertThat(files.readFromLibrary(jar, "app.Missing")).isEmpty();
    }

    @Test
    void readsAClassOfAFolderOfClassesAsALibrary() throws IOException {
        Path classes = directory.resolve("bin");
        CompiledSources.compile(SOURCES, classes, List.of());

        assertThat(files.readFromLibrary(classes, "app.deep.Deeper"))
                .hasValue(Files.readAllBytes(classes.resolve("app/deep/Deeper.class")));
        assertThat(files.readFromLibrary(classes, "app.Missing")).isEmpty();
    }

    @Test
    void findsNoClassInALibraryThatDoesNotExist() throws IOException {
        assertThat(files.readFromLibrary(directory.resolve("gone.jar"), "app.Greeter"))
                .isEmpty();
    }

    @Test
    void readsTheTextOfAFileAndFindsNoneForAFileThatDoesNotExist() throws IOException {
        Path filters = Files.writeString(directory.resolve("infinitest.filters"), "exclude .*IT\n");

        assertThat(files.textOf(filters)).hasValue("exclude .*IT\n");
        assertThat(files.textOf(directory.resolve("missing.filters"))).isEmpty();
    }

    @Test
    void tellsAFileFromAFolderAndFromNothing() throws IOException {
        Path file = Files.writeString(directory.resolve("A.java"), "class A {}");

        assertThat(files.isFile(file)).isTrue();
        assertThat(files.isFile(directory)).isFalse();
        assertThat(files.isFile(directory.resolve("B.java"))).isFalse();
    }
}
