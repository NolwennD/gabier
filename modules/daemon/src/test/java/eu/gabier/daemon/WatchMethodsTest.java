package eu.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.gabier.editing.application.FollowProblems;
import eu.gabier.editing.domain.DiagnosedFile;
import eu.gabier.editing.fixtures.FakeJavaLanguageServer;
import eu.gabier.editing.fixtures.ProjectDiagnosticsScenarios;
import eu.gabier.editing.fixtures.Scenario;
import eu.gabier.editing.fixtures.Scenarios;
import eu.gabier.protocol.DiagnosedFiles;
import eu.gabier.protocol.FileDiagnostics;
import eu.gabier.protocol.Problem;
import eu.gabier.protocol.RpcMessage;
import eu.gabier.protocol.RpcNotification;
import eu.gabier.protocol.RpcResponse;
import eu.gabier.shared.error.domain.ErrorStatus;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The methods {@code watch} and {@code watch-all} through the handler: the answer is where the client starts, and what
 * the daemon tells it afterwards is what its listener is offered turned into the lines of the notifications. The
 * project is the fake language server scripted by the scenarios, the disk is scripted too: the server and the disk of
 * the real project are the business of the adapter.
 */
class WatchMethodsTest {

    private static final String WATCH_ALL = """
        {"jsonrpc":"2.0","id":1,"method":"watch-all"}\
        """;

    @TempDir
    Path project;

    private final ScriptedDisk disk = new ScriptedDisk();
    private final Peers peer = new Peers();
    private Supplier<List<DiagnosedFile>> later = List::of;
    private final ProblemFeed feed = new ProblemFeed(() -> later.get(), () -> disk);

    @AfterEach
    void stopFollowing() {
        feed.close();
    }

    /** The peer of a connection: keeps what the method listens with, and what the feed offered to its listener. */
    private static final class Peers implements Peer {

        private final List<List<DiagnosedFile>> offered = new CopyOnWriteArrayList<>();
        private volatile Function<List<DiagnosedFile>, List<String>> notifications = state -> List.of();
        private volatile Consumer<Listener> whenGone = listener -> {};
        private volatile Listener listener;

        @Override
        public Listener listen(Function<List<DiagnosedFile>, List<String>> notifications, Consumer<Listener> whenGone) {
            this.notifications = notifications;
            this.whenGone = whenGone;
            listener = new Listener() {
                @Override
                public void offer(List<DiagnosedFile> state) {
                    offered.add(state);
                }

                @Override
                public void say(String notice) {}

                @Override
                public void end(Problem reason) {}
            };

            return listener;
        }

        /** The lines the connection would write for what the feed offered. */
        List<String> lines() {
            return offered.stream().map(notifications).flatMap(List::stream).toList();
        }
    }

    private FakeJavaLanguageServer server(Scenario... scenarios) {
        var server = new FakeJavaLanguageServer(project);
        for (var scenario : scenarios) {
            server.scripted(scenario);
        }

        return server;
    }

    private RequestHandler handler(FakeJavaLanguageServer server) {
        var follow = new FollowProblems(server);

        return new RequestHandler(List.of(new WatchAllMethod(follow, feed), new WatchMethod(follow, feed)));
    }

    private RequestHandler.Reply handle(RequestHandler handler, String line) {
        return handler.handle(line, peer);
    }

    private static String watching(String... files) {
        return """
            {"jsonrpc":"2.0","id":2,"method":"watch","params":{"files":"%s"}}\
            """.formatted(String.join("\\n", files));
    }

    private DiagnosedFiles answeredBy(RequestHandler.Reply reply) {
        return DiagnosedFiles.of(((RpcResponse.Success) RpcResponse.parse(reply.line())).result());
    }

    private static List<String> filesOf(DiagnosedFiles diagnosed) {
        return diagnosed.files().stream().map(FileDiagnostics::file).toList();
    }

    private DiagnosedFiles told(String line) {
        return ((RpcNotification) RpcMessage.parse(line)).problems();
    }

    private static void eventually(Supplier<Boolean> condition, String what) {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (!condition.get()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("Waited 10 s for " + what);
            }
            Thread.onSpinWait();
        }
    }

    private String path(String relative) {
        return project.resolve(relative).toString();
    }

    @Test
    void areNamedWatchAndWatchAllAndOnlyTheFirstTakesFiles() {
        var follow = new FollowProblems(server());

        assertThat(new WatchAllMethod(follow, feed).name()).isEqualTo("watch-all");
        assertThat(new WatchAllMethod(follow, feed).parameters()).isEmpty();
        assertThat(new WatchMethod(follow, feed).name()).isEqualTo("watch");
        assertThat(new WatchMethod(follow, feed).parameters()).containsExactly("files");
    }

    /** A method that follows nothing: it answers whoever it runs for. */
    private record SilentMethod() implements StreamingMethod {

        @Override
        public String name() {
            return "silent";
        }

        @Override
        public Outcome call(Map<String, String> params, Peer peer) {
            return Outcome.of("{}");
        }
    }

    @Test
    void runsForNoClientAMethodThatDoesNotListen() {
        assertThat(new SilentMethod().call(Map.of()).resultJson()).isEqualTo("{}");
    }

    @Test
    void failsWhenRunForNoClientBecauseThereIsNoOneToTell() {
        var follow = new FollowProblems(server(ProjectDiagnosticsScenarios.DIAGNOSTICS_OF_THE_WHOLE_PROJECT));

        assertThatThrownBy(() -> new WatchAllMethod(follow, feed).call(Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("There is no client to write to");
    }

    @Test
    void watchAllAnswersTheFilesThatHaveAProblemNowAsDiagnosticsAllDoes() {
        var handler = handler(server(ProjectDiagnosticsScenarios.DIAGNOSTICS_OF_THE_WHOLE_PROJECT));

        var answered = answeredBy(handle(handler, WATCH_ALL));

        assertThat(filesOf(answered))
                .containsExactly(
                        path("src/main/java/demo/Broken.java"),
                        path("src/main/java/demo/shop/Sloppy.java"),
                        path("src/test/java/demo/BrokenTest.java"));
    }

    @Test
    void watchAllTellsOnlyTheFilesWhoseProblemsChangedAndAFileThatBecameCleanWithNoProblem() {
        var handler = handler(server(ProjectDiagnosticsScenarios.DIAGNOSTICS_OF_THE_WHOLE_PROJECT));
        var edited = server(ProjectDiagnosticsScenarios.DIAGNOSTICS_OF_THE_WHOLE_PROJECT_AFTER_TWO_EDITS);
        later = edited::projectDiagnostics;

        handle(handler, WATCH_ALL);
        eventually(() -> peer.offered.size() >= 1, "the project offered");

        var lines = peer.lines();
        assertThat(lines).hasSize(1);
        var changed = told(lines.getFirst());
        assertThat(filesOf(changed))
                .containsExactly(path("src/main/java/demo/Broken.java"), path("src/main/java/demo/Clean.java"));
        assertThat(changed.files().get(0).diagnostics()).isEmpty();
        assertThat(changed.files().get(1).diagnostics()).hasSize(1);
    }

    @Test
    void watchAllTellsNothingWhenTheProjectIsReadAndHasNotChanged() {
        var handler = handler(server(ProjectDiagnosticsScenarios.DIAGNOSTICS_OF_THE_WHOLE_PROJECT));
        var same = server(ProjectDiagnosticsScenarios.DIAGNOSTICS_OF_THE_WHOLE_PROJECT);
        later = same::projectDiagnostics;

        handle(handler, WATCH_ALL);
        eventually(() -> peer.offered.size() >= 1, "the project offered");

        assertThat(peer.lines()).isEmpty();
    }

    @Test
    void watchAllIsRefusedAsDiagnosticsAllIsWhenTheServerIsNotReadyAndFollowsNothing() {
        var handler =
                handler(server(ProjectDiagnosticsScenarios.DIAGNOSTICS_OF_THE_WHOLE_PROJECT_WHILE_THE_SERVER_IMPORTS));

        var reply = handle(handler, WATCH_ALL);

        var failure = (RpcResponse.Failure) RpcResponse.parse(reply.line());
        assertThat(failure.problem().key()).isEqualTo("language-server-not-ready");
        assertThat(failure.problem().category()).isEqualTo(ErrorStatus.BAD_REQUEST);
        assertThat(peer.listener).isNull();
        assertThat(feed.following()).isFalse();
    }

    @Test
    void watchAnswersEachFileAskedForInTheOrderGivenACleanOneIncluded() {
        var handler = handler(server(Scenarios.CLEAN_FILE, Scenarios.FILE_WITH_AN_ERROR));

        var answered = answeredBy(handle(
                handler, watching(path("src/main/java/demo/Broken.java"), path("src/main/java/demo/Main.java"))));

        assertThat(filesOf(answered))
                .containsExactly(path("src/main/java/demo/Broken.java"), path("src/main/java/demo/Main.java"));
        assertThat(answered.files().get(0).diagnostics()).hasSize(1);
        assertThat(answered.files().get(1).diagnostics()).isEmpty();
    }

    @Test
    void watchTellsTheChangesOfTheFilesItWasGivenAndOfNoOtherFile() {
        var handler = handler(server(Scenarios.CLEAN_FILE, Scenarios.FILE_WITH_AN_ERROR));
        var edited = server(ProjectDiagnosticsScenarios.DIAGNOSTICS_OF_THE_WHOLE_PROJECT_AFTER_TWO_EDITS);
        later = edited::projectDiagnostics;

        handle(handler, watching(path("src/main/java/demo/Broken.java"), path("src/main/java/demo/Main.java")));
        eventually(() -> peer.offered.size() >= 1, "the project offered");

        var lines = peer.lines();
        assertThat(lines).hasSize(1);
        var changed = told(lines.getFirst());
        assertThat(filesOf(changed)).containsExactly(path("src/main/java/demo/Broken.java"));
        assertThat(changed.files().getFirst().diagnostics()).isEmpty();
    }

    @Test
    void watchRefusesAFileTheServerRefusesAndFollowsNothing() {
        var handler = handler(server(Scenarios.DIAGNOSTICS_OUTSIDE_THE_PROJECT));

        var reply = handle(handler, watching(path("../outside/Elsewhere.java")));

        assertThat(RpcResponse.parse(reply.line())).isInstanceOf(RpcResponse.Failure.class);
        assertThat(peer.listener).isNull();
        assertThat(feed.following()).isFalse();
    }

    @Test
    void watchRefusesAListOfFilesWithABlankOne() {
        var handler = handler(server());

        var reply = handle(handler, watching(path("src/main/java/demo/Main.java"), " "));

        var failure = (RpcResponse.Failure) RpcResponse.parse(reply.line());
        assertThat(failure.problem().parameters()).containsEntry("parameter", "files");
        assertThat(feed.following()).isFalse();
    }

    @Test
    void watchRefusesAnEmptyListOfFiles() {
        var handler = handler(server());

        var reply = handle(handler, watching());

        var failure = (RpcResponse.Failure) RpcResponse.parse(reply.line());
        assertThat(failure.problem().parameters()).containsEntry("parameter", "files");
    }

    @Test
    void stopsFollowingForAClientWhoseConnectionEnds() {
        var handler = handler(server(ProjectDiagnosticsScenarios.DIAGNOSTICS_OF_THE_WHOLE_PROJECT));
        later = List::of;
        handle(handler, WATCH_ALL);
        eventually(() -> !peer.offered.isEmpty(), "the project offered");
        assertThat(feed.following()).isTrue();

        peer.whenGone.accept(peer.listener);

        eventually(() -> !feed.following(), "the feed stopped");
        assertThat(disk.closed()).isTrue();
    }
}
