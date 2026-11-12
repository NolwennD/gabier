package eu.gabier.testing.fixtures;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * The contract scenarios of the changes on disk, replayed against the real language server by the integration tests of
 * its adapter (see {@link ChangeScenario}). The projects are hand-written Eclipse projects, as the ones of {@link
 * BuildScenarios}.
 */
public final class ChangeScenarios {

    private static final Path GREETER = Path.of("src/main/java/app/Greeter.java");
    private static final Path SERVICE = Path.of("src/main/java/app/Service.java");
    private static final Path GREETER_TEST = Path.of("src/test/java/app/GreeterTest.java");

    private static final Map<Path, String> PROJECT = Map.of(
            GREETER, """
            package app;

            public class Greeter {
                public String greet() {
                    return "hello";
                }
            }
            """,
            SERVICE, """
            package app;

            public class Service {
                public String run() {
                    return new Greeter().greet();
                }
            }
            """,
            GREETER_TEST, """
            package app;

            public class GreeterTest {
                public void greets() {
                    new Greeter().greet();
                }
            }
            """);

    private static final String GREETER_EDITED = """
        package app;

        public class Greeter {
            public String greet() {
                return "bonjour";
            }
        }
        """;

    private static final String GREETER_TEST_EDITED = """
        package app;

        public class GreeterTest {
            public void greets() {
                new Greeter().greet();
                new Service().run();
            }
        }
        """;

    /** A file edited on disk is reported once; once the call is answered, nothing is reported until the next edit. */
    public static final ChangeScenario AN_EDIT_ON_DISK = new ChangeScenario(
            "an-edit-on-disk",
            PROJECT,
            List.of(
                    new ChangeScenario.Step(Map.of(), List.of(), List.of(), true),
                    new ChangeScenario.Step(Map.of(GREETER, GREETER_EDITED), List.of(), List.of(GREETER), true),
                    new ChangeScenario.Step(Map.of(), List.of(), List.of(), true),
                    new ChangeScenario.Step(
                            Map.of(GREETER_TEST, GREETER_TEST_EDITED), List.of(), List.of(GREETER_TEST), true)));

    /** A call that is not answered (it was refused) keeps its changes, and the next call adds the new ones to them. */
    public static final ChangeScenario A_CALL_THAT_IS_NOT_ANSWERED = new ChangeScenario(
            "a-call-that-is-not-answered",
            PROJECT,
            List.of(
                    new ChangeScenario.Step(Map.of(GREETER, GREETER_EDITED), List.of(), List.of(GREETER), false),
                    new ChangeScenario.Step(
                            Map.of(GREETER_TEST, GREETER_TEST_EDITED), List.of(), List.of(GREETER, GREETER_TEST), true),
                    new ChangeScenario.Step(Map.of(), List.of(), List.of(), true)));

    /** A file that is not Java (a readme) is not reported. */
    public static final ChangeScenario A_FILE_THAT_IS_NOT_JAVA = new ChangeScenario(
            "a-file-that-is-not-java",
            PROJECT,
            List.of(new ChangeScenario.Step(Map.of(Path.of("README.md"), "# Project\n"), List.of(), List.of(), true)));

    /**
     * What a rename of a class leaves on disk: the file of the class is deleted and created again under its new name,
     * the file that used it is edited. The new file and the edited one are reported, the deleted one is not.
     */
    public static final ChangeScenario A_RENAME = new ChangeScenario(
            "a-rename",
            PROJECT,
            List.of(new ChangeScenario.Step(
                    Map.of(Path.of("src/main/java/app/Welcomer.java"), """
                        package app;

                        public class Welcomer {
                            public String greet() {
                                return "hello";
                            }
                        }
                        """, SERVICE, """
                        package app;

                        public class Service {
                            public String run() {
                                return new Welcomer().greet();
                            }
                        }
                        """, GREETER_TEST, """
                        package app;

                        public class GreeterTest {
                            public void greets() {
                                new Welcomer().greet();
                            }
                        }
                        """),
                    List.of(GREETER),
                    List.of(SERVICE, Path.of("src/main/java/app/Welcomer.java"), GREETER_TEST),
                    true)));

    private ChangeScenarios() {}

    /** Every scenario of the changes on disk. */
    public static List<ChangeScenario> all() {
        return List.of(AN_EDIT_ON_DISK, A_CALL_THAT_IS_NOT_ANSWERED, A_FILE_THAT_IS_NOT_JAVA, A_RENAME);
    }
}
