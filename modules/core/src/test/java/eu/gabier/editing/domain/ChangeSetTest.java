package eu.gabier.editing.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import eu.gabier.editing.domain.FileChange.CreateFile;
import eu.gabier.editing.domain.FileChange.DeleteFile;
import eu.gabier.shared.error.domain.MissingMandatoryValueException;
import eu.gabier.shared.error.domain.NullElementInCollectionException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChangeSetTest {

    private static final FileChange CREATE = new CreateFile(Path.of("/project/A.java"));
    private static final FileChange DELETE = new DeleteFile(Path.of("/project/B.java"));

    @Test
    void rejectsMissingChanges() {
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> new ChangeSet(null))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("changes"));
    }

    @Test
    void rejectsAnEmptySet() {
        assertThatExceptionOfType(MissingMandatoryValueException.class)
                .isThrownBy(() -> new ChangeSet(List.of()))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("changes"));
    }

    @Test
    void rejectsANullChange() {
        List<FileChange> changes = Arrays.asList(CREATE, null);

        assertThatExceptionOfType(NullElementInCollectionException.class)
                .isThrownBy(() -> new ChangeSet(changes))
                .satisfies(exception -> assertThat(exception.field()).isEqualTo("changes"));
    }

    @Test
    void keepsACopyOfTheChangesInTheirOrder() {
        List<FileChange> changes = new ArrayList<>(List.of(CREATE, DELETE));

        ChangeSet set = new ChangeSet(changes);
        changes.clear();

        assertThat(set.changes()).containsExactly(CREATE, DELETE);
    }
}
