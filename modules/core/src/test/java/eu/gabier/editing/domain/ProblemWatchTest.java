package eu.gabier.editing.domain;

import static org.assertj.core.api.Assertions.assertThat;

import eu.gabier.editing.domain.Diagnostic.Severity;
import eu.gabier.editing.fixtures.ProjectDiagnosticsScenarios;
import eu.gabier.editing.fixtures.Scenario;
import eu.gabier.editing.fixtures.Scenario.Answer;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ProblemWatchTest {

    private static final Path ROOT = Path.of("/project");
    private static final Path BROKEN = ROOT.resolve("src/main/java/demo/Broken.java");
    private static final Path CLEAN = ROOT.resolve("src/main/java/demo/Clean.java");
    private static final Path SLOPPY = ROOT.resolve("src/main/java/demo/shop/Sloppy.java");

    /** The project as the first scenario of the sequel answers it, and as the second one does after two edits. */
    private static final List<DiagnosedFile> BEFORE =
            answered(ProjectDiagnosticsScenarios.DIAGNOSTICS_OF_THE_WHOLE_PROJECT_AFTER_EDITS.first());

    private static final List<DiagnosedFile> AFTER =
            answered(ProjectDiagnosticsScenarios.DIAGNOSTICS_OF_THE_WHOLE_PROJECT_AFTER_EDITS.second());

    private static List<DiagnosedFile> answered(Scenario scenario) {
        return ((Answer.ProjectDiagnosed) scenario.inside(ROOT).answer()).files();
    }

    private static DiagnosedFile problem(Path file, Severity severity, int line) {
        return new DiagnosedFile(file, List.of(new Diagnostic(severity, new Position(line, 4), "")));
    }

    @Nested
    class OfTheProject {

        private final ProblemWatch watch = ProblemWatch.ofProject(BEFORE);

        @Test
        void tellsTheFileWhoseErrorWasFixedAsCleanAndTheOneThatBrokeWithItsProblem() {
            var changes = watch.changesIn(AFTER);

            assertThat(changes).extracting(DiagnosedFile::file).containsExactly(BROKEN, CLEAN);
            assertThat(changes.get(0).diagnostics()).isEmpty();
            assertThat(changes.get(1).diagnostics())
                    .extracting(Diagnostic::severity)
                    .containsExactly(Severity.ERROR);
        }

        @Test
        void tellsNothingWhenTheProblemsAreTheOnesItAlreadyTold() {
            assertThat(watch.changesIn(BEFORE)).isEmpty();
        }

        @Test
        void tellsNothingTwiceForTheSameChange() {
            watch.changesIn(AFTER);

            assertThat(watch.changesIn(AFTER)).isEmpty();
        }

        @Test
        void tellsTheWayBackOnceTheChangeWasTold() {
            watch.changesIn(AFTER);

            assertThat(watch.changesIn(BEFORE)).extracting(DiagnosedFile::file).containsExactly(BROKEN, CLEAN);
        }

        @Test
        void tellsAFileWhoseProblemsChangedWithoutBecomingCleanWithItsNewProblems() {
            var changed = List.of(problem(BROKEN, Severity.WARNING, 2), BEFORE.get(1), BEFORE.get(2));

            assertThat(watch.changesIn(changed)).containsExactly(problem(BROKEN, Severity.WARNING, 2));
        }

        @Test
        void tellsEveryFileAsCleanWhenTheProjectHasNoProblemAnyMore() {
            assertThat(watch.changesIn(List.of()))
                    .extracting(DiagnosedFile::file)
                    .containsExactlyElementsOf(
                            BEFORE.stream().map(DiagnosedFile::file).toList());
            assertThat(watch.changesIn(List.of())).isEmpty();
        }
    }

    @Nested
    class OfFiles {

        private final ProblemWatch watch =
                ProblemWatch.ofFiles(List.of(BEFORE.getFirst(), new DiagnosedFile(CLEAN, List.of()), BEFORE.get(1)));

        @Test
        void tellsTheChangesOfTheFilesItWasGivenAndNotThoseOfTheOthers() {
            var changes = watch.changesIn(AFTER);

            assertThat(changes).extracting(DiagnosedFile::file).containsExactly(BROKEN, CLEAN);
        }

        @Test
        void doesNotTellAFileOfTheOthersThatChanged() {
            var watching = ProblemWatch.ofFiles(List.of(BEFORE.get(1), new DiagnosedFile(SLOPPY, List.of())));

            assertThat(watching.changesIn(AFTER))
                    .extracting(DiagnosedFile::file)
                    .containsExactly(SLOPPY);
        }

        @Test
        void takesAGivenFileTheProjectDoesNotListForClean() {
            var watching = ProblemWatch.ofFiles(List.of(new DiagnosedFile(CLEAN, List.of())));

            assertThat(watching.changesIn(BEFORE)).isEmpty();
        }

        @Test
        void tellsAGivenFileThatHadProblemsAsCleanWhenTheProjectNoLongerListsIt() {
            var watching = ProblemWatch.ofFiles(List.of(BEFORE.getFirst()));

            assertThat(watching.changesIn(AFTER)).containsExactly(new DiagnosedFile(BROKEN, List.of()));
        }
    }
}
