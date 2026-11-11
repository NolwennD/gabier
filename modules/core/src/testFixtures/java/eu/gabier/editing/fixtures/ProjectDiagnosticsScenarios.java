package eu.gabier.editing.fixtures;

import eu.gabier.editing.domain.DiagnosedFile;
import eu.gabier.editing.domain.Diagnostic;
import eu.gabier.editing.domain.Diagnostic.Severity;
import eu.gabier.editing.domain.LanguageServerErrorKey;
import eu.gabier.editing.domain.Position;
import eu.gabier.editing.fixtures.Scenario.Answer;
import eu.gabier.editing.fixtures.Scenario.Request;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * The contract scenarios of the diagnostics of the whole project, kept apart from {@link Scenarios}: they are part of
 * its catalogue ({@link Scenarios#all()}) and are replayed like the others.
 *
 * <p>The adapter chooses the files from what jdtls knows of the project: the source folders of every project it
 * imported ({@code java.project.listSourcePaths}), the test sources and the other source sets included, and the files
 * {@code .java} under them. It then asks the diagnostics of those files together, as it does for files that are
 * named. The project has a clean class, a class with an error and a class with a warning in a package of its own, in
 * the main sources, and a test class with an error: the answer holds the four files, the clean one with no problem,
 * and the severity and the position of each problem. The text of a message is jdtls's own and is not compared, so the
 * scenario holds none.
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
     * Every Java file of the project, the tests included, in the order of the paths: the clean one answered with no
     * problem, an error in the main sources, a warning in a package, and an error in the test sources.
     */
    public static final Scenario DIAGNOSTICS_OF_THE_WHOLE_PROJECT = new Scenario(
            "diagnostics-of-the-whole-project",
            PROJECT,
            new Request.ProjectDiagnostics(),
            new Answer.ProjectDiagnosed(List.of(
                    new DiagnosedFile(BROKEN, List.of(problem(Severity.ERROR, 4, 15))),
                    new DiagnosedFile(CLEAN, List.of()),
                    new DiagnosedFile(SLOPPY, List.of(problem(Severity.WARNING, 4, 12))),
                    new DiagnosedFile(BROKEN_TEST, List.of(problem(Severity.ERROR, 4, 15))))));

    /** The project is still being imported: the server cannot say which files it has. */
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

    /** A problem at a position; the message is jdtls's own and is not compared, so none is held. */
    private static Diagnostic problem(Severity severity, int line, int column) {
        return new Diagnostic(severity, new Position(line, column), "");
    }
}
