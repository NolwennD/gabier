package eu.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class OutputRelayTest {

    @Test
    void givesEachLineOfTheStreamToTheConsumerUntilTheStreamEnds() {
        var lines = new ArrayList<String>();
        var stream = new ByteArrayInputStream("first\nsecond é\n\nlast".getBytes(StandardCharsets.UTF_8));

        OutputRelay.relay(stream, lines::add);

        assertThat(lines).containsExactly("first", "second é", "", "last");
    }

    @Test
    void endsWithoutFailingWhenTheStreamIsClosedWhileItIsRead() {
        var lines = new ArrayList<String>();
        InputStream closed = new InputStream() {
            private boolean first = true;

            @Override
            public int read() throws IOException {
                if (first) {
                    first = false;
                    return 'a';
                }
                throw new IOException("Stream closed");
            }
        };

        OutputRelay.relay(closed, lines::add);

        assertThat(lines).isEmpty();
    }
}
