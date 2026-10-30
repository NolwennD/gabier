package fr.craft.gabier.daemon;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.InaccessibleObjectException;
import java.util.List;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SelfCheckTest {

    private static final String DAEMON_MODULE = "fr.craft.gabier.daemon";
    private static final String LSP4J_MODULE = "org.eclipse.lsp4j";

    @Nested
    class OnNamedModules {

        @Test
        void passesWhenTheDaemonAndLsp4jAreNamedModules() {
            assertEquals(List.of(), new SelfCheck(DAEMON_MODULE, LSP4J_MODULE).problems());
        }

        @Test
        void failsWhenTheDaemonIsOnTheClassPath() {
            SelfCheck check = new SelfCheck(null, LSP4J_MODULE);

            assertEquals(List.of("the daemon is not in a named module: it runs on the class path"), check.problems());
        }

        @Test
        void failsWhenLsp4jIsOnTheClassPath() {
            SelfCheck check = new SelfCheck(DAEMON_MODULE, null);

            assertEquals(
                    List.of("Position is not in the named module org.eclipse.lsp4j: it is on the class path"),
                    check.problems());
        }

        @Test
        void failsWhenPositionIsInAnotherNamedModule() {
            SelfCheck check = new SelfCheck(DAEMON_MODULE, "other.lsp4j");

            assertEquals(
                    List.of("Position is in the module other.lsp4j, not in the named module org.eclipse.lsp4j"),
                    check.problems());
        }

        @Test
        void reportsEveryProblem() {
            assertEquals(2, new SelfCheck(null, null).problems().size());
        }
    }

    @Nested
    class Serialization {

        private final SelfCheck onNamedModules = new SelfCheck(DAEMON_MODULE, LSP4J_MODULE);

        @Test
        void gsonWritesAPositionAsLineAndCharacter() {
            assertEquals("{\"line\":1,\"character\":2}", SelfCheck.positionAsJson());
        }

        @Test
        void failsWhenTheJsonIsNotTheExpectedOne() {
            List<String> problems = onNamedModules.problems(position -> "{\"line\":1}");

            assertEquals(List.of("Gson wrote {\"line\":1} instead of {\"line\":1,\"character\":2}"), problems);
        }

        @Test
        void failsWhenGsonCannotReachIntoThePosition() {
            List<String> problems = onNamedModules.problems(position -> {
                throw new InaccessibleObjectException("module org.eclipse.lsp4j does not open its packages");
            });

            assertEquals(
                    List.of("Gson cannot serialize a Position: java.lang.reflect.InaccessibleObjectException:"
                            + " module org.eclipse.lsp4j does not open its packages"),
                    problems);
        }

        @Test
        void passesWithTheRealGson() {
            assertEquals(List.of(), onNamedModules.problems());
        }
    }

    @Nested
    class Summary {

        @Test
        void namesTheModulesAndTheJson() {
            SelfCheck check = new SelfCheck(DAEMON_MODULE, LSP4J_MODULE);

            assertEquals(
                    "the daemon is in the module fr.craft.gabier.daemon, Position in the module org.eclipse.lsp4j,"
                            + " Gson wrote {\"line\":1,\"character\":2}",
                    check.summary());
        }

        @Test
        void callsAClassPathClassUnnamed() {
            SelfCheck check = new SelfCheck(null, null);

            assertEquals(
                    "the daemon is in no named module, Position in no named module,"
                            + " Gson wrote {\"line\":1,\"character\":2}",
                    check.summary());
        }
    }

    @Nested
    class OnThisRuntime {

        @Test
        void readsTheModulesOfTheClassesItRunsWith() {
            SelfCheck expected = new SelfCheck(
                    SelfCheck.class.getModule().getName(),
                    Position.class.getModule().getName());

            assertEquals(expected, SelfCheck.ofThisRuntime());
        }
    }
}
