package eu.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;

import eu.gabier.editing.domain.LanguageServerException;
import eu.gabier.protocol.JsonRpc;
import eu.gabier.protocol.Problem;
import eu.gabier.protocol.RpcMessage;
import eu.gabier.protocol.RpcNotification;
import eu.gabier.protocol.RpcResponse;
import eu.gabier.protocol.SelectedTests;
import eu.gabier.testing.application.AffectedTests;
import eu.gabier.testing.domain.BuiltProject;
import eu.gabier.testing.domain.ChangedBuild;
import eu.gabier.testing.domain.ProjectBuild;
import eu.gabier.testing.fixtures.AffectedTestsCases;
import eu.gabier.testing.fixtures.BuildScenario;
import eu.gabier.testing.fixtures.CompiledProject;
import eu.gabier.testing.fixtures.FakeProjectBuild;
import eu.gabier.testing.infrastructure.secondary.NioProjectFiles;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BinaryOperator;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The method {@code watch-affected-tests} through the handler, against the shop of the contract scenarios: the answer is
 * the tests of the changes now, and what the daemon tells afterwards is what the feed offered to the listener turned into
 * the lines of the notifications. The build and the disk are scripted, as for {@code watch}.
 */
class WatchAffectedTestsMethodTest {

    private static final String REQUEST = """
        {"jsonrpc":"2.0","id":1,"method":"watch-affected-tests","params":{}}\
        """;
    private static final Path GREETER = Path.of("src/main/java/shop/Greeter.java");
    private static final Path REPOSITORY = Path.of("src/main/java/shop/Repository.java");
    private static final Path UNUSED = Path.of("src/main/java/shop/Unused.java");

    @TempDir
    Path project;

    private final ScriptedDisk disk = new ScriptedDisk();
    private FakeProjectBuild build;
    private AffectedTests affectedTests;
    private ProjectFeed feed;
    private RequestHandler handler;

    /** The peer of a connection: keeps what the method listens with, and what the feed offered to its listener. */
    private static final class Peers implements Peer {

        private final List<Object> offered = new CopyOnWriteArrayList<>();
        private volatile Function<Object, List<String>> notifications = state -> List.of();
        private volatile Consumer<Object> whenGone = listener -> {};
        private volatile BinaryOperator<Object> merge = (waiting, offered) -> offered;
        private volatile Object listener;

        @Override
        @SuppressWarnings("unchecked")
        public <S> Listener<S> listen(
                Function<S, List<String>> notifications, BinaryOperator<S> merge, Consumer<Listener<S>> whenGone) {
            this.notifications = state -> notifications.apply((S) state);
            this.merge = (waiting, offered) -> merge.apply((S) waiting, (S) offered);
            this.whenGone = listener -> whenGone.accept((Listener<S>) listener);
            var listening = new Listener<S>() {
                @Override
                public void offer(S state) {
                    offered.add(state);
                }

                @Override
                public void say(String notice) {}

                @Override
                public void end(Problem reason) {}
            };
            listener = listening;

            return listening;
        }

        /** The lines the connection would write for what the feed offered. */
        List<String> lines() {
            return offered.stream().map(notifications).flatMap(List::stream).toList();
        }

        /** The connection ends. */
        void hangsUp() {
            whenGone.accept(listener);
        }
    }

    private void servingTheShop(List<Path> filesWithErrors) throws IOException {
        for (Map.Entry<Path, String> other : AffectedTestsCases.otherFiles().entrySet()) {
            Files.writeString(project.resolve(other.getKey()), other.getValue());
        }
        build = new FakeProjectBuild(project).scripted(compiled(filesWithErrors));
        serving(build);
    }

    private BuildScenario compiled(List<Path> filesWithErrors) throws IOException {
        BuiltProject built = CompiledProject.of(
                project,
                AffectedTestsCases.mainSources(),
                AffectedTestsCases.testSources(),
                filesWithErrors.stream().map(project::resolve).toList());

        return new BuildScenario("shop", Map.of(), built);
    }

    private void serving(ProjectBuild projectBuild) {
        affectedTests = new AffectedTests(projectBuild, new NioProjectFiles(), project);
        var followed = new FollowedTests(affectedTests);
        feed = new ProjectFeed(List::of, followed::read, () -> disk);
        handler = new RequestHandler(
                List.of(new WatchAffectedTestsMethod(followed, feed), new AffectedTestsChangedMethod(affectedTests)));
    }

    @AfterEach
    void stopFollowing() {
        feed.close();
    }

    private static void eventually(BooleanSupplier condition, String what) {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("Waited 10 s for " + what);
            }
            try {
                Thread.sleep(5);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();

                throw new AssertionError("interrupted", interrupted);
            }
        }
    }

    private List<String> watching(Peers peer) {
        var response = (RpcResponse.Success)
                RpcResponse.parse(handler.handle(REQUEST, peer).line());

        return SelectedTests.of(response.result()).tests();
    }

    private List<String> asked() {
        var response =
                (RpcResponse.Success) RpcResponse.parse(handler.handle("""
                    {"jsonrpc":"2.0","id":2,"method":"affected-tests-changed","params":{}}\
                    """).line());

        return SelectedTests.of(response.result()).tests();
    }

    private static String telling(String... tests) {
        return JsonRpc.affectedTests(new SelectedTests(List.of(tests)));
    }

    /** Waits until the thread follows the disk, so that what the disk says next is a change and not the start. */
    private void followingTheDisk() {
        eventually(() -> disk.waits() > 0, "the thread to follow the disk");
    }

    private static Problem refusalIn(String line) {
        return ((RpcNotification) RpcMessage.parse(line)).refusal();
    }

    @Test
    void isNamedWatchAffectedTestsAndTakesNoParameter() throws IOException {
        servingTheShop(List.of());

        var method = new WatchAffectedTestsMethod(new FollowedTests(affectedTests), feed);

        assertThat(method.name()).isEqualTo("watch-affected-tests");
        assertThat(method.parameters()).isEmpty();
    }

    @Test
    void answersTheTestsOfTheChangesNowAndUsesThemUp() throws IOException {
        servingTheShop(List.of());
        build.edited(REPOSITORY);

        var started = watching(new Peers());

        assertThat(started)
                .containsExactly("shop.ArchitectureTest", "shop.LegacyTest", "shop.NgTest", "shop.ServiceTest");
        assertThat(asked()).isEmpty();
    }

    @Test
    void answersNoTestAndTellsNothingWhenNothingChanged() throws IOException {
        servingTheShop(List.of());
        var peer = new Peers();

        assertThat(watching(peer)).isEmpty();
        followingTheDisk();

        assertThat(peer.lines()).isEmpty();
    }

    @Test
    void tellsTheTestsOfEachChangeOnDiskAndNothingWhenNoTestIsAffected() throws IOException {
        servingTheShop(List.of());
        var peer = new Peers();
        watching(peer);
        followingTheDisk();
        build.edited(UNUSED);

        disk.says(DiskChanges.Signal.CHANGED);

        eventually(() -> peer.lines().size() == 1, "the tests of the change");
        assertThat(peer.lines()).containsExactly(telling("shop.ArchitectureTest"));
        build.edited(Path.of("src/test/java/shop/ServiceTest.java"));

        disk.says(DiskChanges.Signal.CHANGED);

        eventually(() -> peer.lines().size() == 2, "the tests of the second change");
        assertThat(peer.lines())
                .containsExactly(
                        telling("shop.ArchitectureTest"), telling("shop.ArchitectureTest", "shop.ServiceTest"));
    }

    @Test
    void tellsNothingForAChangeOnDiskThatTouchesNoJavaFile() throws IOException {
        servingTheShop(List.of());
        var peer = new Peers();
        watching(peer);
        followingTheDisk();

        disk.says(DiskChanges.Signal.CHANGED);
        eventually(() -> build.asked() > 1, "the build to be read again");

        assertThat(peer.lines()).isEmpty();
    }

    @Test
    void doesNotAnswerAChangeThatDoesNotCompileAsNoTestAffectedButTellsWhyKeepsItAndListsItsTestsOnceItCompiles()
            throws IOException {
        servingTheShop(List.of(GREETER));
        build.edited(GREETER);
        var peer = new Peers();

        var started = watching(peer);

        assertThat(started).isEmpty();
        assertThat(peer.lines()).hasSize(1);
        assertThat(refusalIn(peer.lines().getFirst()).key()).isEqualTo("testing-compilation-failed");
        assertThat(build.builtWithChanges().sources()).containsExactly(project.resolve(GREETER));
        followingTheDisk();
        build.scripted(compiled(List.of()));

        disk.says(DiskChanges.Signal.CHANGED);

        eventually(() -> peer.lines().size() == 2, "the tests once the code compiles");
        assertThat(peer.lines().get(1)).startsWith("{\"jsonrpc\":\"2.0\",\"method\":\"affected-tests\"");
        assertThat(build.builtWithChanges().sources()).isEmpty();
    }

    @Test
    void goesOnAfterARefusalOfAChangeMadeWhileItFollows() throws IOException {
        servingTheShop(List.of());
        var peer = new Peers();
        watching(peer);
        followingTheDisk();
        build.edited(GREETER);
        build.scripted(compiled(List.of(GREETER)));

        disk.says(DiskChanges.Signal.CHANGED);

        eventually(() -> peer.lines().size() == 1, "the refusal");
        assertThat(refusalIn(peer.lines().getFirst()).key()).isEqualTo("testing-compilation-failed");
        assertThat(feed.following()).isTrue();
    }

    @Test
    void refusesTheRequestWhenTheLanguageServerIsNotReadyAndFollowsNothing() {
        serving(new ProjectBuild() {
            @Override
            public BuiltProject built() {
                throw LanguageServerException.notReady();
            }

            @Override
            public ChangedBuild builtWithChanges() {
                throw LanguageServerException.notReady();
            }
        });
        var peer = new Peers();

        var failure = (RpcResponse.Failure)
                RpcResponse.parse(handler.handle(REQUEST, peer).line());

        assertThat(failure.problem().key()).isEqualTo("language-server-not-ready");
        assertThat(feed.following()).isFalse();
    }

    @Test
    void readsOnceForAChangeAndTellsTheSameTestsToEveryWatcher() throws IOException {
        servingTheShop(List.of());
        var first = new Peers();
        var second = new Peers();
        watching(first);
        watching(second);
        followingTheDisk();
        build.edited(UNUSED);
        int before = build.asked();

        disk.says(DiskChanges.Signal.CHANGED);

        eventually(() -> first.lines().size() == 1 && second.lines().size() == 1, "both told");
        assertThat(first.lines()).isEqualTo(second.lines());
        assertThat(build.asked()).isEqualTo(before + 1);
    }

    @Test
    void usesTheChangesUpForTheCallThatComesAfterTheWatcherWasTold() throws IOException {
        servingTheShop(List.of());
        var peer = new Peers();
        watching(peer);
        followingTheDisk();
        build.edited(UNUSED);
        disk.says(DiskChanges.Signal.CHANGED);
        eventually(() -> peer.lines().size() == 1, "the watcher told");

        assertThat(asked()).isEmpty();
    }

    @Test
    void tellsTheWatcherNothingOfTheChangesTheCallThatCameFirstUsedUp() throws IOException {
        servingTheShop(List.of());
        var peer = new Peers();
        watching(peer);
        followingTheDisk();
        build.edited(UNUSED);

        assertThat(asked()).containsExactly("shop.ArchitectureTest");
        disk.says(DiskChanges.Signal.CHANGED);
        eventually(() -> build.asked() > 2, "the watcher to read");

        assertThat(peer.lines()).isEmpty();
    }

    @Test
    void stopsFollowingWhenTheConnectionEnds() throws IOException {
        servingTheShop(List.of());
        var peer = new Peers();
        watching(peer);

        peer.hangsUp();

        eventually(() -> !feed.following(), "the feed to stop");
    }
}
