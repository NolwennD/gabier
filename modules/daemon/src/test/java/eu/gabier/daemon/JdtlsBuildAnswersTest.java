package eu.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.gabier.testing.domain.ClassFolder;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** The reading of what jdtls answers about the build of a project, on the values it gave and on those it never does. */
class JdtlsBuildAnswersTest {

    private static final String OUTPUT_PATH = "org.eclipse.jdt.ls.core.outputPath";
    private static final String ENTRIES = "org.eclipse.jdt.ls.core.classpathEntries";

    private static Map<String, Object> settings(Object entries) {
        return Map.of(OUTPUT_PATH, "/p/bin", ENTRIES, entries);
    }

    @Nested
    class ClassFolders {

        @Test
        void takesTheOutputOfTheEntryWhenItHasOneAndTheOutputPathOfTheProjectWhenItHasNone() {
            var answer = settings(List.of(
                    Map.of("kind", 3.0, "path", "/p/src/main/java", "attributes", Map.of()),
                    Map.of("kind", 3.0, "path", "/p/src/test/java", "output", "/p/test-bin", "attributes", Map.of())));

            assertThat(JdtlsBuildAnswers.classFolders(answer))
                    .containsExactly(
                            new ClassFolder(Path.of("/p/src/main/java"), Path.of("/p/bin")),
                            new ClassFolder(Path.of("/p/src/test/java"), Path.of("/p/test-bin")));
        }

        @Test
        void leavesOutTheEntriesThatAreNotSourceFolders() {
            var answer = settings(List.of(
                    Map.of("kind", 1.0, "path", "/home/user/lib.jar"),
                    Map.of("kind", 2.0, "path", "/other-project"),
                    Map.of("kind", 3.0, "path", "/p/src", "output", "/p/bin")));

            assertThat(JdtlsBuildAnswers.classFolders(answer))
                    .containsExactly(new ClassFolder(Path.of("/p/src"), Path.of("/p/bin")));
        }

        @Test
        void readsAKindThatIsAWholeNumberWhateverItsType() {
            var answer = settings(List.of(Map.of("kind", 3, "path", "/p/src", "output", "/p/bin")));

            assertThat(JdtlsBuildAnswers.classFolders(answer))
                    .containsExactly(new ClassFolder(Path.of("/p/src"), Path.of("/p/bin")));
        }

        @Test
        void keepsAFolderOnceAndTheEntryThatHasNoKindOutOfTheAnswer() {
            var answer = settings(List.of(
                    Map.of("kind", 3.0, "path", "/p/src", "output", "/p/bin"),
                    Map.of("kind", 3.0, "path", "/p/src", "output", "/p/bin"),
                    Map.of("path", "/p/unknown")));

            assertThat(JdtlsBuildAnswers.classFolders(answer))
                    .containsExactly(new ClassFolder(Path.of("/p/src"), Path.of("/p/bin")));
        }

        @Test
        void normalizesThePaths() {
            var answer = settings(List.of(Map.of("kind", 3.0, "path", "/p/x/../src/", "output", "/p/./bin")));

            assertThat(JdtlsBuildAnswers.classFolders(answer))
                    .containsExactly(new ClassFolder(Path.of("/p/src"), Path.of("/p/bin")));
        }

        @Test
        void refusesAnAnswerThatIsNotTheSettingsOfAProject() {
            for (Object answer : new Object[] {"nothing", List.of(), Map.of(OUTPUT_PATH, "/p/bin"), settings("none")}) {
                assertThatThrownBy(() -> JdtlsBuildAnswers.classFolders(answer))
                        .isInstanceOf(JdtlsException.class)
                        .hasMessageContaining("java.project.getSettings answered " + answer);
            }
            assertThatThrownBy(() -> JdtlsBuildAnswers.classFolders(null))
                    .isInstanceOf(JdtlsException.class)
                    .hasMessageContaining("java.project.getSettings answered null");
        }

        @Test
        void refusesAnEntryThatIsNotAnObject() {
            assertThatThrownBy(() -> JdtlsBuildAnswers.classFolders(settings(List.of("/p/src"))))
                    .isInstanceOf(JdtlsException.class)
                    .hasMessageContaining("/p/src, which is not a class path entry");
        }

        @Test
        void refusesASourceFolderOrAnOutputThatIsNotAnAbsolutePath() {
            var relative = settings(List.of(Map.of("kind", 3.0, "path", "src", "output", "/p/bin")));
            var relativeOutput = settings(List.of(Map.of("kind", 3.0, "path", "/p/src", "output", "bin")));
            var notAPath = settings(List.of(Map.of("kind", 3.0, "path", "/p/src", "output", "a\u0000b")));
            var noPath = settings(List.of(Map.of("kind", 3.0, "output", "/p/bin")));

            for (Object answer : new Object[] {relative, relativeOutput, notAPath, noPath}) {
                assertThatThrownBy(() -> JdtlsBuildAnswers.classFolders(answer))
                        .isInstanceOf(JdtlsException.class)
                        .hasMessageContaining("which is not an absolute path");
            }
        }

        @Test
        void refusesASourceFolderThatHasNoOutputWhenTheProjectHasNoOutputPathEither() {
            var answer = Map.of(ENTRIES, List.of(Map.of("kind", 3.0, "path", "/p/src")));

            assertThatThrownBy(() -> JdtlsBuildAnswers.classFolders(answer))
                    .isInstanceOf(JdtlsException.class)
                    .hasMessageContaining("jdtls gave null")
                    .hasMessageContaining("which is not an absolute path");
        }
    }

    @Nested
    class Classpath {

        @Test
        void givesTheEntriesInTheOrderOfTheClassPath() {
            var answer = Map.of(
                    "projectRoot",
                    "file:/p/",
                    "classpaths",
                    List.of("/p/test-bin", "/p/bin", "/home/user/a.jar"),
                    "modulepaths",
                    List.of());

            assertThat(JdtlsBuildAnswers.classpath(answer))
                    .containsExactly(Path.of("/p/test-bin"), Path.of("/p/bin"), Path.of("/home/user/a.jar"));
        }

        @Test
        void givesNoEntryForAnEmptyClassPath() {
            assertThat(JdtlsBuildAnswers.classpath(Map.of("classpaths", List.of())))
                    .isEmpty();
        }

        @Test
        void refusesAnAnswerThatIsNotAListOfClasspaths() {
            for (Object answer : new Object[] {"nothing", List.of(), Map.of("classpaths", "none"), Map.of()}) {
                assertThatThrownBy(() -> JdtlsBuildAnswers.classpath(answer))
                        .isInstanceOf(JdtlsException.class)
                        .hasMessageContaining("java.project.getClasspaths answered " + answer);
            }
            assertThatThrownBy(() -> JdtlsBuildAnswers.classpath(null))
                    .isInstanceOf(JdtlsException.class)
                    .hasMessageContaining("java.project.getClasspaths answered null");
        }

        @Test
        void refusesAnEntryThatIsNotAnAbsolutePath() {
            for (Object entry : new Object[] {"lib/a.jar", 7}) {
                assertThatThrownBy(() -> JdtlsBuildAnswers.classpath(Map.of("classpaths", List.of(entry))))
                        .isInstanceOf(JdtlsException.class)
                        .hasMessageContaining("jdtls gave " + entry);
            }
        }
    }
}
