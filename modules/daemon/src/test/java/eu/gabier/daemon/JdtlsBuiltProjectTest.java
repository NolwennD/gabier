package eu.gabier.daemon;

import static eu.gabier.daemon.FakeOnlyReasons.INTERRUPTED_WHILE_WAITING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.gabier.editing.domain.LanguageServerErrorKey;
import eu.gabier.editing.domain.LanguageServerException;
import eu.gabier.editing.fixtures.FakeOnly;
import eu.gabier.testing.domain.BuiltProject;
import eu.gabier.testing.domain.ClassFolder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Range;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@code built} of the jdtls adapter against {@link FakeJdtls}, answering as the real jdtls 1.63.0 did for an Eclipse
 * project, a Maven project and a Gradle project (the answers are those it gave, with the paths of the temporary
 * project; the replay of the contract scenarios against the real server is an integration test).
 */
@ExtendWith(FakeOnlyGuard.class)
class JdtlsBuiltProjectTest {

    private static final Duration LONG = Duration.ofSeconds(30);
    private static final Duration QUIET = Duration.ofMillis(300);
    private static final Duration SETTLE = Duration.ofMillis(100);

    private static final String OUTPUT_PATH = "org.eclipse.jdt.ls.core.outputPath";
    private static final String CLASSPATH_ENTRIES = "org.eclipse.jdt.ls.core.classpathEntries";

    @TempDir
    Path root;

    private final FakeJdtls fake = new FakeJdtls();
    private final List<String> log = new CopyOnWriteArrayList<>();

    @BeforeEach
    void listTheProject() throws Exception {
        root = root.toRealPath();
        fake.listsProjects(root.toUri().toString());
        fake.registersWatchersFor("**/*.java");
    }

    @AfterEach
    void leaveNothingBehind() throws Exception {
        Thread.interrupted();
        fake.close();
        if (fake.data() != null && Files.exists(fake.data())) {
            try (Stream<Path> files = Files.walk(fake.data())) {
                for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(file);
                }
            }
        }
    }

    private JdtlsLanguageServer start() {
        var settings = new JdtlsSettings(
                root, List.of(root), LONG, Duration.ofMillis(20), Duration.ofSeconds(5), JdtlsWorkspace.temporary());
        var server = JdtlsLanguageServer.start(fake, settings, QUIET, SETTLE, log::add);
        fake.initializeParams();

        return server;
    }

    private JdtlsLanguageServer started() throws Exception {
        var server = start();
        server.awaitStarted();

        return server;
    }

    private static Map<String, Object> source(Path path, Path output, Map<String, Object> attributes) {
        return output == null
                ? Map.of("kind", 3.0, "path", path.toString(), "attributes", attributes)
                : Map.of("kind", 3.0, "path", path.toString(), "output", output.toString(), "attributes", attributes);
    }

    private static Map<String, Object> library(String jar, Map<String, Object> attributes) {
        return Map.of("kind", 1.0, "path", jar, "attributes", attributes);
    }

    private static Map<String, Object> settings(Path output, List<Map<String, Object>> entries) {
        return Map.of(
                OUTPUT_PATH,
                output.toString(),
                CLASSPATH_ENTRIES,
                entries,
                "org.eclipse.jdt.ls.core.natureIds",
                List.of("org.eclipse.jdt.core.javanature"));
    }

    private static Map<String, Object> classpaths(Path project, List<String> entries) {
        return Map.of("projectRoot", project.toUri().toString(), "classpaths", entries, "modulepaths", List.of());
    }

    private static org.eclipse.lsp4j.Diagnostic lsp(DiagnosticSeverity severity) {
        return new org.eclipse.lsp4j.Diagnostic(
                new Range(new org.eclipse.lsp4j.Position(2, 4), new org.eclipse.lsp4j.Position(2, 5)),
                "a problem",
                severity,
                "Java");
    }

    /** An Eclipse project: the main sources go to the output path of the project, the tests to a folder of their own. */
    private void eclipse(Path project) {
        Path main = project.resolve("src/main/java");
        Path tests = project.resolve("src/test/java");
        fake.answersForEachProject(
                        "java.project.getSettings",
                        uri -> settings(
                                project.resolve("bin"),
                                List.of(
                                        source(main, null, Map.of()),
                                        source(tests, project.resolve("test-bin"), Map.of("test", "true")))))
                .answersForEachProject(
                        "java.project.getClasspaths",
                        uri -> classpaths(
                                project,
                                List.of(
                                        project.resolve("test-bin").toString(),
                                        project.resolve("bin").toString())));
    }

    @Test
    void givesTheClassFoldersOfAnEclipseProjectWhoseMainSourcesTakeTheOutputPathOfTheProject() throws Exception {
        eclipse(root);

        try (var server = started()) {
            assertThat(server.built())
                    .isEqualTo(new BuiltProject(
                            List.of(
                                    new ClassFolder(root.resolve("src/main/java"), root.resolve("bin")),
                                    new ClassFolder(root.resolve("src/test/java"), root.resolve("test-bin"))),
                            List.of(),
                            List.of()));
        }
    }

    @Test
    void asksJdtlsForTheSettingsAndTheTestClassPathOfEachProjectByItsUri() throws Exception {
        eclipse(root);

        try (var server = started()) {
            server.built();

            assertThat(fake.projectCommandsReceived())
                    .containsExactly(
                            "java.project.getSettings [\"%s\", [\"%s\",\"%s\"]]"
                                    .formatted(root.toUri(), OUTPUT_PATH, CLASSPATH_ENTRIES),
                            "java.project.getClasspaths [\"%s\", {\"scope\":\"test\"}]".formatted(root.toUri()));
        }
    }

    @Test
    void givesTheClassFoldersAndTheJarsOfAMavenProjectTheSourceFoldersOfWhichShareAClassFolder() throws Exception {
        Path target = root.resolve("target");
        var pom = Map.<String, Object>of("maven.pomderived", "true", "optional", "true");
        var pomTest = Map.<String, Object>of("maven.pomderived", "true", "optional", "true", "test", "true");
        String jupiter = "/home/user/.m2/repository/org/junit/jupiter/junit-jupiter/5.11.4/junit-jupiter-5.11.4.jar";
        String opentest = "/home/user/.m2/repository/org/opentest4j/opentest4j/1.3.0/opentest4j-1.3.0.jar";
        fake.answersForEachProject(
                        "java.project.getSettings",
                        uri -> settings(
                                target.resolve("classes"),
                                List.of(
                                        source(root.resolve("src/main/java"), target.resolve("classes"), pom),
                                        source(root.resolve("src/main/resources"), target.resolve("classes"), pom),
                                        source(root.resolve("src/test/java"), target.resolve("test-classes"), pomTest),
                                        source(
                                                root.resolve("src/test/resources"),
                                                target.resolve("test-classes"),
                                                pomTest),
                                        source(target.resolve("generated-sources/annotations"), null, pom),
                                        library(jupiter, pomTest))))
                .answersForEachProject(
                        "java.project.getClasspaths",
                        uri -> classpaths(
                                root,
                                List.of(
                                        target.resolve("test-classes").toString(),
                                        target.resolve("classes").toString(),
                                        jupiter,
                                        opentest)));

        try (var server = started()) {
            assertThat(server.built())
                    .isEqualTo(new BuiltProject(
                            List.of(
                                    new ClassFolder(root.resolve("src/main/java"), target.resolve("classes")),
                                    new ClassFolder(root.resolve("src/main/resources"), target.resolve("classes")),
                                    new ClassFolder(root.resolve("src/test/java"), target.resolve("test-classes")),
                                    new ClassFolder(root.resolve("src/test/resources"), target.resolve("test-classes")),
                                    new ClassFolder(
                                            target.resolve("generated-sources/annotations"),
                                            target.resolve("classes"))),
                            List.of(Path.of(jupiter), Path.of(opentest)),
                            List.of()));
        }
    }

    @Test
    void givesTheClassFoldersAndTheJarsOfAGradleProjectWhoseClassesAreInBinMainAndBinTest() throws Exception {
        String api = "/home/user/.gradle/caches/modules-2/files-2.1/org.junit.jupiter/junit-jupiter-api/5.11.4/308315/"
                + "junit-jupiter-api-5.11.4.jar";
        fake.answersForEachProject(
                        "java.project.getSettings",
                        uri -> settings(
                                root.resolve("bin/default"),
                                List.of(
                                        source(
                                                root.resolve("src/main/java"),
                                                root.resolve("bin/main"),
                                                Map.of("gradle_scope", "main", "gradle_used_by_scope", "main,test")),
                                        source(
                                                root.resolve("src/test/java"),
                                                root.resolve("bin/test"),
                                                Map.of("test", "true", "gradle_scope", "test")),
                                        library(api, Map.of("test", "true", "gradle_used_by_scope", "test")))))
                .answersForEachProject(
                        "java.project.getClasspaths",
                        uri -> classpaths(
                                root,
                                List.of(
                                        root.resolve("bin/main").toString(),
                                        root.resolve("bin/test").toString(),
                                        api)));

        try (var server = started()) {
            assertThat(server.built())
                    .isEqualTo(new BuiltProject(
                            List.of(
                                    new ClassFolder(root.resolve("src/main/java"), root.resolve("bin/main")),
                                    new ClassFolder(root.resolve("src/test/java"), root.resolve("bin/test"))),
                            List.of(Path.of(api)),
                            List.of()));
        }
    }

    @Test
    void givesTheFoldersOfEveryProjectJdtlsImportedWithTheSharedJarsOnceAndTheClassFoldersOfTheOthersAsNoJar()
            throws Exception {
        Path api = root.resolve("api");
        String shared = "/home/user/.gradle/caches/shared.jar";
        Files.createDirectories(api);
        fake.listsOnlyProjects(root.toUri().toString(), api.toUri().toString());
        fake.answersForEachProject(
                "java.project.getSettings",
                uri -> uri.equals(api.toUri().toString())
                        ? settings(
                                api.resolve("bin/default"),
                                List.of(source(api.resolve("src/main/java"), api.resolve("bin/main"), Map.of())))
                        : settings(
                                root.resolve("bin/default"),
                                List.of(source(root.resolve("src/main/java"), root.resolve("bin/main"), Map.of()))));
        fake.answersForEachProject(
                "java.project.getClasspaths",
                uri -> uri.equals(api.toUri().toString())
                        ? classpaths(api, List.of(api.resolve("bin/main").toString(), shared))
                        : classpaths(
                                root,
                                List.of(
                                        root.resolve("bin/main").toString(),
                                        api.resolve("bin/main").toString(),
                                        shared)));

        try (var server = started()) {
            assertThat(server.built())
                    .isEqualTo(new BuiltProject(
                            List.of(
                                    new ClassFolder(root.resolve("src/main/java"), root.resolve("bin/main")),
                                    new ClassFolder(api.resolve("src/main/java"), api.resolve("bin/main"))),
                            List.of(Path.of(shared)),
                            List.of()));
        }
    }

    @Test
    void namesTheJavaFilesOfTheProjectJdtlsPublishedAnErrorForAndNoOtherFile() throws Exception {
        eclipse(root);
        Path broken = root.resolve("src/main/java/demo/Broken.java");
        Path brokenTest = root.resolve("src/test/java/demo/BrokenTest.java");
        Path warned = root.resolve("src/main/java/demo/Warned.java");
        Path both = root.resolve("src/main/java/demo/Both.java");
        fake.onBuild(() -> {
            fake.publishes(brokenTest, List.of(lsp(DiagnosticSeverity.Error)));
            fake.publishes(broken, List.of(lsp(DiagnosticSeverity.Error)));
            fake.publishes(warned, List.of(lsp(DiagnosticSeverity.Warning), lsp(DiagnosticSeverity.Information)));
            fake.publishes(both, List.of(lsp(DiagnosticSeverity.Warning), lsp(DiagnosticSeverity.Error)));
            fake.publishes(root.resolve("src/main/java/demo/Fixed.java"), List.of());
            fake.publishes(root.resolve("pom.xml"), List.of(lsp(DiagnosticSeverity.Error)));
            fake.publishes(Path.of("/elsewhere/src/Shared.java"), List.of(lsp(DiagnosticSeverity.Error)));
        });

        try (var server = started()) {
            assertThat(server.built().filesWithErrors()).containsExactly(both, broken, brokenTest);
        }
    }

    @Test
    void readsTheBuildOnceJdtlsHasBuiltTheFilesThatChangedOnDisk() throws Exception {
        eclipse(root);
        Path main = root.resolve("src/main/java/demo/Main.java");
        Files.createDirectories(main.getParent());
        Files.writeString(main, "class Main {}\n");

        try (var server = started()) {
            Files.writeString(main, "class Main { int edited; }\n");
            fake.onBuild(() -> fake.publishes(main, List.of(lsp(DiagnosticSeverity.Error))));
            int before = fake.received().size();

            assertThat(server.built().filesWithErrors()).containsExactly(main);

            assertThat(fake.received().subList(before, fake.received().size()))
                    .containsExactly("didChangeWatchedFiles", "acknowledged .gabier-changes-taken-1", "buildWorkspace");
        }
    }

    @Test
    void refusesAsNotReadyWhileJdtlsIsStillImportingAndAsksItNothing() throws Exception {
        fake.neverAnswersInitialize();

        try (var server = start()) {
            assertThatThrownBy(server::built)
                    .isInstanceOfSatisfying(
                            LanguageServerException.class,
                            refusal -> assertThat(refusal.key()).isEqualTo(LanguageServerErrorKey.NOT_READY));
            assertThat(fake.buildRequests()).isEmpty();
            assertThat(fake.projectCommandsReceived()).isEmpty();
        }
    }

    @Test
    void refusesAnAnswerThatIsNotTheSettingsOfAProject() throws Exception {
        fake.answersForEachProject("java.project.getSettings", uri -> "nothing");

        try (var server = started()) {
            assertThatThrownBy(server::built)
                    .isInstanceOf(JdtlsException.class)
                    .hasMessageContaining("java.project.getSettings answered nothing");
        }
    }

    @Test
    void refusesAnAnswerThatIsNotTheClassPathOfAProject() throws Exception {
        eclipse(root);
        fake.answersForEachProject("java.project.getClasspaths", uri -> "nothing");

        try (var server = started()) {
            assertThatThrownBy(server::built)
                    .isInstanceOf(JdtlsException.class)
                    .hasMessageContaining("java.project.getClasspaths answered nothing");
        }
    }

    @FakeOnly(reason = INTERRUPTED_WHILE_WAITING)
    @Test
    void keepsTheInterruptionOfTheThreadThatWaitsForTheBuild() throws Exception {
        eclipse(root);

        try (var server = started()) {
            Thread.currentThread().interrupt();

            assertThatThrownBy(server::built)
                    .isInstanceOf(JdtlsException.class)
                    .hasMessageContaining("Interrupted while waiting for the build of the project");
            assertThat(Thread.interrupted()).isTrue();
        }
    }
}
