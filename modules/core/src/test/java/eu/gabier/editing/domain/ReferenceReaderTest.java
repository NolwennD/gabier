package eu.gabier.editing.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import eu.gabier.editing.infrastructure.secondary.NioFileStore;
import eu.gabier.shared.error.domain.MissingMandatoryValueException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReferenceReaderTest {

    @TempDir
    Path directory;

    private ReferenceReader reader;

    @BeforeEach
    void createReader() {
        reader = new ReferenceReader(new NioFileStore());
    }

    private Path file(String name, String content) throws IOException {
        return Files.writeString(directory.resolve(name), content);
    }

    private void assertRefused(List<Location> locations, EditingErrorKey key) {
        assertThatExceptionOfType(EditingException.class)
                .isThrownBy(() -> reader.read(locations))
                .satisfies(exception -> assertThat(exception.key()).isEqualTo(key));
    }

    @Test
    void givesEachLocationItsLineOfSourceInTheOrderOfTheLocations() throws IOException {
        Path a = file("A.java", "class A {\n    int count;\n}\n");
        Path b = file("B.java", "class B {\n    A a = new A();\n}\n");

        var references = reader.read(List.of(
                new Location(b, new Position(1, 14)),
                new Location(a, new Position(1, 8)),
                new Location(b, new Position(1, 4))));

        assertThat(references)
                .containsExactly(
                        new Reference(b, new Position(1, 14), "    A a = new A();"),
                        new Reference(a, new Position(1, 8), "    int count;"),
                        new Reference(b, new Position(1, 4), "    A a = new A();"));
    }

    @Test
    void givesTheLineWithItsIndentationAndWithoutItsTerminator() throws IOException {
        Path a = file("A.java", "class A {\r\n\tint count;\r\n}\r\n");

        assertThat(reader.read(List.of(new Location(a, new Position(1, 5)))))
                .containsExactly(new Reference(a, new Position(1, 5), "\tint count;"));
    }

    @Test
    void givesTheLastLineOfAFileWithoutTerminator() throws IOException {
        Path a = file("A.java", "class A {}");

        assertThat(reader.read(List.of(new Location(a, new Position(0, 6)))))
                .containsExactly(new Reference(a, new Position(0, 6), "class A {}"));
    }

    @Test
    void doesNotCountTheByteOrderMarkInTheLine() throws IOException {
        Path a = directory.resolve("Bom.java");
        Files.write(a, "﻿int count;".getBytes(StandardCharsets.UTF_8));

        assertThat(reader.read(List.of(new Location(a, new Position(0, 4)))))
                .containsExactly(new Reference(a, new Position(0, 4), "int count;"));
    }

    @Test
    void givesNothingForNoLocation() {
        assertThat(reader.read(List.of())).isEmpty();
    }

    @Test
    void refusesALineThatTheFileDoesNotHaveAnyMore() throws IOException {
        Path a = file("A.java", "class A {}\n");

        assertThatExceptionOfType(EditingException.class)
                .isThrownBy(() -> reader.read(List.of(new Location(a, new Position(5, 0)))))
                .satisfies(exception -> {
                    assertThat(exception.key()).isEqualTo(EditingErrorKey.STALE_EDIT);
                    assertThat(exception.parameters())
                            .isEqualTo(Map.of("file", a.toString(), "line", "5", "column", "0"));
                });
    }

    @Test
    void refusesAColumnPastTheEndOfTheLine() throws IOException {
        Path a = file("A.java", "class A {}\n");

        assertRefused(List.of(new Location(a, new Position(0, 11))), EditingErrorKey.STALE_EDIT);
    }

    @Test
    void refusesAMissingFile() {
        assertRefused(
                List.of(new Location(directory.resolve("Missing.java"), new Position(0, 0))),
                EditingErrorKey.FILE_MISSING);
    }

    @Test
    void refusesAFileItCannotRead() {
        assertRefused(List.of(new Location(directory, new Position(0, 0))), EditingErrorKey.READ_FAILED);
    }

    @Test
    void refusesAFileThatIsNotUtf8() throws IOException {
        Path latin1 = directory.resolve("Latin1.java");
        Files.write(latin1, new byte[] {'a', ' ', (byte) 0xE9});

        assertRefused(List.of(new Location(latin1, new Position(0, 0))), EditingErrorKey.UNSUPPORTED_ENCODING);
    }

    @Test
    void requiresTheStoreAndTheLocations() {
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> new ReferenceReader(null))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("store"));
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> reader.read(null))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("locations"));
    }

    @Test
    void aReferenceRejectsAMissingMember() {
        Path a = Path.of("A.java");
        Position at = new Position(0, 0);

        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> new Reference(null, at, ""))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("file"));
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> new Reference(a, null, ""))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("position"));
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> new Reference(a, at, null))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("source"));
    }
}
