package fr.craft.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.craft.gabier.editing.domain.LanguageServerException;
import fr.craft.gabier.editing.domain.Position;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class NoProjectServerTest {

    private final NoProjectServer server = new NoProjectServer();

    @Test
    void declinesTheDiagnosticsAsNotReady() {
        assertThatThrownBy(() -> server.diagnostics(Path.of("/project/A.java")))
                .isInstanceOfSatisfying(
                        LanguageServerException.class,
                        refusal -> assertThat(refusal.key().get()).isEqualTo("language-server-not-ready"));
    }

    @Test
    void declinesARenameAsNotReady() {
        assertThatThrownBy(() -> server.rename(Path.of("/project/A.java"), new Position(0, 0), "B"))
                .isInstanceOfSatisfying(
                        LanguageServerException.class,
                        refusal -> assertThat(refusal.key().get()).isEqualTo("language-server-not-ready"));
    }
}
