package fr.craft.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class LineReaderTest {

    private static LineReader reader(String text) {
        return new LineReader(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void readsOneLineAfterTheOther() throws IOException {
        var reader = reader("first\nsecond\n");

        assertThat(reader.readLine()).isEqualTo("first");
        assertThat(reader.readLine()).isEqualTo("second");
        assertThat(reader.readLine()).isNull();
    }

    @Test
    void readsAnEmptyLine() throws IOException {
        assertThat(reader("\n").readLine()).isEmpty();
    }

    @Test
    void dropsTheCarriageReturnOfAWindowsLineBreak() throws IOException {
        assertThat(reader("line\r\n").readLine()).isEqualTo("line");
    }

    @Test
    void decodesUtf8() throws IOException {
        assertThat(reader("é😀\n").readLine()).isEqualTo("é😀");
    }

    @Test
    void endsWithoutAnythingOnAnEmptyStream() throws IOException {
        assertThat(reader("").readLine()).isNull();
    }

    @Test
    void ignoresALineThatTheClientDidNotFinish() throws IOException {
        var reader = reader("done\nhalf a requ");

        assertThat(reader.readLine()).isEqualTo("done");
        assertThat(reader.readLine()).isNull();
    }
}
