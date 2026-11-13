package eu.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The operations of the adapter are served one at a time, on the one lock of {@link JdtlsLanguageServer}: the families
 * of operations it delegates to ({@code JdtlsCreations} and the others) hold no lock of their own and are called with
 * that one held. A public method of the facade that is not synchronized is one of the few that read no state of jdtls.
 */
class JdtlsLanguageServerLockTest {

    /** What is public and answers without the lock: the state of the start, and the end. */
    private static final Set<String> WITHOUT_THE_LOCK =
            Set.of("state", "progress", "awaitReady", "close", "watchDisk", "failure");

    @Test
    void everyOtherPublicMethodOfTheFacadeIsSynchronized() {
        var methods = Arrays.stream(JdtlsLanguageServer.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> !WITHOUT_THE_LOCK.contains(method.getName()))
                .toList();

        assertThat(methods)
                .extracting(Method::getName)
                .contains(
                        "diagnostics",
                        "projectDiagnostics",
                        "built",
                        "builtWithChanges",
                        "rename",
                        "extractVariable",
                        "extractConstant",
                        "extractField",
                        "extractMethod",
                        "moveTypeToFile",
                        "moveToPackage",
                        "inline",
                        "references",
                        "codeActions",
                        "codeActionEdit",
                        "createType",
                        "createPackageInfo",
                        "organizeImports",
                        "cleanUp");
        assertThat(methods)
                .allSatisfy(method -> assertThat(Modifier.isSynchronized(method.getModifiers()))
                        .as(method.toString())
                        .isTrue());
    }

    @Test
    void theMethodsThatAnswerWithoutTheLockAreTheOnesListed() {
        assertThat(Arrays.stream(JdtlsLanguageServer.class.getDeclaredMethods())
                        .filter(method -> Modifier.isPublic(method.getModifiers()))
                        .filter(method -> !Modifier.isSynchronized(method.getModifiers()))
                        .map(Method::getName))
                .containsExactlyInAnyOrderElementsOf(WITHOUT_THE_LOCK);
    }
}
