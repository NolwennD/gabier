package eu.gabier.cli.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.gabier.cli.domain.Daemon;
import eu.gabier.cli.domain.DaemonRefusal;
import eu.gabier.cli.domain.DaemonReply;
import eu.gabier.cli.domain.DaemonStart;
import eu.gabier.cli.domain.Operation;
import eu.gabier.cli.domain.Operations;
import eu.gabier.cli.domain.Parameter;
import eu.gabier.cli.domain.Parameter.Type;
import eu.gabier.protocol.ChangedFiles;
import eu.gabier.protocol.CodeActions;
import eu.gabier.protocol.FileDiagnostics;
import eu.gabier.protocol.FileDiagnostics.Diagnosis;
import eu.gabier.protocol.FileDiagnostics.Severity;
import eu.gabier.protocol.FoundReferences;
import eu.gabier.protocol.Json;
import eu.gabier.shared.error.domain.ErrorStatus;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class OperationRunnerTest {

    private static final ChangedFiles CHANGED = new ChangedFiles(
            List.of(), List.of(), List.of(), List.of(Path.of("/project/Greeter.java")), "--- a\n+++ b\n");

    private final FakeDaemon daemon = new FakeDaemon();
    private final OperationRunner runner = new OperationRunner(daemon);

    @Nested
    class Asking {

        @Test
        void asksTheMethodOfTheOperationWithTheValuesAsTexts() {
            daemon.answers(new FileDiagnostics(List.of()).toJson());

            runner.run(Operations.DIAGNOSTICS, Map.of("file", "/project/Broken.java"));

            assertThat(daemon.asked).containsExactly(new Asked("diagnostics", Map.of("file", "/project/Broken.java")));
        }

        @Test
        void sendsAPositionFromZeroWhenItIsGivenFromOne() {
            daemon.answers(CHANGED.toJson());

            runner.run(
                    Operations.RENAME,
                    Map.of("file", "/project/A.java", "line", 6, "column", 18, "newName", "welcome", "dryRun", false));

            assertThat(daemon.asked)
                    .containsExactly(new Asked(
                            "rename",
                            Map.of(
                                    "file", "/project/A.java",
                                    "line", "5",
                                    "column", "17",
                                    "newName", "welcome",
                                    "dryRun", "false")));
        }

        @Test
        void sendsABooleanAsTrueOrFalse() {
            daemon.answers(CHANGED.toJson());

            runner.run(Operations.CREATE, Map.of("file", "/project/A.java", "kind", "class", "dryRun", true));

            assertThat(daemon.asked.getFirst().params()).containsEntry("dryRun", "true");
        }

        @Test
        void sendsAListAsItsElementsOneToALine() {
            daemon.answers(CHANGED.toJson());

            runner.run(
                    Operations.MOVE,
                    Map.of("files", List.of("/project/A.java", "/project/B.java"), "package", "demo", "dryRun", false));
            runner.run(
                    Operations.CLEANUP,
                    Map.of(
                            "file",
                            "/project/A.java",
                            "cleanUps",
                            List.of("addOverride", "stringConcatToTextBlock"),
                            "dryRun",
                            false));

            assertThat(daemon.asked)
                    .extracting(Asked::params)
                    .satisfiesExactly(
                            move -> assertThat(move).containsEntry("files", "/project/A.java\n/project/B.java"),
                            cleanUp -> assertThat(cleanUp)
                                    .containsEntry("cleanUps", "addOverride\nstringConcatToTextBlock"));
        }

        @Test
        void leavesOutAnOptionalValueThatIsNotGiven() {
            daemon.answers(new FileDiagnostics(List.of()).toJson());
            var optional = new Operation(
                    "diagnostics",
                    "the problems of a file",
                    "",
                    List.of(),
                    List.of(Parameter.string("file", "the file"), new Parameter("hint", Type.STRING, false, "a hint")));

            runner.run(optional, Map.of("file", "/project/A.java"));

            assertThat(daemon.asked).containsExactly(new Asked("diagnostics", Map.of("file", "/project/A.java")));
        }

        @Test
        void letsTheRefusalOfTheDaemonThrough() {
            var refusal = new DaemonRefusal(
                    "urn:gabier:error:some-key",
                    "Some key",
                    "It went wrong",
                    "some-key",
                    Map.of(),
                    ErrorStatus.BAD_REQUEST);
            daemon.refuses(refusal);

            assertThatThrownBy(() -> runner.run(Operations.DIAGNOSTICS, Map.of("file", "/project/A.java")))
                    .isSameAs(refusal);
        }
    }

    @Nested
    class Decoding {

        @Test
        void givesTheChangedFilesOfAnOperationThatChangesFiles() {
            daemon.answers(CHANGED.toJson());

            var result = runner.run(
                    Operations.INLINE, Map.of("file", "/project/A.java", "line", 1, "column", 1, "dryRun", false));

            assertThat(result).isEqualTo(new OperationResult.Changed(CHANGED));
        }

        @Test
        void givesTheProblemsOfAFileForDiagnostics() {
            var diagnostics = new FileDiagnostics(List.of(new Diagnosis(Severity.ERROR, 4, 15, "Type mismatch")));
            daemon.answers(diagnostics.toJson());

            var result = runner.run(Operations.DIAGNOSTICS, Map.of("file", "/project/A.java"));

            assertThat(result).isEqualTo(new OperationResult.Diagnosed(diagnostics));
        }

        @Test
        void givesThePlacesForReferences() {
            var found = new FoundReferences(List.of(), List.of());
            daemon.answers(found.toJson());

            var result = runner.run(
                    Operations.REFERENCES,
                    Map.of("file", "/project/A.java", "line", 1, "column", 1, "includeDeclaration", false));

            assertThat(result).isEqualTo(new OperationResult.Referenced(found));
        }

        @Test
        void givesTheActionsForActions() {
            var offered = new CodeActions(List.of());
            daemon.answers(offered.toJson());

            var result = runner.run(
                    Operations.ACTIONS,
                    Map.of(
                            "file", "/project/A.java",
                            "startLine", 1,
                            "startColumn", 1,
                            "endLine", 1,
                            "endColumn", 1));

            assertThat(result).isEqualTo(new OperationResult.Offered(offered));
        }

        @ParameterizedTest
        @MethodSource("eu.gabier.cli.application.OperationRunnerTest#operationsOfTheDaemonThatAskDoesNotServe")
        void refusesAnOperationThatIsNotAskedForItsResult(Operation operation) {
            assertThatThrownBy(() -> runner.run(operation, Map.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("The operation " + operation.method() + " is not run through run");
            assertThat(daemon.asked).isEmpty();
        }
    }

    @Nested
    class Refusing {

        private static final Map<String, Object> RENAME =
                Map.of("file", "/project/A.java", "line", 6, "column", 18, "newName", "welcome", "dryRun", false);

        private Map<String, Object> with(String name, @Nullable Object value) {
            var values = new LinkedHashMap<>(RENAME);
            values.put(name, value);

            return values;
        }

        private Map<String, Object> without(String name) {
            var values = new LinkedHashMap<>(RENAME);
            values.remove(name);

            return values;
        }

        private void refused(Map<String, Object> values, String message, String name) {
            assertThatThrownBy(() -> runner.run(Operations.RENAME, values))
                    .isInstanceOfSatisfying(InvalidValuesException.class, failure -> {
                        assertThat(failure).hasMessage(message);
                        assertThat(failure.key().get()).isEqualTo("operation-invalid-values");
                        assertThat(failure.status()).isEqualTo(ErrorStatus.BAD_REQUEST);
                        assertThat(failure.parameters())
                                .containsEntry("operation", "rename")
                                .containsEntry("parameter", name);
                    });
            assertThat(daemon.asked).isEmpty();
        }

        @Test
        void refusesARequiredValueThatIsMissing() {
            refused(without("newName"), "The operation rename needs the value newName", "newName");
        }

        @Test
        void refusesAValueTheOperationDoesNotTake() {
            refused(with("token", "greet"), "The operation rename takes no value named token", "token");
        }

        @Test
        void refusesATextThatIsAnotherKindOfValue() {
            refused(with("newName", 7), "The value newName of the operation rename must be a text", "newName");
        }

        @Test
        void refusesANumberThatIsAnotherKindOfValue() {
            refused(with("line", "6"), "The value line of the operation rename must be a whole number", "line");
        }

        @Test
        void refusesAPositionBelowOne() {
            refused(with("column", 0), "The value column of the operation rename must be a position, from 1", "column");
        }

        @Test
        void refusesABooleanThatIsAnotherKindOfValue() {
            refused(
                    with("dryRun", "false"),
                    "The value dryRun of the operation rename must be true or false",
                    "dryRun");
        }

        @Test
        void refusesAListThatIsNotOne() {
            assertThatThrownBy(() ->
                            runner.run(Operations.MOVE, Map.of("files", "A.java", "package", "p", "dryRun", true)))
                    .isInstanceOf(InvalidValuesException.class)
                    .hasMessage("The value files of the operation move must be a list of texts");
        }

        @Test
        void refusesAListWithSomethingElseThanTextsInIt() {
            assertThatThrownBy(() -> runner.run(
                            Operations.MOVE, Map.of("files", List.of("A.java", 3), "package", "p", "dryRun", true)))
                    .isInstanceOf(InvalidValuesException.class)
                    .hasMessage("The value files of the operation move must be a list of texts");
        }

        @ParameterizedTest
        @MethodSource("eu.gabier.cli.application.OperationRunnerTest#lineBreaks")
        void refusesAnElementOfAListThatHasALineBreakSinceTheDaemonReadsOneElementToALine(String lineBreak) {
            assertThatThrownBy(() -> runner.run(
                            Operations.MOVE,
                            Map.of("files", List.of("A" + lineBreak + "B.java"), "package", "p", "dryRun", true)))
                    .isInstanceOf(InvalidValuesException.class)
                    .hasMessage("The value files of the operation move must not have a line break in an element");
        }
    }

    static Stream<String> lineBreaks() {
        return Stream.of("\n", "\r");
    }

    static Stream<Operation> operationsOfTheDaemonThatAskDoesNotServe() {
        return Stream.of(Operations.STATUS, Operations.START, Operations.STOP);
    }

    private record Asked(String method, Map<String, String> params) {}

    /** The daemon of the tests: it keeps what it is asked and answers what it was given. */
    private static final class FakeDaemon implements Daemon {

        private final List<Asked> asked = new ArrayList<>();
        private @Nullable String answer;
        private @Nullable RuntimeException refusal;

        void answers(String json) {
            answer = json;
        }

        void refuses(RuntimeException refusal) {
            this.refusal = refusal;
        }

        @Override
        public DaemonReply ask(String method, Map<String, String> params) {
            asked.add(new Asked(method, params));
            if (refusal != null) {
                throw refusal;
            }

            return new DaemonReply(members(Json.parse(Objects.requireNonNull(answer))));
        }

        @SuppressWarnings("unchecked") // The answers of the tests are the JSON objects that the protocol types write.
        private static Map<String, @Nullable Object> members(@Nullable Object json) {
            return (Map<String, @Nullable Object>) json;
        }

        @Override
        public DaemonReply askRunning(String method, boolean awaitEnd) {
            throw new UnsupportedOperationException();
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
