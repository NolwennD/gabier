package eu.gabier.editing.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import eu.gabier.editing.infrastructure.secondary.NioFileStore;
import eu.gabier.shared.error.domain.ErrorStatus;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TokenLocatorTest {

    @TempDir
    Path directory;

    private TokenLocator locator;

    @BeforeEach
    void createLocator() {
        locator = new TokenLocator(new NioFileStore());
    }

    private Path file(String name, String content) throws IOException {
        return Files.writeString(directory.resolve(name), content);
    }

    private void assertRefused(Path file, String token, EditingErrorKey key) {
        assertThatExceptionOfType(EditingException.class)
                .isThrownBy(() -> locator.firstOccurrence(file, token))
                .satisfies(exception -> assertThat(exception.key()).isEqualTo(key));
    }

    @Test
    void locatesTheFirstOccurrenceOfAnIdentifierAsAWholeWord() throws IOException {
        Path file = file("A.java", "class A {\n    String greeting;\n    String greet() {}\n}\n");

        assertThat(locator.firstOccurrence(file, "greet")).isEqualTo(new Position(2, 11));
    }

    @Test
    void doesNotCountTheByteOrderMarkInTheColumn() throws IOException {
        Path file = directory.resolve("Bom.java");
        Files.write(file, "﻿int count;".getBytes(StandardCharsets.UTF_8));

        assertThat(locator.firstOccurrence(file, "int")).isEqualTo(new Position(0, 0));
    }

    @Test
    void refusesAnIdentifierThatIsOnlyPartOfOtherWords() throws IOException {
        Path file = file("A.java", "class Greeter {}");

        assertThatExceptionOfType(EditingException.class)
                .isThrownBy(() -> locator.firstOccurrence(file, "Greet"))
                .satisfies(exception -> {
                    assertThat(exception.key()).isEqualTo(EditingErrorKey.TOKEN_NOT_FOUND);
                    assertThat(exception.status()).isEqualTo(ErrorStatus.BAD_REQUEST);
                    assertThat(exception.parameters()).isEqualTo(Map.of("file", file.toString(), "token", "Greet"));
                    assertThat(exception).hasMessage("\"Greet\" does not occur as a whole word in " + file);
                });
    }

    @Test
    void refusesAMissingFile() {
        assertRefused(directory.resolve("Missing.java"), "a", EditingErrorKey.FILE_MISSING);
    }

    @Test
    void refusesAFileItCannotRead() {
        assertRefused(directory, "a", EditingErrorKey.READ_FAILED);
    }

    @Test
    void refusesAFileThatIsNotUtf8() throws IOException {
        Path file = directory.resolve("Latin1.java");
        Files.write(file, new byte[] {'a', ' ', (byte) 0xE9});

        assertRefused(file, "a", EditingErrorKey.UNSUPPORTED_ENCODING);
    }
}
