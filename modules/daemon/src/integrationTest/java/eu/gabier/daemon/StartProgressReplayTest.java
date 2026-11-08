package eu.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;

import eu.gabier.protocol.JdtlsProgress;
import eu.gabier.protocol.JdtlsState;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * What the daemon says of the start of a real jdtls, which the fake of the unit tests ({@code FakeJdtls.
 * reportsWhileStarting}) scripts: the stages of {@link JdtlsProgress.Phase} come one after the other, an import has a
 * message of jdtls, and a jdtls that is ready says nothing. The words of jdtls are not compared (they are printed): the
 * contract is the order of the stages and that the message is there. Not run by {@code check}: {@code ./gradlew
 * jdtlsProbe}, with {@code GABIER_JDTLS} set.
 */
class StartProgressReplayTest {

    @TempDir
    Path directory;

    @Test
    void theStartGoesFromLaunchingToReadyAndSaysWhatJdtlsDoesOnTheWay() throws Exception {
        Path root = Files.createDirectories(directory.resolve("project")).toRealPath();
        Files.createDirectories(root.resolve("src/main/java"));
        Files.writeString(root.resolve("src/main/java/Main.java"), "class Main {}\n");
        EclipseProject.describe(root);
        List<String> seen = new ArrayList<>();
        List<JdtlsProgress.Phase> phases = new ArrayList<>();
        List<String> importMessages = new ArrayList<>();
        long begun = System.nanoTime();
        try (var server = JdtlsLanguageServer.start(
                RealJdtls.launcher(),
                JdtlsSettings.of(root, List.of(root), Duration.ofMinutes(5)),
                JdtlsLanguageServer.QUIET,
                System.out::println)) {
            String last = "";
            while (server.state() == JdtlsState.STARTING) {
                var now = server.progress();
                var heard = now.map(progress -> progress.phase() + " " + progress.message())
                        .orElse("");
                if (!heard.equals(last)) {
                    seen.add(String.format("%5.1fs %s", (System.nanoTime() - begun) / 1e9, heard));
                }
                now.ifPresent(progress -> {
                    if (phases.isEmpty() || phases.getLast() != progress.phase()) {
                        phases.add(progress.phase());
                    }
                    if (progress.phase() == JdtlsProgress.Phase.IMPORTING) {
                        importMessages.add(progress.message());
                    }
                });
                last = heard;
                Thread.sleep(5);
            }
            seen.forEach(line -> System.out.println("PROGRESS " + line));

            assertThat(server.state()).isEqualTo(JdtlsState.READY);
            assertThat(phases).containsExactly(JdtlsProgress.Phase.LAUNCHING, JdtlsProgress.Phase.IMPORTING);
            assertThat(importMessages)
                    .isNotEmpty()
                    .allSatisfy(message -> assertThat(message).isNotBlank());
            assertThat(server.progress()).isEmpty();
        }
    }
}
