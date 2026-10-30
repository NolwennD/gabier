package fr.craft.gabier.editing.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import fr.craft.gabier.editing.domain.FaultyFileStore.Failure;
import fr.craft.gabier.editing.domain.FaultyFileStore.Operation;
import fr.craft.gabier.editing.domain.FileChange.CreateFile;
import fr.craft.gabier.editing.domain.FileChange.DeleteFile;
import fr.craft.gabier.editing.domain.FileChange.EditFile;
import fr.craft.gabier.editing.domain.FileChange.RenameFile;
import fr.craft.gabier.editing.infrastructure.secondary.NioFileStore;
import fr.craft.gabier.shared.error.domain.ErrorStatus;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A write that fails in the middle of a set: the files are given back as they were. */
class ChangeApplierRollbackTest {

    /** A name that the plan cannot foresee is refused: the file system says so when it is written. */
    private static final String TOO_LONG_FOR_A_FILE_SYSTEM = "x".repeat(300) + ".txt";

    @TempDir
    Path directory;

    private final NioFileStore files = new NioFileStore();

    private Path file(String name, String content) throws IOException {
        return Files.writeString(directory.resolve(name), content);
    }

    private Path path(String name) {
        return directory.resolve(name);
    }

    private static EditFile append(Path file, String text) {
        Position start = new Position(0, 0);

        return new EditFile(file, List.of(new TextEdit(new Range(start, start), text)));
    }

    private static ChangeSet set(FileChange... changes) {
        return new ChangeSet(List.of(changes));
    }

    private Map<String, String> snapshot() throws IOException {
        Map<String, String> snapshot = new TreeMap<>();
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path path : paths.toList()) {
                String content =
                        Files.isDirectory(path) ? "<directory>" : HexFormat.of().formatHex(Files.readAllBytes(path));
                snapshot.put(directory.relativize(path).toString(), content);
            }
        }

        return snapshot;
    }

    @Nested
    class OnTheRealFileSystem {

        private final ChangeApplier applier = new ChangeApplier(files);

        @Test
        void restoresTheFileAlreadyWrittenWhenTheSecondOneCannotBeWritten() throws IOException {
            Path edited = file("A.txt", "original\n");
            Path impossible = path(TOO_LONG_FOR_A_FILE_SYSTEM);
            Map<String, String> before = snapshot();

            assertThatExceptionOfType(EditingException.class)
                    .isThrownBy(() -> applier.apply(set(append(edited, "changed "), new CreateFile(impossible))))
                    .satisfies(exception -> {
                        assertThat(exception.key()).isEqualTo(EditingErrorKey.WRITE_FAILED);
                        assertThat(exception.status()).isEqualTo(ErrorStatus.INTERNAL_SERVER_ERROR);
                        assertThat(exception.parameters()).isEqualTo(Map.of("file", impossible.toString()));
                        assertThat(exception).hasCauseInstanceOf(FileSystemException.class);
                    });

            assertThat(edited).hasContent("original\n");
            assertThat(snapshot()).isEqualTo(before);
        }

        @Test
        void removesTheFilesAlreadyCreated() throws IOException {
            Map<String, String> before = snapshot();

            assertThatExceptionOfType(EditingException.class)
                    .isThrownBy(() -> applier.apply(
                            set(new CreateFile(path("One.txt")), new CreateFile(path(TOO_LONG_FOR_A_FILE_SYSTEM)))))
                    .satisfies(exception -> assertThat(exception.key()).isEqualTo(EditingErrorKey.WRITE_FAILED));

            assertThat(snapshot()).isEqualTo(before);
        }
    }

    @Nested
    class WithAFailureInjected {

        @Test
        void restoresTheFirstFileWhenTheSecondEditFails() throws IOException {
            Path first = file("A.txt", "a\n");
            Path second = file("B.txt", "b\n");
            Map<String, String> before = snapshot();
            ChangeApplier applier = new ChangeApplier(new FaultyFileStore(files).failing(Operation.WRITE, second));

            assertThatExceptionOfType(EditingException.class)
                    .isThrownBy(() -> applier.apply(set(append(first, "x"), append(second, "y"))))
                    .satisfies(exception -> {
                        assertThat(exception.key()).isEqualTo(EditingErrorKey.WRITE_FAILED);
                        assertThat(exception.parameters()).isEqualTo(Map.of("file", second.toString()));
                    });

            assertThat(snapshot()).isEqualTo(before);
        }

        @Test
        void restoresAFileThatWasWrittenHalfWay() throws IOException {
            Path first = file("A.txt", "a\n");
            Path second = file("B.txt", "a long original text\n");
            Map<String, String> before = snapshot();
            FaultyFileStore store =
                    new FaultyFileStore(files).failing(Operation.WRITE, second, 1, Failure.HALF_WRITTEN);

            assertThatExceptionOfType(EditingException.class)
                    .isThrownBy(() -> new ChangeApplier(store).apply(set(append(first, "x"), append(second, "y"))))
                    .satisfies(exception -> assertThat(exception.key()).isEqualTo(EditingErrorKey.WRITE_FAILED));

            assertThat(snapshot()).isEqualTo(before);
        }

        @Test
        void putsBackTheFilesRenamedAndDeletedBeforeTheFailure() throws IOException {
            Path renamed = file("A.txt", "renamed\n");
            Path deleted = file("B.txt", "deleted\n");
            Path edited = file("C.txt", "edited\n");
            Map<String, String> before = snapshot();
            ChangeApplier applier = new ChangeApplier(new FaultyFileStore(files).failing(Operation.WRITE, edited));

            assertThatExceptionOfType(EditingException.class)
                    .isThrownBy(() -> applier.apply(set(
                            new RenameFile(renamed, path("Renamed.txt")),
                            new DeleteFile(deleted),
                            append(edited, "x"))))
                    .satisfies(exception -> assertThat(exception.key()).isEqualTo(EditingErrorKey.WRITE_FAILED));

            assertThat(snapshot()).isEqualTo(before);
        }

        @Test
        void leavesTheSourceAloneWhenTheRenameItselfFails() throws IOException {
            Path edited = file("A.txt", "a\n");
            Path source = file("B.txt", "b\n");
            Map<String, String> before = snapshot();
            ChangeApplier applier =
                    new ChangeApplier(new FaultyFileStore(files).failing(Operation.MOVE, path("Renamed.txt")));

            assertThatExceptionOfType(EditingException.class)
                    .isThrownBy(
                            () -> applier.apply(set(append(edited, "x"), new RenameFile(source, path("Renamed.txt")))))
                    .satisfies(exception -> {
                        assertThat(exception.key()).isEqualTo(EditingErrorKey.WRITE_FAILED);
                        assertThat(exception.parameters())
                                .isEqualTo(Map.of("file", path("Renamed.txt").toString()));
                    });

            assertThat(snapshot()).isEqualTo(before);
        }

        @Test
        void leavesAloneATargetThatAppearedBeforeTheMoveAndReportsOnlyTheFailedWrite() throws IOException {
            Path edited = file("A.txt", "a\n");
            Path source = file("B.txt", "b\n");
            Path target = path("Renamed.txt");
            FaultyFileStore store =
                    new FaultyFileStore(files).failing(Operation.MOVE, target, 1, Failure.FOREIGN_FILE_APPEARS);

            assertThatExceptionOfType(EditingException.class)
                    .isThrownBy(() ->
                            new ChangeApplier(store).apply(set(append(edited, "x"), new RenameFile(source, target))))
                    .satisfies(exception -> {
                        assertThat(exception.key()).isEqualTo(EditingErrorKey.WRITE_FAILED);
                        assertThat(exception.getSuppressed()).isEmpty();
                    });

            assertThat(edited).hasContent("a\n");
            assertThat(source).hasContent("b\n");
            assertThat(target).hasContent("someone else's file");
        }

        @Test
        void leavesAloneAFileThatAppearedBeforeTheCreateAndReportsOnlyTheFailedWrite() throws IOException {
            Path edited = file("A.txt", "a\n");
            Path target = path("New.txt");
            FaultyFileStore store =
                    new FaultyFileStore(files).failing(Operation.CREATE, target, 1, Failure.FOREIGN_FILE_APPEARS);

            assertThatExceptionOfType(EditingException.class)
                    .isThrownBy(() -> new ChangeApplier(store).apply(set(append(edited, "x"), new CreateFile(target))))
                    .satisfies(exception -> {
                        assertThat(exception.key()).isEqualTo(EditingErrorKey.WRITE_FAILED);
                        assertThat(exception.parameters()).isEqualTo(Map.of("file", target.toString()));
                        assertThat(exception.getSuppressed()).isEmpty();
                    });

            assertThat(edited).hasContent("a\n");
            assertThat(target).hasContent("someone else's file");
        }

        @Test
        void removesAFileItCreatedWhenItsWriteFailsHalfWay() throws IOException {
            Path target = path("New.txt");
            Map<String, String> before = snapshot();
            FaultyFileStore store =
                    new FaultyFileStore(files).failing(Operation.CREATE, target, 1, Failure.HALF_WRITTEN);

            assertThatExceptionOfType(EditingException.class)
                    .isThrownBy(() -> new ChangeApplier(store).apply(set(new CreateFile(target))))
                    .satisfies(exception -> assertThat(exception.key()).isEqualTo(EditingErrorKey.WRITE_FAILED));

            assertThat(snapshot()).isEqualTo(before);
        }

        @Test
        void putsBackAFileMovedAwayWhenTheFileCreatedUnderItsOldNameCannotBeRemoved() throws IOException {
            Path source = file("A.txt", "a\n");
            Path moved = path("Moved.txt");
            Path edited = file("C.txt", "c\n");
            FaultyFileStore store =
                    new FaultyFileStore(files).failing(Operation.WRITE, edited).failing(Operation.DELETE, source);

            assertThatExceptionOfType(EditingException.class)
                    .isThrownBy(() -> new ChangeApplier(store)
                            .apply(set(new RenameFile(source, moved), new CreateFile(source), append(edited, "x"))))
                    .satisfies(exception -> {
                        assertThat(exception.key()).isEqualTo(EditingErrorKey.ROLLBACK_INCOMPLETE);
                        assertThat(exception.parameters()).containsEntry("notRestored", source + ", " + moved);
                    });
        }

        @Test
        void givesBackADeletedFileWithItsPermissions() throws IOException {
            Path deleted = file("run.sh", "echo\n");
            Files.setPosixFilePermissions(deleted, PosixFilePermissions.fromString("rwxr-x---"));
            Path edited = file("B.txt", "b\n");
            ChangeApplier applier = new ChangeApplier(new FaultyFileStore(files).failing(Operation.WRITE, edited));

            assertThatExceptionOfType(EditingException.class)
                    .isThrownBy(() -> applier.apply(set(new DeleteFile(deleted), append(edited, "x"))))
                    .satisfies(exception -> assertThat(exception.key()).isEqualTo(EditingErrorKey.WRITE_FAILED));

            assertThat(deleted).hasContent("echo\n");
            assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(deleted)))
                    .isEqualTo("rwxr-x---");
        }

        @Test
        void givesBackAFileMovedThenDeletedWithItsPermissions() throws IOException {
            Path source = file("A.sh", "echo\n");
            Files.setPosixFilePermissions(source, PosixFilePermissions.fromString("rwx------"));
            Path moved = path("B.sh");
            Path edited = file("C.txt", "c\n");
            ChangeApplier applier = new ChangeApplier(new FaultyFileStore(files).failing(Operation.WRITE, edited));

            assertThatExceptionOfType(EditingException.class)
                    .isThrownBy(() -> applier.apply(
                            set(new RenameFile(source, moved), new DeleteFile(moved), append(edited, "x"))))
                    .satisfies(exception -> assertThat(exception.key()).isEqualTo(EditingErrorKey.WRITE_FAILED));

            assertThat(source).hasContent("echo\n");
            assertThat(moved).doesNotExist();
            assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(source)))
                    .isEqualTo("rwx------");
        }

        @Test
        void leavesTheFileAloneWhenItsDeletionItselfFails() throws IOException {
            Path edited = file("A.txt", "a\n");
            Path deleted = file("B.txt", "b\n");
            Map<String, String> before = snapshot();
            ChangeApplier applier = new ChangeApplier(new FaultyFileStore(files).failing(Operation.DELETE, deleted));

            assertThatExceptionOfType(EditingException.class)
                    .isThrownBy(() -> applier.apply(set(append(edited, "x"), new DeleteFile(deleted))))
                    .satisfies(exception -> assertThat(exception.key()).isEqualTo(EditingErrorKey.WRITE_FAILED));

            assertThat(snapshot()).isEqualTo(before);
        }

        @Test
        void restoresTheFilesWhenTheStoreThrowsARuntimeException() throws IOException {
            Path first = file("A.txt", "a\n");
            Path second = file("B.txt", "b\n");
            Map<String, String> before = snapshot();
            FaultyFileStore store = new FaultyFileStore(files).failing(Operation.WRITE, second, 1, Failure.RUNTIME);

            assertThatExceptionOfType(EditingException.class)
                    .isThrownBy(() -> new ChangeApplier(store).apply(set(append(first, "x"), append(second, "y"))))
                    .satisfies(exception -> {
                        assertThat(exception.key()).isEqualTo(EditingErrorKey.WRITE_FAILED);
                        assertThat(exception).hasCauseInstanceOf(UncheckedIOException.class);
                    });

            assertThat(snapshot()).isEqualTo(before);
        }

        @Test
        void saysWhichFilesCouldNotBeRestoredAndRestoresTheOthers() throws IOException {
            Path first = file("A.txt", "a\n");
            Path second = file("B.txt", "b\n");
            Path third = file("C.txt", "c\n");
            // C fails when written, and A when put back (its second write).
            FaultyFileStore store = new FaultyFileStore(files)
                    .failing(Operation.WRITE, third)
                    .failing(Operation.WRITE, first, 2, Failure.IO);
            ChangeApplier applier = new ChangeApplier(store);

            assertThatExceptionOfType(EditingException.class)
                    .isThrownBy(() -> applier.apply(set(append(first, "x"), append(second, "y"), append(third, "z"))))
                    .satisfies(exception -> {
                        assertThat(exception.key()).isEqualTo(EditingErrorKey.ROLLBACK_INCOMPLETE);
                        assertThat(exception.status()).isEqualTo(ErrorStatus.INTERNAL_SERVER_ERROR);
                        assertThat(exception.parameters())
                                .isEqualTo(Map.of(
                                        "file", third.toString(),
                                        "notRestored", first.toString()));
                        assertThat(exception.getCause()).hasMessageContaining("injected failure on " + third);
                        assertThat(exception.getSuppressed()).hasSize(1);
                        assertThat(exception.getSuppressed()[0]).hasMessageContaining("injected failure on " + first);
                    });

            assertThat(first).hasContent("xa\n");
            assertThat(second).hasContent("b\n");
            assertThat(third).hasContent("c\n");
        }

        @Test
        void listsEveryFileThatCouldNotBeRestored() throws IOException {
            Path first = file("A.txt", "a\n");
            Path second = file("B.txt", "b\n");
            Path third = file("C.txt", "c\n");
            FaultyFileStore store = new FaultyFileStore(files)
                    .failing(Operation.WRITE, third)
                    .failing(Operation.WRITE, second, 2, Failure.IO)
                    .failing(Operation.WRITE, first, 2, Failure.IO);
            ChangeApplier applier = new ChangeApplier(store);

            assertThatExceptionOfType(EditingException.class)
                    .isThrownBy(() -> applier.apply(set(append(first, "x"), append(second, "y"), append(third, "z"))))
                    .satisfies(exception ->
                            assertThat(exception.parameters()).containsEntry("notRestored", second + ", " + first));
        }
    }
}
