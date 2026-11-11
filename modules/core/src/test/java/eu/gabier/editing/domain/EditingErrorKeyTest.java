package eu.gabier.editing.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class EditingErrorKeyTest {

    @Test
    void givesTheKeyThatReportsAreMappedFrom() {
        assertThat(Arrays.stream(EditingErrorKey.values()).map(EditingErrorKey::get))
                .containsExactly(
                        "editing-ambiguous-code-action",
                        "editing-file-exists",
                        "editing-file-missing",
                        "editing-folder-missing",
                        "editing-invalid-range",
                        "editing-no-such-code-action",
                        "editing-not-a-package-info",
                        "editing-not-a-package-name",
                        "editing-not-a-type-name",
                        "editing-overlapping-edits",
                        "editing-read-failed",
                        "editing-rollback-incomplete",
                        "editing-stale-edit",
                        "editing-token-not-found",
                        "editing-unknown-clean-up",
                        "editing-unsupported-encoding",
                        "editing-write-failed");
    }
}
