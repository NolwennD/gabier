package eu.gabier.editing.fixtures;

import eu.gabier.editing.domain.DiagnosedFile;
import eu.gabier.editing.domain.Diagnostic;
import eu.gabier.editing.domain.Diagnostic.Severity;
import eu.gabier.editing.domain.LanguageServerErrorKey;
import eu.gabier.editing.domain.Position;
import eu.gabier.editing.fixtures.Scenario.Answer;
import eu.gabier.editing.fixtures.Scenario.Request;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The contract scenarios of the diagnostics of the whole project, kept apart from {@link Scenarios}: they are part of
 * its catalogue ({@link Scenarios#all()}) and are replayed like the others.
 *
 * <p>The adapter answers from what jdtls publishes for the project once it has built it, the files that are not open
 * included, with no file opened and none walked: it asks for the build and takes what was published. The project has a
 * clean class, a class with an error and a class with a warning in a package of its own, in the main sources, and a test
 * class with an error: the answer holds the three files that have a problem, in the order of the paths, with the
 * severity and the position of each problem, and not the clean one. The text of a message is jdtls's own and is not
 * compared, so the scenario holds none.
 */
public final class ProjectDiagnosticsScenarios {

    private static final Path CLEAN = Path.of("src/main/java/demo/Clean.java");
    private static final Path BROKEN = Path.of("src/main/java/demo/Broken.java");
    private static final Path SLOPPY = Path.of("src/main/java/demo/shop/Sloppy.java");
    private static final Path BROKEN_TEST = Path.of("src/test/java/demo/BrokenTest.java");

    private static final Map<Path, String> PROJECT = Map.of(
            CLEAN, """
            package demo;

            public class Clean {
                public int one() {
                    return 1;
                }
            }
            """,
            BROKEN, """
            package demo;

            public class Broken {
                public int answer() {
                    return "forty-two";
                }
            }
            """,
            SLOPPY, """
            package demo.shop;

            public class Sloppy {
                public void run() {
                    int unused = 42;
                }
            }
            """,
            BROKEN_TEST, """
            package demo;

            public class BrokenTest {
                String text() {
                    return 42;
                }
            }
            """);

    /**
     * Every Java file of the project that has a problem, the tests included, in the order of the paths: an error in the
     * main sources, a warning in a package, and an error in the test sources; the clean class is not listed.
     */
    public static final Scenario DIAGNOSTICS_OF_THE_WHOLE_PROJECT = new Scenario(
            "diagnostics-of-the-whole-project",
            PROJECT,
            new Request.ProjectDiagnostics(),
            new Answer.ProjectDiagnosed(List.of(
                    new DiagnosedFile(BROKEN, List.of(problem(Severity.ERROR, 4, 15))),
                    new DiagnosedFile(SLOPPY, List.of(problem(Severity.WARNING, 4, 12))),
                    new DiagnosedFile(BROKEN_TEST, List.of(problem(Severity.ERROR, 4, 15))))));

    /**
     * The files of {@link #PROJECT} after an agent fixed {@code Broken} and broke {@code Clean} on disk: the error of the
     * first is gone and the second has one, the warning and the error of the tests are as they were.
     */
    public static final Scenario DIAGNOSTICS_OF_THE_WHOLE_PROJECT_AFTER_TWO_EDITS = new Scenario(
            "diagnostics-of-the-whole-project-after-two-edits",
            edited(),
            new Request.ProjectDiagnostics(),
            new Answer.ProjectDiagnosed(List.of(
                    new DiagnosedFile(CLEAN, List.of(problem(Severity.ERROR, 4, 15))),
                    new DiagnosedFile(SLOPPY, List.of(problem(Severity.WARNING, 4, 12))),
                    new DiagnosedFile(BROKEN_TEST, List.of(problem(Severity.ERROR, 4, 15))))));

    /**
     * An error fixed and another one made on disk between two requests: jdtls is told of the edits, builds, and the
     * answer is the one of the new text, not what it published before.
     */
    public static final Sequel DIAGNOSTICS_OF_THE_WHOLE_PROJECT_AFTER_EDITS = new Sequel(
            "diagnostics-of-the-whole-project-after-edits",
            DIAGNOSTICS_OF_THE_WHOLE_PROJECT,
            DIAGNOSTICS_OF_THE_WHOLE_PROJECT_AFTER_TWO_EDITS);

    /** The project is still being imported: the server has not built it, so it cannot say which files have a problem. */
    public static final Scenario DIAGNOSTICS_OF_THE_WHOLE_PROJECT_WHILE_THE_SERVER_IMPORTS = new Scenario(
            "diagnostics-of-the-whole-project-while-the-server-imports",
            PROJECT,
            new Request.ProjectDiagnostics(),
            new Answer.Refused(LanguageServerErrorKey.NOT_READY));

    private ProjectDiagnosticsScenarios() {}

    /** Every scenario of the diagnostics of the whole project. */
    public static List<Scenario> all() {
        return List.of(DIAGNOSTICS_OF_THE_WHOLE_PROJECT, DIAGNOSTICS_OF_THE_WHOLE_PROJECT_WHILE_THE_SERVER_IMPORTS);
    }

    private static Map<Path, String> edited() {
        Map<Path, String> files = new HashMap<>(PROJECT);
        files.put(BROKEN, PROJECT.get(BROKEN).replace("\"forty-two\"", "42"));
        files.put(CLEAN, PROJECT.get(CLEAN).replace("return 1;", "return \"one\";"));

        return files;
    }

    /** A problem at a position; the message is jdtls's own and is not compared, so none is held. */
    private static Diagnostic problem(Severity severity, int line, int column) {
        return new Diagnostic(severity, new Position(line, column), "");
    }
}
