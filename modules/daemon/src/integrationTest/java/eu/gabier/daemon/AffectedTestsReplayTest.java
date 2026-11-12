package eu.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;

import eu.gabier.protocol.Problem;
import eu.gabier.testing.application.AffectedTests;
import eu.gabier.testing.fixtures.AffectedTestsCase;
import eu.gabier.testing.fixtures.AffectedTestsCases;
import eu.gabier.testing.fixtures.BuildScenario;
import eu.gabier.testing.fixtures.BuildScenarios;
import eu.gabier.testing.fixtures.ChangeScenario;
import eu.gabier.testing.fixtures.ChangeScenarios;
import eu.gabier.testing.fixtures.CompiledProject;
import eu.gabier.testing.infrastructure.secondary.NioProjectFiles;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * Replays the contract scenarios of the selection of tests against a real jdtls: the build it reports for a small
 * project is the one the scenario says ({@link BuildScenarios}), and the tests selected from the classes it wrote are
 * those of every case ({@link AffectedTestsCases}), the one that edits a file on disk included. Not run by {@code check}:
 * {@code ./gradlew jdtlsProbe}, with {@code GABIER_JDTLS} set to the jdtls launcher.
 */
class AffectedTestsReplayTest {

    private static final Duration TIMEOUT = Duration.ofMinutes(5);

    /** How long a client that follows the tests may wait to be told those of an edit. */
    private static final Duration TOLD_WITHIN = Duration.ofSeconds(60);

    /** How long a client that follows the tests is watched after it was told, to see that nothing more comes. */
    private static final Duration QUIET = Duration.ofSeconds(2);

    private static JdtlsLanguageServer started(Path root) throws Exception {
        var settings = JdtlsSettings.of(root, List.of(root), TIMEOUT);
        var server = JdtlsLanguageServer.start(RealJdtls.launcher(), settings, JdtlsLanguageServer.QUIET, line -> {});
        server.awaitStarted();

        return server;
    }

    private static void write(Path root, Path folder, Map<String, String> sources) throws IOException {
        for (Map.Entry<String, String> source : sources.entrySet()) {
            Path file = root.resolve(folder).resolve(source.getKey());
            Files.createDirectories(file.getParent());
            Files.writeString(file, source.getValue());
        }
    }

    @Test
    void reportsTheBuildEveryScenarioSays() throws Exception {
        for (BuildScenario scenario : BuildScenarios.all()) {
            Path root = Files.createTempDirectory("gabier-build-").toRealPath();
            scenario.writeProject(root);
            EclipseProject.describe(root);

            try (var server = started(root)) {
                var expected = scenario.inside(root).built();

                var built = server.built();

                assertThat(built).as(scenario.name()).isEqualTo(expected);
                for (var folder : expected.folders()) {
                    assertThat(folder.classes()).as(scenario.name()).isDirectory();
                }
            }
        }
    }

    @Test
    void selectsTheTestsOfEveryCaseFromTheClassesJdtlsWrote() throws Exception {
        Path root = Files.createTempDirectory("gabier-affected-").toRealPath();
        write(root, Path.of("src/main/java"), AffectedTestsCases.mainSources());
        write(root, Path.of("src/test/java"), AffectedTestsCases.testSources());
        for (Map.Entry<Path, String> other : AffectedTestsCases.otherFiles().entrySet()) {
            Files.writeString(root.resolve(other.getKey()), other.getValue());
        }
        EclipseProject.describe(root, List.of(CompiledProject.frameworks(root)));

        try (var server = started(root)) {
            var affectedTests = new AffectedTests(server, new NioProjectFiles(), root);
            for (AffectedTestsCase scenario : AffectedTestsCases.all()) {
                for (Map.Entry<Path, String> edit : scenario.edits().entrySet()) {
                    Files.writeString(root.resolve(edit.getKey()), edit.getValue());
                }

                var affected = affectedTests.affectedBy(
                        scenario.changed().stream().map(root::resolve).toList());

                assertThat(affected).as(scenario.name()).containsExactlyElementsOf(scenario.affected());
            }
        }
    }

    @Test
    void reportsTheChangesOnDiskOfEveryScenarioAsTheServerWasTold() throws Exception {
        for (ChangeScenario scenario : ChangeScenarios.all()) {
            Path root = Files.createTempDirectory("gabier-changes-").toRealPath();
            scenario.writeProject(root);
            EclipseProject.describe(root);

            try (var server = started(root)) {
                for (ChangeScenario.Step step : scenario.steps()) {
                    write(root, Path.of(""), toStrings(step.writes()));
                    for (Path deleted : step.deletes()) {
                        Files.delete(root.resolve(deleted));
                    }

                    var changed = server.builtWithChanges();

                    assertThat(changed.sources())
                            .as(scenario.name())
                            .containsExactlyElementsOf(
                                    step.reported().stream().map(root::resolve).toList());
                    if (step.answered()) {
                        changed.answered();
                    }
                }
            }
        }
    }

    @Test
    void forgetsTheChangesOfAServerThatWasStoppedAndStartedAgain() throws Exception {
        var scenario = ChangeScenarios.AN_EDIT_ON_DISK;
        Path root = Files.createTempDirectory("gabier-restart-").toRealPath();
        scenario.writeProject(root);
        EclipseProject.describe(root);
        Path edited = scenario.steps().get(1).writes().keySet().iterator().next();

        try (var server = started(root)) {
            write(root, Path.of(""), toStrings(scenario.steps().get(1).writes()));

            assertThat(server.builtWithChanges().sources()).containsExactly(root.resolve(edited));
        }

        try (var restarted = started(root)) {
            assertThat(restarted.builtWithChanges().sources()).isEmpty();
        }
    }

    @Test
    void selectsTheTestsOfEveryCaseFromTheFilesJdtlsWasToldChangedWithNoFileGiven() throws Exception {
        Path root = Files.createTempDirectory("gabier-affected-changes-").toRealPath();
        write(root, Path.of("src/main/java"), AffectedTestsCases.mainSources());
        write(root, Path.of("src/test/java"), AffectedTestsCases.testSources());
        for (Map.Entry<Path, String> other : AffectedTestsCases.otherFiles().entrySet()) {
            Files.writeString(root.resolve(other.getKey()), other.getValue());
        }
        EclipseProject.describe(root, List.of(CompiledProject.frameworks(root)));

        try (var server = started(root)) {
            var affectedTests = new AffectedTests(server, new NioProjectFiles(), root);
            for (AffectedTestsCase scenario : AffectedTestsCases.all()) {
                for (Map.Entry<Path, String> edit : scenario.edits().entrySet()) {
                    Files.writeString(root.resolve(edit.getKey()), edit.getValue());
                }
                for (Path changed : scenario.changed()) {
                    Path file = root.resolve(changed);
                    Files.writeString(file, Files.readString(file) + "\n");
                }

                var affected = affectedTests.affectedByChanges();

                assertThat(affected).as(scenario.name()).containsExactlyElementsOf(scenario.affected());
                assertThat(affectedTests.affectedByChanges())
                        .as(scenario.name())
                        .isEmpty();
            }
        }
    }

    /**
     * The tests of every case, told to a client that follows them as the files change on disk: the feed follows the disk
     * with the events of the system and reads the tests once for each change, with no request of the client, so this is
     * also the proof that jdtls is told of an edit by the files alone. A client is told the tests of the case, once the
     * edit is made, and nothing more afterwards.
     */
    @Test
    void tellsTheTestsOfEveryCaseToAClientThatFollowsThemWhileTheFilesChange() throws Exception {
        Path root = Files.createTempDirectory("gabier-affected-follow-").toRealPath();
        write(root, Path.of("src/main/java"), AffectedTestsCases.mainSources());
        write(root, Path.of("src/test/java"), AffectedTestsCases.testSources());
        for (Map.Entry<Path, String> other : AffectedTestsCases.otherFiles().entrySet()) {
            Files.writeString(root.resolve(other.getKey()), other.getValue());
        }
        EclipseProject.describe(root, List.of(CompiledProject.frameworks(root)));

        try (var server = started(root)) {
            var followed = new FollowedTests(new AffectedTests(server, new NioProjectFiles(), root));
            var updates = new CopyOnWriteArrayList<AffectedTestsUpdate>();
            var following = new CountDownLatch(1);
            try (var feed = new ProjectFeed(List::of, followed::read, () -> {
                var changes = server.watchDisk();
                following.countDown();

                return changes;
            })) {
                feed.subscribeTests(new Peer.Listener<AffectedTestsUpdate>() {
                    @Override
                    public void offer(AffectedTestsUpdate state) {
                        updates.add(state);
                    }

                    @Override
                    public void say(String notice) {
                        System.out.println("the feed said: " + notice);
                    }

                    @Override
                    public void end(Problem reason) {
                        updates.add(AffectedTestsUpdate.refused(reason));
                    }
                });
                assertThat(following.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS))
                        .as("the feed follows the disk")
                        .isTrue();
                for (AffectedTestsCase scenario : AffectedTestsCases.all()) {
                    updates.clear();
                    for (Map.Entry<Path, String> edit : scenario.edits().entrySet()) {
                        Files.writeString(root.resolve(edit.getKey()), edit.getValue());
                    }
                    for (Path changed : scenario.changed()) {
                        Path file = root.resolve(changed);
                        Files.writeString(file, Files.readString(file) + "\n");
                    }

                    var told = awaitTold(updates, scenario);

                    System.out.println(scenario.name() + ": told " + told);
                    assertThat(told.tests()).as(scenario.name()).containsExactlyElementsOf(scenario.affected());
                    assertThat(told.refusal()).as(scenario.name()).isEmpty();
                    Thread.sleep(QUIET.toMillis());
                    assertThat(together(updates).tests())
                            .as(scenario.name() + " (nothing more after the edit)")
                            .containsExactlyElementsOf(scenario.affected());
                }
            }
        }
    }

    /** What a client is told, put together, once it has been told what the case says or the time ran out. */
    private static AffectedTestsUpdate awaitTold(List<AffectedTestsUpdate> updates, AffectedTestsCase scenario)
            throws InterruptedException {
        long deadline = System.nanoTime() + TOLD_WITHIN.toNanos();
        while (System.nanoTime() < deadline) {
            if (!updates.isEmpty() && together(updates).tests().equals(scenario.affected())) {
                break;
            }
            Thread.sleep(50);
        }

        return together(updates);
    }

    private static AffectedTestsUpdate together(List<AffectedTestsUpdate> updates) {
        return updates.stream().reduce(AffectedTestsUpdate.of(List.of()), AffectedTestsUpdate::then);
    }

    private static Map<String, String> toStrings(Map<Path, String> files) {
        return files.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        entry -> entry.getKey().toString(), Map.Entry::getValue));
    }
}
