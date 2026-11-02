package eu.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.gabier.shared.error.domain.ErrorStatus;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Nested;
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

    @Nested
    class Limit {

        private static final int LIMIT = 8;

        private LineReader limited(String text) {
            return new LineReader(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)), LIMIT);
        }

        @Test
        void readsALineOfExactlyTheLimit() throws IOException {
            assertThat(limited("12345678\nnext\n").readLine()).isEqualTo("12345678");
        }

        @Test
        void refusesALineOneByteOverTheLimit() {
            var reader = limited("123456789\n");

            assertThatThrownBy(reader::readLine).isInstanceOfSatisfying(LineTooLongException.class, refusal -> {
                assertThat(refusal.key()).isEqualTo(DaemonErrorKey.LINE_TOO_LONG);
                assertThat(refusal.status()).isEqualTo(ErrorStatus.BAD_REQUEST);
                assertThat(refusal.getMessage()).contains("8 bytes");
            });
        }

        @Test
        void countsBytesNotCharacters() throws IOException {
            assertThat(limited("éééé\n").readLine()).isEqualTo("éééé");
            assertThatThrownBy(limited("ééééa\n")::readLine).isInstanceOf(LineTooLongException.class);
        }

        @Test
        void skipsTheRestOfARefusedLineAndReadsTheNextOne() throws IOException {
            var reader = limited("123456789012345\nnext\n");

            assertThatThrownBy(reader::readLine).isInstanceOf(LineTooLongException.class);

            assertThat(reader.readLine()).isEqualTo("next");
            assertThat(reader.readLine()).isNull();
        }

        @Test
        void skipsARefusedLineWhoseEndWasTheNextByte() throws IOException {
            var reader = limited("123456789\nnext\n");

            assertThatThrownBy(reader::readLine).isInstanceOf(LineTooLongException.class);

            assertThat(reader.readLine()).isEqualTo("next");
        }

        @Test
        void endsWhenTheClientLeavesInTheMiddleOfARefusedLine() throws IOException {
            var reader = limited("1234567890123");

            assertThatThrownBy(reader::readLine).isInstanceOf(LineTooLongException.class);

            assertThat(reader.readLine()).isNull();
        }

        @Test
        void neverReadsMoreThanOneByteOverTheLimitBeforeRefusing() {
            var read = new AtomicLong();
            var endless = new InputStream() {
                @Override
                public int read() {
                    read.incrementAndGet();

                    return 'x';
                }
            };
            var reader = new LineReader(endless, LIMIT);

            assertThatThrownBy(reader::readLine).isInstanceOf(LineTooLongException.class);

            // The reader stops on its own; the stream below it hands out one buffer of 8192 bytes at most.
            assertThat(read.get()).isLessThanOrEqualTo(8192);
        }
    }
}
