package fr.craft.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;

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
            assertThat(new SelfCheck(DAEMON_MODULE, LSP4J_MODULE).problems()).isEmpty();
        }

        @Test
        void failsWhenTheDaemonIsOnTheClassPath() {
            SelfCheck check = new SelfCheck(null, LSP4J_MODULE);

            assertThat(check.problems())
                    .containsExactly("the daemon is not in a named module: it runs on the class path");
        }

        @Test
        void failsWhenLsp4jIsOnTheClassPath() {
            SelfCheck check = new SelfCheck(DAEMON_MODULE, null);

            assertThat(check.problems())
                    .containsExactly("Position is not in the named module org.eclipse.lsp4j: it is on the class path");
        }

        @Test
        void failsWhenPositionIsInAnotherNamedModule() {
            SelfCheck check = new SelfCheck(DAEMON_MODULE, "other.lsp4j");

            assertThat(check.problems())
                    .containsExactly(
                            "Position is in the module other.lsp4j, not in the named module org.eclipse.lsp4j");
        }

        @Test
        void reportsEveryProblem() {
            assertThat(new SelfCheck(null, null).problems()).hasSize(2);
        }
    }

    @Nested
    class Serialization {

        private final SelfCheck onNamedModules = new SelfCheck(DAEMON_MODULE, LSP4J_MODULE);

        @Test
        void gsonWritesAPositionAsLineAndCharacter() {
            assertThat(SelfCheck.positionAsJson()).isEqualTo("{\"line\":1,\"character\":2}");
        }

        @Test
        void failsWhenTheJsonIsNotTheExpectedOne() {
            List<String> problems = onNamedModules.problems(position -> "{\"line\":1}");

            assertThat(problems).containsExactly("Gson wrote {\"line\":1} instead of {\"line\":1,\"character\":2}");
        }

        @Test
        void failsWhenGsonCannotReachIntoThePosition() {
            List<String> problems = onNamedModules.problems(position -> {
                throw new InaccessibleObjectException("module org.eclipse.lsp4j does not open its packages");
            });

            assertThat(problems)
                    .containsExactly("Gson cannot serialize a Position: java.lang.reflect.InaccessibleObjectException:"
                            + " module org.eclipse.lsp4j does not open its packages");
        }

        @Test
        void passesWithTheRealGson() {
            assertThat(onNamedModules.problems()).isEmpty();
        }
    }

    @Nested
    class Summary {

        @Test
        void namesTheModulesAndTheJson() {
            SelfCheck check = new SelfCheck(DAEMON_MODULE, LSP4J_MODULE);

            assertThat(check.summary())
                    .isEqualTo("the daemon is in the module fr.craft.gabier.daemon, Position in the module"
                            + " org.eclipse.lsp4j, Gson wrote {\"line\":1,\"character\":2}");
        }

        @Test
        void callsAClassPathClassUnnamed() {
            SelfCheck check = new SelfCheck(null, null);

            assertThat(check.summary())
                    .isEqualTo("the daemon is in no named module, Position in no named module,"
                            + " Gson wrote {\"line\":1,\"character\":2}");
        }
    }

    @Nested
    class OnThisRuntime {

        @Test
        void readsTheModulesOfTheClassesItRunsWith() {
            SelfCheck expected = new SelfCheck(
                    SelfCheck.class.getModule().getName(),
                    Position.class.getModule().getName());

            assertThat(SelfCheck.ofThisRuntime()).isEqualTo(expected);
        }
    }
}
