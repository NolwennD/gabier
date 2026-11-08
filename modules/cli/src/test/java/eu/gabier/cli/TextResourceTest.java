package eu.gabier.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class TextResourceTest {

    @Test
    void aResourceThatIsMissingFromTheBuildIsAnErrorThatNamesIt() {
        assertThatThrownBy(() -> TextResource.read("improve.md", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("improve.md");
    }

    @Test
    void aResourceThatCannotBeReadIsAnErrorThatNamesIt() {
        var broken = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("disk");
            }
        };

        assertThatThrownBy(() -> TextResource.read("agents.md", broken))
                .isInstanceOf(UncheckedIOException.class)
                .hasMessageContaining("agents.md");
    }

    @Test
    void theResourceIsReadAsUtf8() {
        var stream = new ByteArrayInputStream("é".getBytes(StandardCharsets.UTF_8));

        assertThat(TextResource.read("improve.md", stream)).isEqualTo("é");
    }
}
