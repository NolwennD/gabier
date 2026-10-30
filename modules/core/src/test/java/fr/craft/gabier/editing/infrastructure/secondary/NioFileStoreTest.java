package fr.craft.gabier.editing.infrastructure.secondary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NioFileStoreTest {

    private final NioFileStore store = new NioFileStore();

    @TempDir
    Path directory;

    private static List<String> names(Path folder) throws IOException {
        try (Stream<Path> children = Files.list(folder)) {
            return children.map(child -> child.getFileName().toString())
                    .sorted()
                    .toList();
        }
    }

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
    class Folders {

        @Test
        void knowsAFolderALinkToAFolderAFileAndAMissingPath() throws IOException {
            Path file = Files.writeString(directory.resolve("a.txt"), "a");
            Path folder = Files.createDirectory(directory.resolve("folder"));
            Path link = Files.createSymbolicLink(directory.resolve("link"), folder);

            assertThat(store.isFolder(folder)).isTrue();
            assertThat(store.isFolder(link)).isTrue();
            assertThat(store.isFolder(file)).isFalse();
            assertThat(store.isFolder(directory.resolve("missing"))).isFalse();
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
        void replacesTheFileInsteadOfRewritingItInPlace() throws IOException {
            Path file = Files.writeString(directory.resolve("a.txt"), "old");
            Path otherNameOfTheOldFile = Files.createLink(directory.resolve("old.txt"), file);

            store.write(file, new byte[] {'n', 'e', 'w'});

            assertThat(file).hasContent("new");
            assertThat(otherNameOfTheOldFile).hasContent("old");
        }

        @Test
        void keepsThePermissionsOfTheFile() throws IOException {
            Path file = Files.writeString(directory.resolve("run.sh"), "old");
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rwxr-x---"));

            store.write(file, new byte[] {1});

            assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(file)))
                    .isEqualTo("rwxr-x---");
        }

        @Test
        void leavesNoTemporaryFileBehind() throws IOException {
            Files.writeString(directory.resolve("a.txt"), "old");

            store.write(directory.resolve("a.txt"), new byte[] {1});

            assertThat(names(directory)).containsExactly("a.txt");
        }

        @Test
        void leavesNoTemporaryFileBehindWhenTheWriteFails() throws IOException {
            Path folder = Files.createDirectory(directory.resolve("folder"));

            assertThatExceptionOfType(IOException.class).isThrownBy(() -> store.write(folder, new byte[] {1}));

            assertThat(names(directory)).containsExactly("folder");
        }

        @Test
        void writesThroughALinkAndKeepsTheLink() throws IOException {
            Files.createDirectory(directory.resolve("real"));
            Path target = Files.writeString(directory.resolve("real/a.txt"), "old");
            Path link = Files.createSymbolicLink(directory.resolve("link.txt"), Path.of("real/a.txt"));

            store.write(link, new byte[] {'n', 'e', 'w'});

            assertThat(link).isSymbolicLink();
            assertThat(target).hasContent("new");
            assertThat(names(directory.resolve("real"))).containsExactly("a.txt");
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
    class Permissions {

        @Test
        void readsAndSetsThePermissionsOfAFile() throws IOException {
            Path file = Files.writeString(directory.resolve("a.txt"), "a");

            store.setPermissions(file, PosixFilePermissions.fromString("rw-r-----"));

            assertThat(store.permissions(file)).isEqualTo(PosixFilePermissions.fromString("rw-r-----"));
        }

        @Test
        void failsOnAMissingFile() {
            Path file = directory.resolve("missing.txt");

            assertThatExceptionOfType(NoSuchFileException.class).isThrownBy(() -> store.permissions(file));
            assertThatExceptionOfType(NoSuchFileException.class)
                    .isThrownBy(() -> store.setPermissions(file, PosixFilePermissions.fromString("rw-------")));
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
