package eu.gabier.editing.fixtures;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.gabier.editing.domain.ChangeSet;
import eu.gabier.editing.domain.EditingException;
import eu.gabier.editing.domain.FileChange.EditFile;
import eu.gabier.editing.domain.Position;
import eu.gabier.editing.domain.Range;
import eu.gabier.editing.domain.TextEdit;
import eu.gabier.editing.fixtures.Scenario.Answer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The effect of a change set is what a replay compares: the sources of the project once it is applied. */
class EffectTest {

    private static final Path GREETER = Path.of("src/main/java/demo/Greeter.java");
    private static final Path MAIN = Path.of("src/main/java/demo/Main.java");

    private static Scenario written(Scenario scenario, Path root) throws IOException {
        Scenario inside = scenario.inside(root);
        inside.writeProject(root);

        return inside;
    }

    private static ChangeSet changesOf(Scenario scenario) {
        return ((Answer.Changing) scenario.answer()).changes();
    }

    @Test
    void holdsTheSourcesOfTheProjectByPathUnderTheRootWithTheEditsApplied(@TempDir Path root) throws IOException {
        Scenario rename = written(Scenarios.RENAME_METHOD, root);

        Map<Path, String> sources = Effect.of(root, changesOf(rename));

        assertThat(sources.keySet()).isEqualTo(Scenarios.RENAME_METHOD.files().keySet());
        assertThat(sources.get(GREETER)).contains("public String welcome(String name)");
        assertThat(sources.get(MAIN)).contains("greeter.welcome(");
    }

    @Test
    void holdsTheSourcesOfEveryProjectOfARootThatHoldsSeveral(@TempDir Path root) throws IOException {
        Scenario move = written(PackageMoveScenarios.MOVE_A_CLASS_THAT_ANOTHER_MODULE_USES, root);

        Map<Path, String> sources = Effect.of(root, changesOf(move));

        assertThat(sources)
                .containsKey(Path.of("shared/src/main/java/demo/shared/money/Money.java"))
                .doesNotContainKey(Path.of("shared/src/main/java/demo/shared/Money.java"));
        assertThat(sources.get(Path.of("app/src/main/java/demo/app/Till.java")))
                .contains("import demo.shared.money.Money;");
    }

    @Test
    void holdsAMovedFileAtItsNewPathOnly(@TempDir Path root) throws IOException {
        Scenario rename = written(Scenarios.RENAME_TYPE, root);

        Map<Path, String> sources = Effect.of(root, changesOf(rename));

        assertThat(sources).containsKey(Path.of("src/main/java/demo/Welcomer.java"));
        assertThat(sources).doesNotContainKey(GREETER);
        assertThat(sources.get(Path.of("src/main/java/demo/Welcomer.java"))).contains("public class Welcomer");
    }

    @Test
    void holdsACreatedFileWithItsText(@TempDir Path root) throws IOException {
        Scenario create = written(Scenarios.CREATE_A_CLASS, root);

        Map<Path, String> sources = Effect.of(root, changesOf(create));

        assertThat(sources.get(Path.of("src/main/java/demo/Widget.java"))).contains("public class Widget");
    }

    @Test
    void isTheSameWhateverEditsWriteIt(@TempDir Path root) throws IOException {
        Scenario rename = written(Scenarios.RENAME_LOCAL_VARIABLE, root);
        ChangeSet merged = changesOf(rename);
        ChangeSet oneEditPerOccurrence = new ChangeSet(List.of(new EditFile(
                root.resolve(GREETER),
                List.of(
                        new TextEdit(new Range(new Position(6, 15), new Position(6, 22)), "text"),
                        new TextEdit(new Range(new Position(7, 15), new Position(7, 22)), "text")))));

        assertThat(merged.changes().getFirst())
                .isNotEqualTo(oneEditPerOccurrence.changes().getFirst());
        assertThat(Effect.of(root, oneEditPerOccurrence)).isEqualTo(Effect.of(root, merged));
    }

    @Test
    void leavesTheProjectOnDiskAsItWas(@TempDir Path root) throws IOException {
        Scenario rename = written(Scenarios.RENAME_TYPE, root);

        Effect.of(root, changesOf(rename));

        assertThat(Files.readString(root.resolve(GREETER)))
                .isEqualTo(Scenarios.RENAME_TYPE.files().get(GREETER));
        assertThat(root.resolve("src/main/java/demo/Welcomer.java")).doesNotExist();
    }

    @Test
    void refusesAChangeSetThatCannotBeApplied(@TempDir Path root) throws IOException {
        Scenario rename = written(Scenarios.RENAME_METHOD, root);
        ChangeSet ofAFileThatIsNotThere = new ChangeSet(List.of(new EditFile(
                root.resolve("src/main/java/demo/Missing.java"),
                List.of(new TextEdit(new Range(new Position(0, 0), new Position(0, 1)), "x")))));

        assertThatThrownBy(() -> Effect.of(root, ofAFileThatIsNotThere)).isInstanceOf(EditingException.class);
        assertThat(rename.files()).isNotEmpty();
    }
}
