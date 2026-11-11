package eu.gabier.testing.domain;

import static org.assertj.core.api.Assertions.assertThat;

import eu.gabier.testing.fixtures.CompiledSources;
import eu.gabier.testing.infrastructure.secondary.NioProjectFiles;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClassIndexTest {

    @TempDir
    Path directory;

    private Path first;
    private Path second;

    private ClassIndex indexOfLibraries(ProjectFiles files) throws IOException {
        first = CompiledSources.jar(
                directory.resolve("first.jar"), Map.of("lib/First.java", "package lib; public class First {}"));
        second = CompiledSources.jar(
                directory.resolve("second.jar"), Map.of("lib/Second.java", "package lib; public class Second {}"));

        return new ClassIndex(files, new BuiltProject(List.of(), List.of(first, second), List.of()));
    }

    @Test
    void findsAClassInTheFirstLibraryThatHoldsIt() throws IOException {
        var index = indexOfLibraries(new NioProjectFiles());

        assertThat(index.find("lib.First")).isPresent();
        assertThat(index.find("lib.Second")).isPresent();
    }

    @Test
    void findsNoClassThatNoLibraryHolds() throws IOException {
        var index = indexOfLibraries(new NioProjectFiles());

        assertThat(index.find("lib.Third")).isEmpty();
    }

    @Test
    void readsALibraryClassOnce() throws IOException {
        var index = indexOfLibraries(new NioProjectFiles());
        index.find("lib.Second");
        Files.delete(second);

        assertThat(index.find("lib.Second")).isPresent();
    }

    @Test
    void doesNotLookForTheClassesOfTheJdkInTheLibraries() throws IOException {
        var index = indexOfLibraries(new FaultyProjectFiles(FaultyProjectFiles.Operation.READ_FROM_LIBRARY));

        assertThat(index.find("java.lang.String")).isEmpty();
    }
}
