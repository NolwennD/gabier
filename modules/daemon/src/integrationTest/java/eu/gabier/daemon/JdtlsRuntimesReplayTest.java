package eu.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;

import eu.gabier.daemon.JdtlsRuntimes.Jdk;
import eu.gabier.protocol.ProjectJdks;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * What the real jdtls does with the JDKs a project chooses ({@link RuntimeSources}), which {@code JdtlsSessionTest}
 * scripts against {@link FakeJdtls#bindsAsJdtlsDoes}: two folders that are the same JDK 25 under two paths (a link),
 * which jdtls takes for two JDKs of the version, so that the one it binds is the one chosen and not the only one there
 * is. Not run by {@code check}: {@code ./gradlew jdtlsProbe}, with {@code GABIER_JDTLS} set.
 */
class JdtlsRuntimesReplayTest {

    private static final Duration TIMEOUT = Duration.ofMinutes(5);

    @TempDir
    Path directory;

    private Path root;
    private Path cache;
    private Path jdk;
    private Path same;
    private String environment;
    private final List<String> log = new ArrayList<>();

    @BeforeEach
    void aProjectAndTwoPathsToTheSameJdk() throws IOException {
        jdk = Path.of(System.getProperty("java.home")).toRealPath();
        same = Files.createSymbolicLink(directory.resolve("same-jdk"), jdk);
        environment = Jdk.at(jdk).orElseThrow().name();
        root = Files.createDirectories(directory.resolve("project")).toRealPath();
        Files.createDirectories(root.resolve("src/main/java"));
        Files.writeString(root.resolve("src/main/java/Main.java"), "class Main {}\n");
        EclipseProject.describe(root);
        Files.writeString(
                root.resolve(".classpath"),
                Files.readString(root.resolve(".classpath"))
                        .replace(
                                "org.eclipse.jdt.launching.JRE_CONTAINER\"",
                                "org.eclipse.jdt.launching.JRE_CONTAINER/org.eclipse.jdt.internal.debug.ui.launcher.StandardVMType/"
                                        + environment + "\""));
        cache = Files.createDirectories(directory.resolve("cache"));
    }

    private void choose(String... entries) throws IOException {
        Files.createDirectories(root.resolve(".vscode"));
        Files.writeString(
                root.resolve(".vscode/settings.json"),
                "{\"java.configuration.runtimes\": [" + String.join(",", entries) + "]}");
    }

    private String entry(String name, Path home) {
        return "{\"name\": \"%s\", \"path\": \"%s\"}".formatted(name, home);
    }

    /** A run on the workspace kept in the cache: what it says of the JDKs, and the lines of its log. */
    private ProjectJdks run() throws Exception {
        return run(Optional.of(cache));
    }

    private ProjectJdks run(Optional<Path> kept) throws Exception {
        var real = RealJdtls.launcher();
        var launcher = new JdtlsLauncher(real.launcher(), real.javaHome(), List.of(), kept);
        log.clear();
        try (JdtlsSession session =
                JdtlsSession.start(launcher, JdtlsSettings.of(root, List.of(root), TIMEOUT), log::add, ignored -> {})) {
            System.out.println(log);

            return session.jdks();
        }
    }

    private boolean declaredAgain() {
        return log.stream().anyMatch(line -> line.startsWith("jdtls configured: "));
    }

    @Test
    void bindsTheJdkTheProjectChoseOnANewWorkspaceAndOnARestartWithoutDeclaringItAgain() throws Exception {
        choose(entry(environment, same));
        assertThat(run().bindings()).singleElement().satisfies(binding -> {
            assertThat(binding.home()).isEqualTo(same);
            assertThat(binding.source()).isEqualTo("chosen in .vscode/settings.json");
        });

        assertThat(run().bindings())
                .singleElement()
                .satisfies(binding -> assertThat(binding.home()).isEqualTo(same));
        assertThat(declaredAgain())
                .as("a restart on the same choice declares nothing")
                .isFalse();
    }

    @Test
    void bindsTheJdkTheProjectChoseFromTheFirstImportOfAWorkspaceThatIsNotKept() throws Exception {
        choose(entry(environment, same));

        assertThat(run(Optional.empty()).bindings())
                .singleElement()
                .satisfies(binding -> assertThat(binding.home()).isEqualTo(same));
        assertThat(declaredAgain()).as("the initialization options were enough").isFalse();
    }

    @Test
    void bindsAnotherJdkWhenTheChoiceChangesBetweenTwoStarts() throws Exception {
        choose(entry(environment, same));
        run();

        choose(entry(environment, jdk));
        assertThat(run().bindings())
                .singleElement()
                .satisfies(binding -> assertThat(binding.home()).isEqualTo(jdk));

        choose(entry(environment, same));
        assertThat(run().bindings())
                .singleElement()
                .satisfies(binding -> assertThat(binding.home()).isEqualTo(same));
    }

    @Test
    void ignoresAFolderThatIsNoJdkAndBindsTheOneItDrawsAndSaysWhy() throws Exception {
        choose(entry(environment, directory.resolve("nowhere")));

        var jdks = run();

        assertThat(jdks.bindings())
                .singleElement()
                .satisfies(binding -> assertThat(binding.source()).isEqualTo("bound by jdtls"));
        assertThat(jdks.warnings()).singleElement().asString().contains("nowhere");
    }
}
