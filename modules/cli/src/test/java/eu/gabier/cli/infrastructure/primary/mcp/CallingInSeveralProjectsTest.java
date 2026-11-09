package eu.gabier.cli.infrastructure.primary.mcp;

import static eu.gabier.cli.infrastructure.primary.mcp.McpExchange.at;
import static eu.gabier.cli.infrastructure.primary.mcp.McpExchange.exchange;
import static eu.gabier.cli.infrastructure.primary.mcp.McpExchange.modern;
import static eu.gabier.cli.infrastructure.primary.mcp.McpExchange.modernCall;
import static org.assertj.core.api.Assertions.assertThat;

import eu.gabier.protocol.DaemonStatus;
import eu.gabier.protocol.FileDiagnostics;
import eu.gabier.protocol.ImportSettings;
import eu.gabier.protocol.JdtlsProgress;
import eu.gabier.protocol.JdtlsState;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.Test;

/** A call acts on the project of its file: the clone the server was launched in, or a worktree entered afterwards. */
class CallingInSeveralProjectsTest {

    private static final Path CLONE = Path.of("/work/clone");
    private static final Path WORKTREE = Path.of("/work/clone/trees/tree");
    private static final String CLEAN = new FileDiagnostics(List.of()).toJson();

    private final StubDaemon inClone = new StubDaemon().answering(CLEAN);
    private final StubDaemon inWorktree = new StubDaemon().answering(CLEAN);
    private final FakeProjects projects =
            FakeProjects.launchedIn(CLONE, inClone).and(WORKTREE, inWorktree);

    private static String diagnose(String id, String file) {
        return modernCall(id, "diagnostics", "{\"file\":\"" + file + "\"}");
    }

    private static String statusOf(String phase, String task) {
        return new DaemonStatus(
                        "0.1",
                        4242,
                        Duration.ofSeconds(20),
                        CLONE,
                        JdtlsState.STARTING,
                        Optional.empty(),
                        ImportSettings.DEFAULTS,
                        Optional.of(new JdtlsProgress(JdtlsProgress.Phase.valueOf(phase), task, Duration.ofSeconds(9))))
                .toJson();
    }

    @Test
    void sendsACallToTheDaemonOfTheProjectOfItsFile() {
        var answers = exchange(
                McpExchange.serverOf(projects, McpExchange.noWarmUp()),
                diagnose("1", "/work/clone/trees/tree/src/A.java"),
                diagnose("2", "/work/clone/src/B.java"));

        assertThat(answers).hasSize(2);
        assertThat(inWorktree.asked())
                .containsExactly(new StubDaemon.Asked(
                        "diagnostics", Map.of("file", "/work/clone/trees/tree/src/A.java"), false));
        assertThat(inClone.asked())
                .containsExactly(new StubDaemon.Asked("diagnostics", Map.of("file", "/work/clone/src/B.java"), false));
    }

    @Test
    void sendsACallThatNamesNoFileToTheDaemonOfTheProjectOfTheLaunch() {
        var status = statusOf("IMPORTING", "Build model");
        var daemon = new StubDaemon().reportingStatus(status);

        var answers = exchange(
                McpExchange.serverOf(
                        FakeProjects.launchedIn(CLONE, daemon).and(WORKTREE, inWorktree), McpExchange.noWarmUp()),
                modernCall("1", "status", "{}"));

        assertThat(at(answers.getFirst(), "result", "isError")).isEqualTo(false);
        assertThat(daemon.asked()).extracting(StubDaemon.Asked::method).containsExactly("status");
        assertThat(inWorktree.asked()).isEmpty();
    }

    @Test
    void refusesTheFilesOfOneCallThatAreInTwoProjectsAndAsksNoDaemon() {
        var answers = exchange(McpExchange.serverOf(projects, McpExchange.noWarmUp()), modernCall("1", "move", """
            {"files":["/work/clone/trees/tree/A.java","/work/clone/B.java"],"package":"demo","dryRun":false}\
            """));

        assertThat(at(answers.getFirst(), "result", "isError")).isEqualTo(true);
        assertThat(at(answers.getFirst(), "result", "structuredContent", "key")).isEqualTo("operation-invalid-values");
        assertThat((String) at(answers.getFirst(), "result", "structuredContent", "detail"))
                .startsWith("The value files of the operation move must be in one project: ");
        assertThat(inClone.asked()).isEmpty();
        assertThat(inWorktree.asked()).isEmpty();
    }

    @Test
    void refusesARelativePathAndSaysToGiveAnAbsoluteOneAsTheServerDoesNotKnowTheFolderOfTheSession() {
        var answers = exchange(McpExchange.serverOf(projects, McpExchange.noWarmUp()), diagnose("1", "src/A.java"));

        assertThat(at(answers.getFirst(), "result", "isError")).isEqualTo(true);
        assertThat(at(answers.getFirst(), "result", "structuredContent", "detail"))
                .isEqualTo("The value file of the operation diagnostics must be an absolute path: "
                        + "the folder the call is made from is not known");
        assertThat(inClone.asked()).isEmpty();
        assertThat(inWorktree.asked()).isEmpty();
    }

    @Test
    void doesNotMakeACallForAnotherProjectWaitForTheStartOfTheDaemonOfTheLaunch() throws Exception {
        var go = new CountDownLatch(1);
        var warmUp = WarmUp.inBackground(
                () -> {
                    try {
                        go.await();
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    }
                },
                McpExchange.silent());

        try (var session = new McpSession(McpExchange.serverOf(projects, warmUp))) {
            session.send(modern("1", "server/discover"));
            session.send(diagnose("2", "/work/clone/trees/tree/A.java"));

            var answers = List.of(session.next(), session.next());

            assertThat(answers)
                    .extracting(answer -> at(answer, "id"))
                    .containsExactlyInAnyOrder(McpExchange.number(1), McpExchange.number(2));
            assertThat(go.getCount()).isOne();
            go.countDown();
        }
    }

    @Test
    void tellsHowTheCallGoesFromTheDaemonOfTheProjectOfTheCallNotOfTheLaunch() throws Exception {
        var release = new CountDownLatch(1);
        var cloneDaemon = new StubDaemon().reportingStatus(statusOf("LAUNCHING", "Start the runtime"));
        var worktreeDaemon = new StubDaemon()
                .answering(CLEAN)
                .reportingStatus(statusOf("IMPORTING", "Build model"))
                .holding(release);
        var time = new ManualTime();

        try (var session = new McpSession(McpExchange.serverOf(
                FakeProjects.launchedIn(CLONE, cloneDaemon).and(WORKTREE, worktreeDaemon), time))) {
            session.send(modernCall("1", "diagnostics", "{\"file\":\"/work/clone/trees/tree/A.java\"}", "\"token\""));
            worktreeDaemon.awaitAsked();
            time.advance(Duration.ofSeconds(5));
            time.tick();

            var notice = session.next();

            assertThat((String) at(notice, "params", "message")).contains("Build model");
            assertThat(cloneDaemon.asked()).isEmpty();
            release.countDown();
            session.next();
        }
    }
}
