package fr.craft.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.InaccessibleObjectException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.jsonrpc.Endpoint;
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

    /** What the check puts to work on the jdtls client, each part replaced in turn by one that fails. */
    @Nested
    class OnTheJdtlsClient {

        private final SelfCheck onNamedModules = new SelfCheck(DAEMON_MODULE, LSP4J_MODULE);
        private final SelfCheck.Machinery real = SelfCheck.Machinery.real();
        private final InaccessibleObjectException closed =
                new InaccessibleObjectException("module fr.craft.gabier.daemon does not open its package");

        private SelfCheck.Machinery with(
                SelfCheck.Machinery.JsonReader fromJson,
                Function<Object, Endpoint> endpointOf,
                Function<Class<?>, Set<String>> methodsOf) {
            return new SelfCheck.Machinery(real.toJson(), fromJson, endpointOf, methodsOf);
        }

        @Test
        void passesWithTheRealMachinery() {
            assertThat(onNamedModules.problems(real)).isEmpty();
        }

        @Test
        void failsWhenLsp4jDoesNotKnowAMethodOfTheClient() {
            var machinery = with(real.fromJson(), real.endpointOf(), type -> Set.of("language/status"));

            assertThat(onNamedModules.problems(machinery))
                    .containsExactly("LSP4J does not know the method language/eventNotification of the jdtls client");
        }

        @Test
        void failsWhenLsp4jCannotReadTheMethodsOfTheClient() {
            var machinery = with(real.fromJson(), real.endpointOf(), type -> {
                throw closed;
            });

            assertThat(onNamedModules.problems(machinery))
                    .containsExactly("LSP4J cannot read the methods of the jdtls client: " + closed);
        }

        @Test
        void failsWhenLsp4jCannotCallTheClient() {
            var machinery = with(
                    real.fromJson(),
                    client -> {
                        throw closed;
                    },
                    real.methodsOf());

            assertThat(onNamedModules.problems(machinery))
                    .containsExactly("LSP4J cannot call the jdtls client: " + closed);
        }

        @Test
        void failsWhenTheNotificationDoesNotReachTheClient() {
            var deaf = new Endpoint() {
                @Override
                public CompletableFuture<?> request(String method, Object parameter) {
                    return CompletableFuture.completedFuture(null);
                }

                @Override
                public void notify(String method, Object parameter) {}
            };
            var machinery = with(real.fromJson(), client -> deaf, real.methodsOf());

            assertThat(onNamedModules.problems(machinery))
                    .containsExactly("The notification language/status did not reach the jdtls client");
        }

        @Test
        void failsWhenGsonCannotReadAStatus() {
            var machinery = with(failingFor(JdtlsClient.Status.class), real.endpointOf(), real.methodsOf());

            assertThat(onNamedModules.problems(machinery))
                    .containsExactly("Gson cannot read a language/status of jdtls: " + closed);
        }

        @Test
        void failsWhenGsonReadsAStatusWrong() {
            var machinery = with(readingEmptyStatus(), real.endpointOf(), real.methodsOf());

            assertThat(onNamedModules.problems(machinery))
                    .containsExactly("Gson read a language/status of jdtls as Status[type=, message=]");
        }

        @Test
        void failsWhenGsonCannotReadAPublicationOfDiagnostics() {
            var machinery = with(failingFor(PublishDiagnosticsParams.class), real.endpointOf(), real.methodsOf());

            assertThat(onNamedModules.problems(machinery))
                    .containsExactly("Gson cannot read a publication of diagnostics: " + closed);
        }

        @Test
        void failsWhenGsonReadsAPublicationOfDiagnosticsWrong() {
            var machinery = with(
                    new SelfCheck.Machinery.JsonReader() {
                        @Override
                        @SuppressWarnings("unchecked")
                        public <T> T read(String json, Class<T> type) {
                            return type == PublishDiagnosticsParams.class
                                    ? (T) new PublishDiagnosticsParams("file:///other.java", List.of())
                                    : real.fromJson().read(json, type);
                        }
                    },
                    real.endpointOf(),
                    real.methodsOf());

            assertThat(onNamedModules.problems(machinery))
                    .containsExactly("Gson read a publication of diagnostics for file:///other.java,"
                            + " not for file:///self-check/A.java");
        }

        private SelfCheck.Machinery.JsonReader failingFor(Class<?> failing) {
            return new SelfCheck.Machinery.JsonReader() {
                @Override
                public <T> T read(String json, Class<T> type) {
                    if (type == failing) {
                        throw closed;
                    }
                    return real.fromJson().read(json, type);
                }
            };
        }

        private SelfCheck.Machinery.JsonReader readingEmptyStatus() {
            return new SelfCheck.Machinery.JsonReader() {
                @Override
                @SuppressWarnings("unchecked")
                public <T> T read(String json, Class<T> type) {
                    return type == JdtlsClient.Status.class
                            ? (T) new JdtlsClient.Status("", "")
                            : real.fromJson().read(json, type);
                }
            };
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
