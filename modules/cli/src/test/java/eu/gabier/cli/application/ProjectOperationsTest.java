package eu.gabier.cli.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import eu.gabier.cli.domain.Daemon;
import eu.gabier.cli.domain.DaemonReply;
import eu.gabier.cli.domain.DaemonStart;
import eu.gabier.cli.domain.Operation;
import eu.gabier.cli.domain.Operations;
import eu.gabier.cli.domain.Parameter;
import eu.gabier.cli.domain.Projects;
import eu.gabier.protocol.DaemonStatus;
import eu.gabier.protocol.FileDiagnostics;
import eu.gabier.protocol.JdtlsState;
import eu.gabier.protocol.Json;
import eu.gabier.shared.error.domain.GeneratorException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** An operation runs in the project of the files it names, whichever project the front end was launched in. */
class ProjectOperationsTest {

    private static final Path MAIN = Path.of("/work/main");
    private static final Path TREE = Path.of("/work/main/.claude/worktrees/tree");

    private final TalkingDaemon main = new TalkingDaemon();
    private final TalkingDaemon tree = new TalkingDaemon();
    private final ProjectOperations operations = new ProjectOperations(new TwoProjects());

    @Test
    void runsInTheProjectOfTheFileItNames() {
        var prepared = operations.prepare(Operations.DIAGNOSTICS, Map.of("file", TREE + "/src/A.java"));

        prepared.run().get();

        assertThat(prepared.project()).isEqualTo(TREE);
        assertThat(tree.asked).containsExactly(Map.of("file", TREE + "/src/A.java"));
        assertThat(main.asked).isEmpty();
    }

    @Test
    void runsInTheLaunchedProjectForAFileOfIt() {
        operations
                .prepare(Operations.DIAGNOSTICS, Map.of("file", MAIN + "/src/A.java"))
                .run()
                .get();

        assertThat(main.asked).hasSize(1);
        assertThat(tree.asked).isEmpty();
    }

    @Test
    void runsInTheProjectOfAListOfFilesThatAreInOne() {
        var prepared = operations.prepare(
                Operations.MOVE,
                Map.of("files", List.of(TREE + "/A.java", TREE + "/src/B.java"), "package", "demo", "dryRun", false));

        assertThat(prepared.project()).isEqualTo(TREE);
    }

    @Test
    void refusesFilesOfTwoProjectsBeforeAskingAnyDaemon() {
        var refused = catchThrowable(() -> operations.prepare(
                Operations.MOVE,
                Map.of("files", List.of(TREE + "/A.java", MAIN + "/B.java"), "package", "demo", "dryRun", false)));

        assertThat(refused)
                .isInstanceOf(GeneratorException.class)
                .hasMessage("The value files of the operation move must be in one project: " + MAIN + "/B.java is in "
                        + MAIN + ", the other files of the call are in " + TREE);
        assertThat(main.asked).isEmpty();
        assertThat(tree.asked).isEmpty();
    }

    @Test
    void refusesARelativePathAndSaysToGiveAnAbsoluteOne() {
        var refused = catchThrowable(() -> operations.prepare(Operations.DIAGNOSTICS, Map.of("file", "src/A.java")));

        assertThat(refused)
                .isInstanceOf(GeneratorException.class)
                .hasMessage("The value file of the operation diagnostics must be an absolute path: "
                        + "the folder the call is made from is not known");
    }

    @Test
    void refusesARelativePathAmongTheFilesOfAList() {
        var refused = catchThrowable(() -> operations.prepare(
                Operations.MOVE,
                Map.of("files", List.of(TREE + "/A.java", "B.java"), "package", "demo", "dryRun", false)));

        assertThat(refused)
                .isInstanceOf(GeneratorException.class)
                .hasMessage("The value files of the operation move must be an absolute path: "
                        + "the folder the call is made from is not known");
    }

    @Test
    void refusesWhatIsNoPathAtAll() {
        var refused =
                catchThrowable(() -> operations.prepare(Operations.DIAGNOSTICS, Map.of("file", "/work/A\0.java")));

        assertThat(refused).isInstanceOf(GeneratorException.class).hasMessageContaining("must be an absolute path");
    }

    @Test
    void checksTheValuesAsTheRunnerDoesBeforeItLooksForAProject() {
        var empty = catchThrowable(() -> operations.prepare(Operations.DIAGNOSTICS, Map.of("file", "")));
        var unknown = catchThrowable(
                () -> operations.prepare(Operations.DIAGNOSTICS, Map.of("file", "/work/A.java", "verbose", true)));

        assertThat(empty).hasMessage("The value file of the operation diagnostics must not be empty");
        assertThat(unknown).hasMessage("The operation diagnostics takes no value named verbose");
    }

    @Test
    void leavesOutAnOptionalValueThatIsNotGivenAndLooksOnlyAtTheValuesThatAreFiles() {
        var optional = new Operation(
                "diagnostics",
                "the problems of a file",
                "",
                List.of(),
                List.of(
                        Parameter.path("file", "the file"),
                        Parameter.string("note", "a text that is no file"),
                        new Parameter("hint", Parameter.Type.STRING, false, "a hint")));

        var prepared = operations.prepare(optional, Map.of("file", TREE + "/A.java", "note", "src/A.java"));

        assertThat(prepared.project()).isEqualTo(TREE);
    }

    @Test
    void runsAnOperationThatNamesNoFileInTheLaunchedProject() {
        var prepared = operations.prepare(Operations.STATUS, Map.of());

        assertThat(prepared.project()).isEqualTo(MAIN);
        assertThat(operations.launched()).isEqualTo(MAIN);
    }

    @Test
    void tellsTheStatusOfTheDaemonOfAProject() {
        var status = operations.statusOf(TREE);

        assertThat(status.project()).isEqualTo(Path.of("/work/tree"));
        assertThat(tree.statusAsked).isTrue();
        assertThat(main.statusAsked).isFalse();
    }

    /** The two projects: the main checkout the front end is launched in, and a worktree inside it. */
    private final class TwoProjects implements Projects {

        @Override
        public Path launched() {
            return MAIN;
        }

        @Override
        public Path projectOf(Path file) {
            return file.startsWith(TREE) ? TREE : MAIN;
        }

        @Override
        public Daemon daemonOf(Path project) {
            return project.equals(TREE) ? tree : main;
        }
    }

    /** A daemon that answers an empty list of diagnostics, and its status when it is asked that. */
    private static final class TalkingDaemon implements Daemon {

        private final List<Map<String, String>> asked = new ArrayList<>();
        private boolean statusAsked;

        @Override
        public DaemonReply ask(String method, Map<String, String> params) {
            asked.add(params);

            return reply(new FileDiagnostics(List.of()).toJson());
        }

        @Override
        public DaemonReply askRunning(String method, boolean awaitEnd) {
            statusAsked = true;
            var status = new DaemonStatus("0.1", 4242, Duration.ofSeconds(1), Path.of("/work/tree"), JdtlsState.READY);

            return reply(status.toJson());
        }

        @SuppressWarnings("unchecked") // The answers of the tests are the JSON objects that the protocol types write.
        private static DaemonReply reply(String json) {
            return new DaemonReply((Map<String, @Nullable Object>) Json.parse(json));
        }

        @Override
        public DaemonStart askStarting(String method) {
            throw new UnsupportedOperationException();
        }

        @Override
        public DaemonReply statusAfter(Duration pause) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Notice noticeWhileWaiting() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Path socket() {
            throw new UnsupportedOperationException();
        }
    }
}
