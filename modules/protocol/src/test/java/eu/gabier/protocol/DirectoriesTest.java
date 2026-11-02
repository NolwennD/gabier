package eu.gabier.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DirectoriesTest {

    @TempDir
    Path folder;

    @Test
    void deletesTheFolderAndEverythingInIt() throws IOException {
        Path directory = Files.createDirectories(folder.resolve("data/.metadata/.plugins"));
        Files.writeString(directory.resolve("state"), "imported");
        Files.writeString(folder.resolve("data/file"), "text");

        Directories.delete(folder.resolve("data"));

        assertThat(folder.resolve("data")).doesNotExist();
    }

    @Test
    void deletesALinkAndNeverWhatItPointsTo() throws IOException {
        Path precious = Files.createDirectories(folder.resolve("precious"));
        Files.writeString(precious.resolve("state"), "kept");
        Files.createSymbolicLink(Files.createDirectories(folder.resolve("data")).resolve("link"), precious);

        Directories.delete(folder.resolve("data"));

        assertThat(folder.resolve("data")).doesNotExist();
        assertThat(precious.resolve("state")).hasContent("kept");
    }
}
