package fr.craft.gabier.editing.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import fr.craft.gabier.editing.domain.ChangeReport.Renamed;
import fr.craft.gabier.editing.domain.FileChange.CreateFile;
import fr.craft.gabier.editing.domain.FileChange.DeleteFile;
import fr.craft.gabier.editing.domain.FileChange.EditFile;
import fr.craft.gabier.editing.domain.FileChange.RenameFile;
import fr.craft.gabier.editing.infrastructure.secondary.NioFileStore;
import fr.craft.gabier.shared.error.domain.ErrorStatus;
import fr.craft.gabier.shared.error.domain.MissingMandatoryValueException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ChangeApplierTest {

    @TempDir
    Path directory;

    private ChangeApplier applier;

    @BeforeEach
    void createApplier() {
        applier = new ChangeApplier(new NioFileStore());
    }

    private Path file(String name, String content) throws IOException {
        return Files.writeString(directory.resolve(name), content);
    }

    private Path path(String name) {
        return directory.resolve(name);
    }

    private static TextEdit edit(int startLine, int startColumn, int endLine, int endColumn, String newText) {
        return new TextEdit(new Range(new Position(startLine, startColumn), new Position(endLine, endColumn)), newText);
    }

    private static ChangeSet set(FileChange... changes) {
        return new ChangeSet(List.of(changes));
    }

    /** Every path under the directory, with the bytes of its files in hexadecimal: what "nothing changed" means. */
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

    private void assertRefusedAndUntouched(ChangeSet changes, EditingErrorKey key) throws IOException {
        Map<String, String> before = snapshot();

        assertThatExceptionOfType(EditingException.class)
                .isThrownBy(() -> applier.apply(changes))
                .satisfies(exception -> assertThat(exception.key()).isEqualTo(key));

        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void rejectsMissingStore() {
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> new ChangeApplier(null))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("store"));
    }

    @Nested
    class Editing {

        @Test
        void writesTheEditedFile() throws IOException {
            Path file = file("A.java", "class A {}\n");

            ChangeReport report = applier.apply(set(new EditFile(file, List.of(edit(0, 6, 0, 7, "B")))));

            assertThat(file).hasContent("class B {}\n");
            assertThat(report.modified()).containsExactly(file);
            assertThat(report.created()).isEmpty();
            assertThat(report.renamed()).isEmpty();
            assertThat(report.deleted()).isEmpty();
        }

        @Test
        void keepsTheBomAndTheCrLfLineEndingsOfTheFile() throws IOException {
            byte[] bytes = "﻿a\r\nb\r\n".getBytes(StandardCharsets.UTF_8);
            Path file = Files.write(path("A.txt"), bytes);

            applier.apply(set(new EditFile(file, List.of(edit(1, 1, 1, 1, "x\ny")))));

            assertThat(Files.readAllBytes(file)).isEqualTo("﻿a\r\nbx\r\ny\r\n".getBytes(StandardCharsets.UTF_8));
        }

        @Test
        void countsACharacterOutsideTheBmpAsTwoColumns() throws IOException {
            Path file = file("A.txt", "😀 = 1\n");

            applier.apply(set(new EditFile(file, List.of(edit(0, 5, 0, 6, "2")))));

            assertThat(file).hasContent("😀 = 2\n");
        }

        @Test
        void appliesTheEditsOfOneFileWhateverTheirOrderInTheSet() throws IOException {
            Path file = file("A.txt", "abc\ndef\n");

            applier.apply(set(new EditFile(file, List.of(edit(1, 0, 1, 1, "D"), edit(0, 0, 0, 1, "A")))));

            assertThat(file).hasContent("Abc\nDef\n");
        }

        @Test
        void appliesTheChangesOfTheSetToTheirFiles() throws IOException {
            Path first = file("A.txt", "a");
            Path second = file("B.txt", "b");

            ChangeReport report = applier.apply(set(
                    new EditFile(first, List.of(edit(0, 1, 0, 1, "1"))),
                    new EditFile(second, List.of(edit(0, 0, 0, 0, "2")))));

            assertThat(first).hasContent("a1");
            assertThat(second).hasContent("2b");
            assertThat(report.modified()).containsExactly(first, second);
        }

        @Test
        void appliesTwoChangesToTheSameFileOneAfterTheOther() throws IOException {
            Path file = file("A.txt", "abc");

            ChangeReport report = applier.apply(set(
                    new EditFile(file, List.of(edit(0, 0, 0, 1, "XXXX"))),
                    new EditFile(file, List.of(edit(0, 4, 0, 5, "Y")))));

            assertThat(file).hasContent("XXXXYc");
            assertThat(report.modified()).containsExactly(file);
        }

        @Test
        void doesNotTouchNorReportAFileWhoseTextDoesNotChange() throws IOException {
            Path file = file("A.txt", "abc");
            Files.setLastModifiedTime(file, java.nio.file.attribute.FileTime.fromMillis(1_000));

            ChangeReport report = applier.apply(set(new EditFile(file, List.of(edit(0, 1, 0, 2, "b")))));

            assertThat(report.modified()).isEmpty();
            assertThat(report.diff()).isEmpty();
            assertThat(Files.getLastModifiedTime(file).toMillis()).isEqualTo(1_000);
        }

        @Test
        void refusesOverlappingEditsAndChangesNothing() throws IOException {
            Path other = file("B.txt", "b");
            Path file = file("A.txt", "abcdef");

            assertRefusedAndUntouched(
                    set(
                            new EditFile(other, List.of(edit(0, 0, 0, 1, "B"))),
                            new EditFile(file, List.of(edit(0, 0, 0, 3, "x"), edit(0, 2, 0, 4, "y")))),
                    EditingErrorKey.OVERLAPPING_EDITS);
        }

        @Test
        void refusesStaleEditsAndChangesNothing() throws IOException {
            Path other = file("B.txt", "b");
            Path file = file("A.txt", "abc");

            assertRefusedAndUntouched(
                    set(
                            new EditFile(other, List.of(edit(0, 0, 0, 1, "B"))),
                            new EditFile(file, List.of(edit(0, 0, 0, 4, "x")))),
                    EditingErrorKey.STALE_EDIT);
        }

        @Test
        void refusesAFileThatIsNotUtf8AndChangesNothing() throws IOException {
            Path file = Files.write(path("A.txt"), "café".getBytes(StandardCharsets.ISO_8859_1));

            assertRefusedAndUntouched(
                    set(new EditFile(file, List.of(edit(0, 0, 0, 1, "x")))), EditingErrorKey.UNSUPPORTED_ENCODING);
        }

        @Test
        void refusesAReplacementThatUtf8CannotEncodeAndChangesNothing() throws IOException {
            Path file = file("A.txt", "abc");

            assertRefusedAndUntouched(
                    set(new EditFile(file, List.of(edit(0, 0, 0, 1, "\uD83D")))), EditingErrorKey.UNSUPPORTED_ENCODING);
        }

        @Test
        void refusesAMissingFile() throws IOException {
            file("B.txt", "b");

            assertRefusedAndUntouched(
                    set(new EditFile(path("A.txt"), List.of(edit(0, 0, 0, 0, "x")))), EditingErrorKey.FILE_MISSING);
        }

        @Test
        void refusesADirectoryItCannotRead() throws IOException {
            Files.createDirectory(path("dir"));

            assertRefusedAndUntouched(
                    set(new EditFile(path("dir"), List.of(edit(0, 0, 0, 0, "x")))), EditingErrorKey.READ_FAILED);
        }
    }

    @Nested
    class CreatingFiles {

        @Test
        void createsAnEmptyFile() throws IOException {
            ChangeReport report = applier.apply(set(new CreateFile(path("New.java"))));

            assertThat(path("New.java")).exists().isEmptyFile();
            assertThat(report.created()).containsExactly(path("New.java"));
            assertThat(report.modified()).isEmpty();
        }

        @Test
        void givesTheNewFileItsTextWithALaterEdit() throws IOException {
            Path file = path("New.java");

            ChangeReport report = applier.apply(
                    set(new CreateFile(file), new EditFile(file, List.of(edit(0, 0, 0, 0, "class New {}\n")))));

            assertThat(file).hasContent("class New {}\n");
            assertThat(report.created()).containsExactly(file);
            assertThat(report.modified()).containsExactly(file);
        }

        @Test
        void refusesAnExistingTargetAndChangesNothing() throws IOException {
            Path existing = file("A.java", "class A {}");

            assertRefusedAndUntouched(
                    set(new CreateFile(path("B.java")), new CreateFile(existing)), EditingErrorKey.FILE_EXISTS);
        }

        @Test
        void refusesADirectoryAsTheTarget() throws IOException {
            Files.createDirectory(path("dir"));

            assertRefusedAndUntouched(set(new CreateFile(path("dir"))), EditingErrorKey.FILE_EXISTS);
        }

        @Test
        void refusesTheSamePathCreatedTwice() throws IOException {
            assertRefusedAndUntouched(
                    set(new CreateFile(path("New.java")), new CreateFile(path("New.java"))),
                    EditingErrorKey.FILE_EXISTS);
        }
    }

    @Nested
    class RenamingFiles {

        @Test
        void movesTheFileWithItsContent() throws IOException {
            Path from = file("A.java", "class A {}");

            ChangeReport report = applier.apply(set(new RenameFile(from, path("B.java"))));

            assertThat(from).doesNotExist();
            assertThat(path("B.java")).hasContent("class A {}");
            assertThat(report.renamed()).containsExactly(new Renamed(from, path("B.java")));
        }

        @Test
        void movesAFileThatIsNotText() throws IOException {
            byte[] bytes = {(byte) 0xFF, (byte) 0xFE, 0};
            Path from = Files.write(path("a.bin"), bytes);

            applier.apply(set(new RenameFile(from, path("b.bin"))));

            assertThat(Files.readAllBytes(path("b.bin"))).isEqualTo(bytes);
        }

        @Test
        void editsTheFileUnderItsNewNameWhenTheEditComesAfterTheRename() throws IOException {
            Path from = file("A.java", "class A {}");
            Path to = path("B.java");

            ChangeReport report =
                    applier.apply(set(new RenameFile(from, to), new EditFile(to, List.of(edit(0, 6, 0, 7, "B")))));

            assertThat(from).doesNotExist();
            assertThat(to).hasContent("class B {}");
            assertThat(report.renamed()).containsExactly(new Renamed(from, to));
            assertThat(report.modified()).containsExactly(to);
        }

        @Test
        void editsTheFileUnderItsOldNameWhenTheEditComesBeforeTheRename() throws IOException {
            Path from = file("A.java", "class A {}");
            Path to = path("B.java");

            applier.apply(set(new EditFile(from, List.of(edit(0, 6, 0, 7, "B"))), new RenameFile(from, to)));

            assertThat(from).doesNotExist();
            assertThat(to).hasContent("class B {}");
        }

        @Test
        void refusesAnEditUnderTheOldNameAfterTheRename() throws IOException {
            Path from = file("A.java", "class A {}");

            assertRefusedAndUntouched(
                    set(new RenameFile(from, path("B.java")), new EditFile(from, List.of(edit(0, 0, 0, 0, "x")))),
                    EditingErrorKey.FILE_MISSING);
        }

        @Test
        void refusesAnExistingTargetAndChangesNothing() throws IOException {
            Path from = file("A.java", "a");
            Path to = file("B.java", "b");

            assertRefusedAndUntouched(set(new RenameFile(from, to)), EditingErrorKey.FILE_EXISTS);
        }

        @Test
        void refusesAMissingSourceAndChangesNothing() throws IOException {
            file("B.java", "b");

            assertRefusedAndUntouched(
                    set(new RenameFile(path("A.java"), path("C.java"))), EditingErrorKey.FILE_MISSING);
        }

        @Test
        void refusesToRenameAFileOntoItself() throws IOException {
            Path from = file("A.java", "a");

            assertRefusedAndUntouched(set(new RenameFile(from, from)), EditingErrorKey.FILE_EXISTS);
        }
    }

    @Nested
    class DeletingFiles {

        @Test
        void removesTheFile() throws IOException {
            Path file = file("A.java", "class A {}");
            file("B.java", "class B {}");

            ChangeReport report = applier.apply(set(new DeleteFile(file)));

            assertThat(file).doesNotExist();
            assertThat(path("B.java")).exists();
            assertThat(report.deleted()).containsExactly(file);
        }

        @Test
        void refusesAMissingFileAndChangesNothing() throws IOException {
            Path file = file("A.java", "a");

            assertRefusedAndUntouched(
                    set(new DeleteFile(file), new DeleteFile(path("B.java"))), EditingErrorKey.FILE_MISSING);
        }

        @Test
        void refusesTheSameFileDeletedTwice() throws IOException {
            Path file = file("A.java", "a");

            assertRefusedAndUntouched(set(new DeleteFile(file), new DeleteFile(file)), EditingErrorKey.FILE_MISSING);
        }

        @Test
        void letsAFileBeCreatedAgainAfterItsDeletion() throws IOException {
            Path file = file("A.java", "class A {}");

            applier.apply(set(new DeleteFile(file), new CreateFile(file)));

            assertThat(file).exists().isEmptyFile();
        }
    }

    @Nested
    class SpellingsOfOnePath {

        @Test
        void refusesAnEditUnderAnotherSpellingOfTheOldNameAfterTheRename() throws IOException {
            Path from = file("A.java", "class A {}");

            assertRefusedAndUntouched(
                    set(
                            new RenameFile(path("./A.java"), path("B.java")),
                            new EditFile(from, List.of(edit(0, 0, 0, 0, "x")))),
                    EditingErrorKey.FILE_MISSING);
        }

        @Test
        void refusesTheSameFileDeletedUnderTwoSpellings() throws IOException {
            Files.createDirectory(path("sub"));
            Path file = file("A.java", "a");

            assertRefusedAndUntouched(
                    set(new DeleteFile(file), new DeleteFile(path("sub/../A.java"))), EditingErrorKey.FILE_MISSING);
        }

        @Test
        void refusesAFileCreatedUnderTwoSpellings() throws IOException {
            assertRefusedAndUntouched(
                    set(new CreateFile(path("New.java")), new CreateFile(path("./New.java"))),
                    EditingErrorKey.FILE_EXISTS);
        }

        @Test
        void editsTheFileUnderItsNewNameWhateverTheSpellingOfTheEdit() throws IOException {
            Path from = file("A.java", "class A {}");

            applier.apply(set(
                    new RenameFile(from, path("B.java")),
                    new EditFile(path("sub/../B.java"), List.of(edit(0, 6, 0, 7, "B")))));

            assertThat(path("B.java")).hasContent("class B {}");
            assertThat(from).doesNotExist();
        }

        @Test
        void listsAFileEditedUnderTwoSpellingsOnce() throws IOException {
            Path file = file("A.java", "class A {}");

            ChangeReport report = applier.dryRun(set(
                    new EditFile(file, List.of(edit(0, 6, 0, 7, "B"))),
                    new EditFile(path("./A.java"), List.of(edit(0, 6, 0, 7, "C")))));

            assertThat(report.modified()).containsExactly(file);
        }

        @Test
        void givesTheSameVerdictInTheDryRun() throws IOException {
            Path from = file("A.java", "class A {}");
            ChangeSet changes = set(
                    new RenameFile(path("./A.java"), path("B.java")),
                    new EditFile(from, List.of(edit(0, 0, 0, 0, "x"))));

            assertThatExceptionOfType(EditingException.class)
                    .isThrownBy(() -> applier.dryRun(changes))
                    .satisfies(exception -> {
                        assertThat(exception.key()).isEqualTo(EditingErrorKey.FILE_MISSING);
                        assertThat(exception.parameters()).isEqualTo(Map.of("file", from.toString()));
                    });
        }

        /** The same file as a path relative to the working directory of the process, which has no root of its own. */
        private Path relative(Path file) {
            return Path.of("").toAbsolutePath().relativize(file);
        }

        @Test
        void refusesTheSameFileDeletedUnderAnAbsoluteAndARelativeSpelling() throws IOException {
            Path file = file("A.java", "a");

            assertRefusedAndUntouched(
                    set(new DeleteFile(file), new DeleteFile(relative(file))), EditingErrorKey.FILE_MISSING);
        }

        @Test
        void reportsTheAbsolutePathOfAFileGivenAsARelativePath() throws IOException {
            Path file = file("A.java", "a");

            ChangeReport report = applier.dryRun(set(new DeleteFile(relative(file))));

            assertThat(report.deleted()).containsExactly(file);
        }

        @Test
        void reportsTheNormalizedPathOfAFile() throws IOException {
            file("A.java", "a");

            ChangeReport report = applier.dryRun(set(new DeleteFile(path("./sub/../A.java"))));

            assertThat(report.deleted()).containsExactly(path("A.java"));
        }
    }

    @Nested
    class MissingFolders {

        @Test
        void refusesACreateInAFolderThatDoesNotExistAndSaysWhich() throws IOException {
            Map<String, String> before = snapshot();
            Path file = path("missing/New.java");

            assertThatExceptionOfType(EditingException.class)
                    .isThrownBy(() -> applier.apply(set(new CreateFile(file))))
                    .satisfies(exception -> {
                        assertThat(exception.key()).isEqualTo(EditingErrorKey.FOLDER_MISSING);
                        assertThat(exception.status()).isEqualTo(ErrorStatus.BAD_REQUEST);
                        assertThat(exception.parameters())
                                .isEqualTo(Map.of(
                                        "file",
                                        file.toString(),
                                        "folder",
                                        path("missing").toString()));
                    });
            assertThat(snapshot()).isEqualTo(before);
        }

        @Test
        void refusesARenameIntoAFolderThatDoesNotExist() throws IOException {
            Path from = file("A.java", "a");

            assertRefusedAndUntouched(
                    set(new RenameFile(from, path("missing/B.java"))), EditingErrorKey.FOLDER_MISSING);
        }

        @Test
        void refusesBeforeWritingTheChangesThatComeFirst() throws IOException {
            Path edited = file("A.java", "a");

            assertRefusedAndUntouched(
                    set(new EditFile(edited, List.of(edit(0, 0, 0, 0, "x"))), new CreateFile(path("missing/B.java"))),
                    EditingErrorKey.FOLDER_MISSING);
        }

        @Test
        void givesTheSameVerdictInTheDryRun() throws IOException {
            Path file = path("missing/New.java");

            assertThatExceptionOfType(EditingException.class)
                    .isThrownBy(() -> applier.dryRun(set(new CreateFile(file))))
                    .satisfies(exception -> {
                        assertThat(exception.key()).isEqualTo(EditingErrorKey.FOLDER_MISSING);
                        assertThat(exception.parameters())
                                .containsEntry("folder", path("missing").toString());
                    });
        }

        @Test
        void refusesACreateUnderAFileThatIsNotAFolder() throws IOException {
            Path file = file("A.java", "a");

            assertRefusedAndUntouched(set(new CreateFile(file.resolve("New.java"))), EditingErrorKey.FOLDER_MISSING);
        }

        @Test
        void refusesARenameUnderAFileThatIsNotAFolderAndSaysWhichInTheDryRunToo() throws IOException {
            Path from = file("A.java", "a");
            Path notAFolder = file("B.java", "b");
            ChangeSet changes = set(new RenameFile(from, notAFolder.resolve("C.java")));

            assertThatExceptionOfType(EditingException.class)
                    .isThrownBy(() -> applier.dryRun(changes))
                    .satisfies(exception -> {
                        assertThat(exception.key()).isEqualTo(EditingErrorKey.FOLDER_MISSING);
                        assertThat(exception.parameters()).containsEntry("folder", notAFolder.toString());
                    });
        }

        @Test
        void createsAFileInALinkToAFolder() throws IOException {
            Files.createDirectory(path("real"));
            Files.createSymbolicLink(path("link"), Path.of("real"));

            applier.apply(set(new CreateFile(path("link/New.java"))));

            assertThat(path("real/New.java")).exists();
        }

        @Test
        void createsAFileInAFolderThatExists() throws IOException {
            Files.createDirectory(path("sub"));

            applier.apply(set(new CreateFile(path("sub/New.java"))));

            assertThat(path("sub/New.java")).exists();
        }
    }

    @Nested
    class Permissions {

        @Test
        void keepsThePermissionsOfAnEditedFile() throws IOException {
            Path file = file("run.sh", "echo a\n");
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rwxr-x---"));

            applier.apply(set(new EditFile(file, List.of(edit(0, 5, 0, 6, "b")))));

            assertThat(file).hasContent("echo b\n");
            assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(file)))
                    .isEqualTo("rwxr-x---");
        }

        @Test
        void editsTheTargetOfALinkAndLeavesTheLink() throws IOException {
            Path target = file("Target.java", "class A {}");
            Path link = Files.createSymbolicLink(path("Link.java"), Path.of("Target.java"));

            applier.apply(set(new EditFile(link, List.of(edit(0, 6, 0, 7, "B")))));

            assertThat(link).isSymbolicLink();
            assertThat(target).hasContent("class B {}");
        }
    }

    @Nested
    class DryRun {

        @Test
        void listsEveryKindOfChangeAndGivesTheUnifiedDiffWithoutWriting() throws IOException {
            Path edited = file("Edited.java", "class A {}\n");
            Path renamed = file("Old.java", "old\n");
            Path deleted = file("Gone.java", "bye\nbye\n");
            Path created = path("New.java");
            Map<String, String> before = snapshot();

            ChangeReport report = applier.dryRun(set(
                    new EditFile(edited, List.of(edit(0, 6, 0, 7, "B"))),
                    new RenameFile(renamed, path("Renamed.java")),
                    new DeleteFile(deleted),
                    new CreateFile(created)));

            assertThat(snapshot()).isEqualTo(before);
            assertThat(report.modified()).containsExactly(edited);
            assertThat(report.renamed()).containsExactly(new Renamed(renamed, path("Renamed.java")));
            assertThat(report.deleted()).containsExactly(deleted);
            assertThat(report.created()).containsExactly(created);
            assertThat(report.diff()).isEqualTo("""
                --- %1$s/Edited.java
                +++ %1$s/Edited.java
                @@ -1 +1 @@
                -class A {}
                +class B {}
                --- %1$s/Old.java
                +++ %1$s/Renamed.java
                --- %1$s/Gone.java
                +++ /dev/null
                @@ -1,2 +0,0 @@
                -bye
                -bye
                --- /dev/null
                +++ %1$s/New.java
                """.formatted(directory));
        }

        @Test
        void givesTheSameReportAsTheApplicationOfTheSet() throws IOException {
            Path edited = file("Edited.java", "class A {}\n");
            ChangeSet changes =
                    set(new EditFile(edited, List.of(edit(0, 6, 0, 7, "B"))), new CreateFile(path("New.java")));

            ChangeReport dryRun = applier.dryRun(changes);
            ChangeReport applied = applier.apply(changes);

            assertThat(applied).isEqualTo(dryRun);
        }

        @Test
        void showsTheDiffOfTheDeletionOfAFileThatIsNotTextWithoutItsContent() throws IOException {
            Path file = Files.write(path("a.bin"), new byte[] {(byte) 0xFF});

            ChangeReport report = applier.dryRun(set(new DeleteFile(file)));

            assertThat(report.diff()).isEqualTo("--- %s/a.bin\n+++ /dev/null\n".formatted(directory));
        }

        @Test
        void refusesTheSameSetsAsApply() throws IOException {
            Map<String, String> before = snapshot();

            assertThatExceptionOfType(EditingException.class)
                    .isThrownBy(() -> applier.dryRun(set(new DeleteFile(path("missing.java")))))
                    .satisfies(exception -> {
                        assertThat(exception.key()).isEqualTo(EditingErrorKey.FILE_MISSING);
                        assertThat(exception.status()).isEqualTo(ErrorStatus.BAD_REQUEST);
                        assertThat(exception.parameters())
                                .isEqualTo(Map.of("file", path("missing.java").toString()));
                    });
            assertThat(snapshot()).isEqualTo(before);
        }
    }
}
