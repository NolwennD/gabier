package eu.gabier.daemon;

import static org.assertj.core.api.Assertions.fail;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** The real jdtls that the integration tests start: the launcher of the distribution, as {@code GABIER_JDTLS} says. */
final class RealJdtls {

    private RealJdtls() {}

    /**
     * The launcher is required: a missing one is a failure to fix, never a reason to skip a test.
     *
     * <p>The launcher runs the java of {@code JAVA_HOME}: the one that runs the test, not whatever the shell has.
     */
    static JdtlsLauncher launcher() {
        String path = System.getenv("GABIER_JDTLS");
        if (path == null || path.isBlank()) {
            return fail("GABIER_JDTLS is not set: it must be the path of the jdtls launcher. Run `mise install` (it"
                    + " installs jdtls and exports the variable), then `mise exec -- ./gradlew jdtlsProbe`.");
        }
        Path launcher = Path.of(path);
        if (!Files.isRegularFile(launcher) || !Files.isExecutable(launcher)) {
            return fail("GABIER_JDTLS is " + path + ", which is not an executable file. "
                    + "It must be the path of the jdtls launcher: run `mise install` to install it.");
        }
        return new JdtlsLauncher(launcher, Path.of(System.getProperty("java.home")), List.of());
    }
}
