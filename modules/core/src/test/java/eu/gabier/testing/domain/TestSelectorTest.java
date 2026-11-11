package eu.gabier.testing.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import eu.gabier.testing.fixtures.CompiledProject;
import eu.gabier.testing.infrastructure.secondary.NioProjectFiles;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TestSelectorTest {

    private static final String GREETER = """
        package app;

        public class Greeter {
            public String greet() {
                return "hello";
            }
        }
        """;

    private static final String GREETER_TEST = """
        package app;

        public class GreeterTest {
            @org.junit.Test
            public void greets() {
                new Greeter().greet();
            }
        }
        """;

    @TempDir
    Path root;

    private BuiltProject built;

    private void project(Map<String, String> main, Map<String, String> tests, Path... filesWithErrors)
            throws IOException {
        built = CompiledProject.of(root, main, tests, List.of(filesWithErrors));
    }

    private Path main(String file) {
        return root.resolve("src/main/java").resolve(file);
    }

    private Path test(String file) {
        return root.resolve("src/test/java").resolve(file);
    }

    private List<String> affectedBy(Path... sources) {
        return new TestSelector(new NioProjectFiles(), root).affectedBy(built, List.of(sources));
    }

    private void refused(TestingErrorKey key, Path... sources) {
        assertThatExceptionOfType(TestingException.class)
                .isThrownBy(() -> affectedBy(sources))
                .satisfies(exception -> assertThat(exception.key()).isEqualTo(key));
    }

    @Test
    void selectsTheTestsThatUseAChangedApplicationFile() throws IOException {
        project(Map.of("app/Greeter.java", GREETER), Map.of("app/GreeterTest.java", GREETER_TEST));

        assertThat(affectedBy(main("app/Greeter.java"))).containsExactly("app.GreeterTest");
    }

    @Test
    void selectsAChangedTestFileItself() throws IOException {
        project(Map.of("app/Greeter.java", GREETER), Map.of("app/GreeterTest.java", GREETER_TEST));

        assertThat(affectedBy(test("app/GreeterTest.java"))).containsExactly("app.GreeterTest");
    }

    @Test
    void takesEveryClassOfAFileTheNestedOnesAndTheOtherTopLevelTypesIncluded() throws IOException {
        project(
                Map.of("app/Greeter.java", """
                    package app;

                    public class Greeter {
                        public static class Inner {}
                    }

                    class Helper {}
                    """),
                Map.of(
                        "app/InnerTest.java", """
                        package app;

                        public class InnerTest {
                            @org.junit.Test
                            public void inner() {
                                new Greeter.Inner();
                            }
                        }
                        """,
                        "app/HelperTest.java", """
                        package app;

                        public class HelperTest {
                            @org.junit.Test
                            public void helper() {
                                new Helper();
                            }
                        }
                        """));

        assertThat(affectedBy(main("app/Greeter.java"))).containsExactly("app.HelperTest", "app.InnerTest");
    }

    @Test
    void tellsTwoFilesOfTheSameNameInDifferentPackagesApart() throws IOException {
        project(
                Map.of(
                        "app/first/Util.java", "package app.first; public class Util {}",
                        "app/second/Util.java", "package app.second; public class Util {}"),
                Map.of(
                        "app/first/FirstTest.java", """
                        package app.first;

                        public class FirstTest {
                            @org.junit.Test
                            public void uses() {
                                new Util();
                            }
                        }
                        """,
                        "app/second/SecondTest.java", """
                        package app.second;

                        public class SecondTest {
                            @org.junit.Test
                            public void uses() {
                                new Util();
                            }
                        }
                        """));

        assertThat(affectedBy(main("app/first/Util.java"))).containsExactly("app.first.FirstTest");
    }

    @Test
    void takesAFileOfTheDefaultPackage() throws IOException {
        project(Map.of("Plain.java", "public class Plain {}"), Map.of("PlainTest.java", """
            public class PlainTest {
                @org.junit.Test
                public void plain() {
                    new Plain();
                }
            }
            """));

        assertThat(affectedBy(main("Plain.java"))).containsExactly("PlainTest");
    }

    @Test
    void leavesOutTheTestsThatTheFiltersFileOfTheProjectExcludes() throws IOException {
        project(
                Map.of("app/Greeter.java", GREETER),
                Map.of(
                        "app/GreeterTest.java",
                        GREETER_TEST,
                        "app/GreeterIT.java",
                        GREETER_TEST.replace("GreeterTest", "GreeterIT")));
        Files.writeString(root.resolve("infinitest.filters"), "# the slow ones\nexclude .*IT\n");

        assertThat(affectedBy(main("app/Greeter.java"))).containsExactly("app.GreeterTest");
    }

    @Test
    void refusesAFiltersFileWhosePatternIsNoRegularExpression() throws IOException {
        project(Map.of("app/Greeter.java", GREETER), Map.of("app/GreeterTest.java", GREETER_TEST));
        Files.writeString(root.resolve("infinitest.filters"), "exclude *IT\n");

        refused(TestingErrorKey.INVALID_TEST_FILTER, main("app/Greeter.java"));
    }

    @Test
    void leavesOutTheFilesThatAreNoJavaSources() throws IOException {
        project(Map.of("app/Greeter.java", GREETER), Map.of("app/GreeterTest.java", GREETER_TEST));
        Path readme = Files.writeString(root.resolve("README.md"), "# Project");

        assertThat(affectedBy(readme)).isEmpty();
        assertThat(affectedBy(readme, main("app/Greeter.java"))).containsExactly("app.GreeterTest");
    }

    @Test
    void refusesAJavaFileThatIsNotUnderASourceFolder() throws IOException {
        project(Map.of("app/Greeter.java", GREETER), Map.of("app/GreeterTest.java", GREETER_TEST));
        Path script = Files.writeString(root.resolve("Script.java"), "class Script {}");

        refused(TestingErrorKey.NOT_IN_A_SOURCE_FOLDER, script);
    }

    @Test
    void refusesAJavaFileThatDoesNotExist() throws IOException {
        project(Map.of("app/Greeter.java", GREETER), Map.of("app/GreeterTest.java", GREETER_TEST));

        refused(TestingErrorKey.FILE_MISSING, main("app/Gone.java"));
    }

    @Test
    void refusesAFileTheBuildFoundAnErrorInNamingIt() throws IOException {
        project(
                Map.of("app/Greeter.java", GREETER),
                Map.of("app/GreeterTest.java", GREETER_TEST),
                main("app/Greeter.java"));

        assertThatExceptionOfType(TestingException.class)
                .isThrownBy(() -> affectedBy(test("app/GreeterTest.java"), main("app/Greeter.java")))
                .satisfies(exception -> {
                    assertThat(exception.key()).isEqualTo(TestingErrorKey.COMPILATION_FAILED);
                    assertThat(exception.parameters())
                            .containsEntry("files", main("app/Greeter.java").toString());
                });
    }

    @Test
    void cleansADotAndADoubleDotSegmentOfAPathBeforeLookingForItsSourceFolder() throws IOException {
        project(Map.of("app/Greeter.java", GREETER), Map.of("app/GreeterTest.java", GREETER_TEST));

        assertThat(affectedBy(root.resolve("./src/main/java/app/Greeter.java"))).containsExactly("app.GreeterTest");
        assertThat(affectedBy(root.resolve("src/test/java/other/../app/GreeterTest.java")))
                .containsExactly("app.GreeterTest");
    }

    @Test
    void refusesAFileWithAnErrorGivenWithADotOrADoubleDotSegmentInItsPath() throws IOException {
        project(
                Map.of("app/Greeter.java", GREETER),
                Map.of("app/GreeterTest.java", GREETER_TEST),
                main("app/Greeter.java"));

        for (Path given : List.of(
                root.resolve("src/main/java/./app/Greeter.java"),
                root.resolve("src/main/java/other/../app/Greeter.java"),
                root.resolve("src/../src/main/java/app/Greeter.java"))) {
            assertThatExceptionOfType(TestingException.class)
                    .isThrownBy(() -> affectedBy(given))
                    .satisfies(exception -> {
                        assertThat(exception.key()).isEqualTo(TestingErrorKey.COMPILATION_FAILED);
                        assertThat(exception.parameters())
                                .containsEntry("files", main("app/Greeter.java").toString());
                    });
        }
    }

    @Test
    void doesNotRefuseBecauseOfAnErrorInAFileThatWasNotGiven() throws IOException {
        project(
                Map.of("app/Greeter.java", GREETER),
                Map.of("app/GreeterTest.java", GREETER_TEST),
                main("app/Greeter.java"));

        assertThat(affectedBy(test("app/GreeterTest.java"))).containsExactly("app.GreeterTest");
    }

    @Test
    void goesByTheMostSpecificSourceFolderWhenTheyNest() throws IOException {
        project(Map.of("app/Greeter.java", GREETER), Map.of("app/GreeterTest.java", GREETER_TEST));
        BuiltProject nested = new BuiltProject(
                List.of(
                        built.folders().get(0),
                        new ClassFolder(root.resolve("src/main/java/app"), root.resolve("elsewhere"))),
                built.libraries(),
                List.of());

        assertThat(new TestSelector(new NioProjectFiles(), root).affectedBy(nested, List.of(main("app/Greeter.java"))))
                .isEmpty();
    }

    @Test
    void refusesWhatItCannotReadNamingTheFile() throws IOException {
        project(Map.of("app/Greeter.java", GREETER), Map.of("app/GreeterTest.java", """
            package app;

            class GreeterTest {
                @org.junit.jupiter.api.Test
                void greets() {
                    new Greeter().greet();
                }
            }
            """));

        for (var operation : List.of(
                FaultyProjectFiles.Operation.LIST,
                FaultyProjectFiles.Operation.READ,
                FaultyProjectFiles.Operation.READ_FROM_LIBRARY,
                FaultyProjectFiles.Operation.TEXT)) {
            assertThatExceptionOfType(TestingException.class)
                    .as(operation.toString())
                    .isThrownBy(() -> new TestSelector(new FaultyProjectFiles(operation), root)
                            .affectedBy(built, List.of(main("app/Greeter.java"))))
                    .satisfies(exception -> assertThat(exception.key()).isEqualTo(TestingErrorKey.READ_FAILED));
        }
    }
}
