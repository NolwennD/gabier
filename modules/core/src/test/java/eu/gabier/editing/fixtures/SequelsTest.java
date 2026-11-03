package eu.gabier.editing.fixtures;

import static org.assertj.core.api.Assertions.assertThat;

import eu.gabier.editing.domain.ChangeApplier;
import eu.gabier.editing.domain.Diagnostic;
import eu.gabier.editing.domain.Position;
import eu.gabier.editing.fixtures.Scenario.Answer;
import eu.gabier.editing.fixtures.Scenario.Request;
import eu.gabier.editing.infrastructure.secondary.NioFileStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The sequels are data that adapters replay: the second scenario must start where the first one leaves the disk. */
class SequelsTest {

    private static final List<Sequel> ALL = Scenarios.sequels();

    private static Map<Path, String> filesUnder(Path root) throws IOException {
        Map<Path, String> files = new HashMap<>();
        try (Stream<Path> written = Files.walk(root)) {
            for (Path file : written.filter(Files::isRegularFile).toList()) {
                files.put(file, Files.readString(file));
            }
        }

        return files;
    }

    private static Sequel named(String name) {
        return ALL.stream()
                .filter(sequel -> sequel.name().equals(name))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void holdsTheRenameAfterARenameAndTheDiagnosticsAfterAnEdit() {
        assertThat(ALL.stream().map(Sequel::name))
                .containsExactly(
                        "rename-after-a-rename",
                        "diagnostics-after-an-edit",
                        "rename-a-package-then-the-same-package-of-the-tests");
    }

    @ParameterizedTest
    @ValueSource(strings = {"rename-after-a-rename", "rename-a-package-then-the-same-package-of-the-tests"})
    void startsTheSecondRenameWhereTheChangeSetOfTheFirstLeavesTheDisk(String name, @TempDir Path directory)
            throws IOException {
        Sequel sequel = named(name);
        Scenario first = sequel.first().inside(directory);
        first.writeProject(directory);

        new ChangeApplier(new NioFileStore()).apply(((Answer.Renamed) first.answer()).changes());

        assertThat(filesUnder(directory))
                .isEqualTo(sequel.second().inside(directory).files());
    }

    @Test
    void renamesBackTheTypeTheFirstRenamed() {
        Sequel sequel = named("rename-after-a-rename");
        Request.Rename first = (Request.Rename) sequel.first().request();
        Request.Rename second = (Request.Rename) sequel.second().request();
        String lineOfTheType = sequel.second()
                .files()
                .get(second.file())
                .split("\n", -1)[second.position().line()];

        assertThat(lineOfTheType.substring(second.position().column())).startsWith(first.newName());
        assertThat(second.newName()).isEqualTo("Greeter");
        assertThat(second.file()).isNotEqualTo(first.file());
    }

    @Test
    void editsOnlyTheFileAskedAboutBetweenTheTwoDiagnostics() {
        Sequel sequel = named("diagnostics-after-an-edit");
        Path file = sequel.first().request().file();

        assertThat(sequel.second().request()).isEqualTo(sequel.first().request());
        assertThat(sequel.second().files().keySet())
                .isEqualTo(sequel.first().files().keySet());
        sequel.first().files().forEach((path, text) -> {
            if (path.equals(file)) {
                assertThat(sequel.second().files().get(path)).isNotEqualTo(text);
            } else {
                assertThat(sequel.second().files()).containsEntry(path, text);
            }
        });
    }

    @Test
    void answersTheSecondDiagnosticsForTheNewTextAndNotForTheOldOne() {
        Sequel sequel = named("diagnostics-after-an-edit");
        List<Diagnostic> before = ((Answer.Diagnosed) sequel.first().answer()).diagnostics();
        List<Diagnostic> after = ((Answer.Diagnosed) sequel.second().answer()).diagnostics();
        Position at = after.getFirst().position();

        assertThat(after).hasSize(1).doesNotContainAnyElementsOf(before);
        assertThat(at).isEqualTo(before.getFirst().position());
        assertThat(sequel.second()
                        .files()
                        .get(sequel.second().request().file())
                        .split("\n", -1)[at.line()]
                        .substring(at.column()))
                .startsWith("true");
    }
}
