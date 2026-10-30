package fr.craft.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;

import fr.craft.gabier.editing.domain.LanguageServerErrorKey;
import fr.craft.gabier.editing.domain.LanguageServerException;
import fr.craft.gabier.editing.fixtures.Scenario;
import fr.craft.gabier.editing.fixtures.Scenario.Answer;
import fr.craft.gabier.editing.fixtures.Scenario.Request;
import fr.craft.gabier.editing.fixtures.Scenarios;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

/**
 * Replays the contract scenarios of the core ({@link Scenarios}) against a real jdtls through the adapter, and fails
 * on any difference between what jdtls answers and what the scenario says: this is what makes the answers the fake of
 * the port is scripted with a promise about the real server and not a guess.
 *
 * <p>The scenarios are grouped by their project: one jdtls is started on each. Its project is written in a fresh
 * folder, with the two files of an Eclipse project ({@code .project}, {@code .classpath}) that jdtls imports without
 * network, which a build file of Maven or Gradle would not guarantee. A scenario whose answer is "not ready" is replayed
 * before jdtls has imported the project, which is how the adapter detects it: the import takes seconds, so asking at
 * once is deterministic. The other scenarios are replayed once it is ready. Not run by {@code check}: {@code
 * ./gradlew jdtlsProbe}, with {@code GABIER_JDTLS} set to the jdtls launcher.
 *
 * <p>Only the requests the adapter implements are replayed; {@link #replayed} says which, and the test prints the
 * scenarios it leaves out.
 */
class ScenarioReplayTest {

    private static final Duration TIMEOUT = Duration.ofMinutes(5);

    private static final String ECLIPSE_PROJECT = """
        <?xml version="1.0" encoding="UTF-8"?>
        <projectDescription>
          <name>scenario</name>
          <comment></comment>
          <projects></projects>
          <buildSpec>
            <buildCommand><name>org.eclipse.jdt.core.javabuilder</name><arguments></arguments></buildCommand>
          </buildSpec>
          <natures><nature>org.eclipse.jdt.core.javanature</nature></natures>
        </projectDescription>
        """;

    private static final String ECLIPSE_CLASSPATH = """
        <?xml version="1.0" encoding="UTF-8"?>
        <classpath>
          <classpathentry kind="src" path="src/main/java"/>
          <classpathentry kind="con" path="org.eclipse.jdt.launching.JRE_CONTAINER"/>
          <classpathentry kind="output" path="bin"/>
        </classpath>
        """;

    @Test
    void replaysTheScenariosTheAdapterImplements() throws Exception {
        Map<Map<Path, String>, List<Scenario>> projects = new LinkedHashMap<>();
        List<String> left = new ArrayList<>();
        for (Scenario scenario : Scenarios.all()) {
            if (replayed(scenario)) {
                projects.computeIfAbsent(scenario.files(), files -> new ArrayList<>())
                        .add(scenario);
            } else {
                left.add(scenario.name());
            }
        }
        assertThat(projects).as("the scenarios to replay").isNotEmpty();
        System.out.println("scenarios not replayed (the adapter does not implement their request yet): " + left);

        var differences = new SoftAssertions();
        for (List<Scenario> project : projects.values()) {
            replay(project, differences);
        }
        differences.assertAll();
    }

    /** The requests the adapter implements. */
    private static boolean replayed(Scenario scenario) {
        return scenario.request() instanceof Request.Diagnostics;
    }

    /** Starts jdtls on the project the scenarios share, and replays them: first those that need it still importing. */
    private static void replay(List<Scenario> scenarios, SoftAssertions differences) throws Exception {
        Path root = Files.createTempDirectory("gabier-scenarios-").toRealPath();
        try {
            scenarios.getFirst().writeProject(root);
            Files.writeString(root.resolve(".project"), ECLIPSE_PROJECT);
            Files.writeString(root.resolve(".classpath"), ECLIPSE_CLASSPATH);
            var settings = JdtlsSettings.of(root, List.of(root), TIMEOUT);
            try (var server = JdtlsLanguageServer.start(
                    RealJdtls.launcher(), settings, JdtlsLanguageServer.QUIET, System.out::println)) {
                scenarios.stream()
                        .filter(ScenarioReplayTest::needsJdtlsToImport)
                        .forEach(scenario -> replay(scenario.inside(root), server, differences));
                server.awaitStarted();
                scenarios.stream()
                        .filter(scenario -> !needsJdtlsToImport(scenario))
                        .forEach(scenario -> replay(scenario.inside(root), server, differences));
            }
        } finally {
            delete(root);
        }
    }

    private static boolean needsJdtlsToImport(Scenario scenario) {
        return scenario.answer() instanceof Answer.Refused refused && refused.key() == LanguageServerErrorKey.NOT_READY;
    }

    private static void replay(Scenario scenario, JdtlsLanguageServer server, SoftAssertions differences) {
        var request = (Request.Diagnostics) scenario.request();
        switch (scenario.answer()) {
            case Answer.Diagnosed diagnosed -> {
                var answer = server.diagnostics(request.file());
                System.out.println(scenario.name() + ": jdtls answered " + answer);
                differences
                        .assertThat(answer)
                        .as("%s: the diagnostics jdtls answers", scenario.name())
                        .isEqualTo(diagnosed.diagnostics());
            }
            case Answer.Refused refused -> {
                LanguageServerException expected = request.refusal(refused.key());
                try {
                    var answer = server.diagnostics(request.file());
                    differences.fail(
                            "%s: jdtls answered %s, the scenario expects %s", scenario.name(), answer, expected);
                } catch (LanguageServerException refusal) {
                    System.out.println(scenario.name() + ": jdtls refused with " + refusal.key());
                    differences
                            .assertThat(refusal.key())
                            .as("%s: the key of the refusal", scenario.name())
                            .isEqualTo(expected.key());
                    differences
                            .assertThat(refusal.parameters())
                            .as("%s: the parameters of the refusal", scenario.name())
                            .isEqualTo(expected.parameters());
                    differences
                            .assertThat(refusal.getMessage())
                            .as("%s: the message of the refusal", scenario.name())
                            .isEqualTo(expected.getMessage());
                }
            }
            case Answer.Renamed _ -> throw new IllegalStateException("A rename is not replayed: " + scenario.name());
        }
    }

    private static void delete(Path folder) throws IOException {
        try (var files = Files.walk(folder)) {
            for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(file);
            }
        }
    }
}
