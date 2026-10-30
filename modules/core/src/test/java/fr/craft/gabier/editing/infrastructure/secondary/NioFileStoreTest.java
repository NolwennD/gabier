package fr.craft.gabier.editing.infrastructure.secondary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NioFileStoreTest {

    private final NioFileStore store = new NioFileStore();

    @TempDir
    Path directory;

    @Nested
    class Existence {

        @Test
        void knowsAFileADirectoryAndAMissingPath() throws IOException {
            Path file = Files.writeString(directory.resolve("a.txt"), "a");

            assertThat(store.exists(file)).isTrue();
            assertThat(store.exists(directory)).isTrue();
            assertThat(store.exists(directory.resolve("missing.txt"))).isFalse();
        }

        @Test
        void countsALinkWhoseTargetIsGoneAsExisting() throws IOException {
            Path link = Files.createSymbolicLink(directory.resolve("link"), directory.resolve("gone"));

            assertThat(store.exists(link)).isTrue();
        }
    }

    @Nested
    class Reading {

        @Test
        void givesTheBytesOfAFile() throws IOException {
            Path file = Files.write(directory.resolve("a.bin"), new byte[] {1, 2, (byte) 0xFF});

            assertThat(store.read(file)).containsExactly(1, 2, (byte) 0xFF);
        }

        @Test
        void failsOnAMissingFile() {
            assertThatExceptionOfType(NoSuchFileException.class)
                    .isThrownBy(() -> store.read(directory.resolve("missing.txt")));
        }
    }

    @Nested
    class Writing {

        @Test
        void createsAFile() throws IOException {
            Path file = directory.resolve("new.txt");

            store.write(file, new byte[] {1, 2});

            assertThat(Files.readAllBytes(file)).containsExactly(1, 2);
        }

        @Test
        void replacesTheWholeContentOfAFile() throws IOException {
            Path file = Files.writeString(directory.resolve("a.txt"), "a long content");

            store.write(file, new byte[] {1});

            assertThat(Files.readAllBytes(file)).containsExactly(1);
        }

        @Test
        void doesNotCreateTheDirectory() {
            Path file = directory.resolve("missing").resolve("new.txt");

            assertThatExceptionOfType(NoSuchFileException.class).isThrownBy(() -> store.write(file, new byte[0]));
            assertThat(directory.resolve("missing")).doesNotExist();
        }
    }

    @Nested
    class Moving {

        @Test
        void movesAFile() throws IOException {
            Path from = Files.writeString(directory.resolve("from.txt"), "content");
            Path to = directory.resolve("to.txt");

            store.move(from, to);

            assertThat(from).doesNotExist();
            assertThat(to).hasContent("content");
        }

        @Test
        void neverReplacesAFile() throws IOException {
            Path from = Files.writeString(directory.resolve("from.txt"), "from");
            Path to = Files.writeString(directory.resolve("to.txt"), "to");

            assertThatExceptionOfType(FileAlreadyExistsException.class).isThrownBy(() -> store.move(from, to));
            assertThat(from).hasContent("from");
            assertThat(to).hasContent("to");
        }
    }

    @Nested
    class Deleting {

        @Test
        void deletesAFile() throws IOException {
            Path file = Files.writeString(directory.resolve("a.txt"), "a");

            store.delete(file);

            assertThat(file).doesNotExist();
        }

        @Test
        void failsOnAMissingFile() {
            assertThatExceptionOfType(NoSuchFileException.class)
                    .isThrownBy(() -> store.delete(directory.resolve("missing.txt")));
        }
    }
}
