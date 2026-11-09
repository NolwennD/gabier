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
import eu.gabier.protocol.DaemonStatus;
import eu.gabier.protocol.FileDiagnostics;
import eu.gabier.protocol.FileDiagnostics.Diagnosis;
import eu.gabier.protocol.FileDiagnostics.Severity;
import eu.gabier.protocol.FoundReferences;
import eu.gabier.protocol.JdtlsState;
import eu.gabier.protocol.Json;
import eu.gabier.shared.error.domain.ErrorStatus;
import java.math.BigDecimal;
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

        @ParameterizedTest
        @MethodSource("eu.gabier.cli.application.OperationRunnerTest#wholeNumbersAsAFrontEndGivesThem")
        void takesAPositionAsAnyKindOfWholeNumberAFrontEndGives(Number number) {
            daemon.answers(CHANGED.toJson());

            runner.run(
                    Operations.INLINE,
                    Map.of("file", "/project/A.java", "line", number, "column", number, "dryRun", false));

            assertThat(daemon.asked.getFirst().params())
                    .containsEntry("line", "5")
                    .containsEntry("column", "5");
        }

        @Test
        void sendsAWholeNumberThatIsNoPositionAsItIsWhateverItsValue() {
            daemon.answers(CHANGED.toJson());

            runner.run(withATimeout(), Map.of("file", "/project/A.java", "timeout", 0));
            runner.run(withATimeout(), Map.of("file", "/project/A.java", "timeout", 30L));

            assertThat(daemon.asked)
                    .extracting(Asked::params)
                    .satisfiesExactly(
                            first -> assertThat(first).containsEntry("timeout", "0"),
                            second -> assertThat(second).containsEntry("timeout", "30"));
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
        @MethodSource("eu.gabier.cli.application.OperationRunnerTest#operationsOfTheDaemonThatAskServes")
        void decodesTheResultOfEveryOperationAsTheKindItAnswers(Operation operation) {
            var expected = kindAnswered(operation);
            daemon.answers(expected.json());

            var result = runner.run(operation, validValuesOf(operation));

            assertThat(result).isEqualTo(expected.result());
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
    class ReportingTheStatus {

        private final DaemonStatus status =
                new DaemonStatus("0.1", 4242, Duration.ofSeconds(65), Path.of("/work/demo"), JdtlsState.READY);

        @Test
        void asksADaemonThatRunsAndStartsNone() {
            daemon.answers(status.toJson());

            var result = runner.run(Operations.STATUS, Map.of());

            assertThat(result).isEqualTo(new OperationResult.Reported(status));
            assertThat(daemon.askedRunning).containsExactly("status");
        }

        @Test
        void refusesAValueTheOperationDoesNotTake() {
            assertThatThrownBy(() -> runner.run(Operations.STATUS, Map.of("verbose", true)))
                    .isInstanceOf(InvalidValuesException.class)
                    .hasMessage("The operation status takes no value named verbose");
            assertThat(daemon.askedRunning).isEmpty();
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
        void saysTheValueNoParameterHasBeforeTheRequiredValueThatIsMissing() {
            var values = without("newName");
            values.put("token", "greet");

            refused(values, "The operation rename takes no value named token", "token");
        }

        @Test
        void refusesAnUnknownValueWhateverItsKind() {
            var values = without("newName");
            values.put("token", 7);
            values.put("another", "x");

            refused(values, "The operation rename takes no value named another", "another");
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

        @ParameterizedTest
        @MethodSource("eu.gabier.cli.application.OperationRunnerTest#numbersThatAreNoWholeNumber")
        void refusesANumberThatHasAFractionOrIsNotOne(Number number) {
            refused(with("line", number), "The value line of the operation rename must be a whole number", "line");
        }

        @Test
        void refusesAWholeNumberThatIsNoLongerOne() {
            refused(
                    with("line", new BigDecimal("1e30")),
                    "The value line of the operation rename must be a whole number",
                    "line");
        }

        @Test
        void refusesAWholeNumberThatTheOperationDoesNotHoldAsOne() {
            assertThatThrownBy(() -> runner.run(withATimeout(), Map.of("file", "/project/A.java", "timeout", "30")))
                    .isInstanceOfSatisfying(
                            InvalidValuesException.class,
                            failure -> assertThat(failure)
                                    .hasMessage("The value timeout of the operation inline must be a whole number"));
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

        @Test
        void refusesAnEmptyElementOfAListSinceTheDaemonWouldReadItAsNoElementAtAll() {
            assertThatThrownBy(() -> runner.run(
                            Operations.MOVE, Map.of("files", List.of("A.java", ""), "package", "p", "dryRun", true)))
                    .isInstanceOf(InvalidValuesException.class)
                    .hasMessage("The value files of the operation move must not have an empty element");
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

    static Stream<Number> wholeNumbersAsAFrontEndGivesThem() {
        return Stream.of(
                6, 6L, (short) 6, new BigDecimal("6"), new BigDecimal("6.0"), 6.0, 6.0f, new BigDecimal("0.6E1"));
    }

    static Stream<Number> numbersThatAreNoWholeNumber() {
        return Stream.of(6.5, 6.5f, new BigDecimal("6.5"), Double.NaN, Double.POSITIVE_INFINITY);
    }

    /** An operation with a whole number that is no position, as no operation of the daemon has one today. */
    private static Operation withATimeout() {
        return new Operation(
                "inline",
                "inline it",
                "",
                List.of(),
                List.of(Parameter.string("file", "the file"), Parameter.integer("timeout", "seconds")));
    }

    static Stream<Operation> operationsOfTheDaemonThatAskDoesNotServe() {
        return Stream.of(Operations.START, Operations.STOP);
    }

    static Stream<Operation> operationsOfTheDaemonThatAskServes() {
        return Operations.all().stream()
                .filter(operation -> !operation.equals(Operations.STATUS))
                .filter(operation ->
                        operationsOfTheDaemonThatAskDoesNotServe().noneMatch(other -> other.equals(operation)));
    }

    /** What an operation answers, as a test writes it down: the answer of the daemon and what it decodes into. */
    private record KindAnswered(String json, OperationResult result) {}

    private static KindAnswered kindAnswered(Operation operation) {
        return switch (operation.method()) {
            case "diagnostics" -> {
                var diagnostics = new FileDiagnostics(List.of(new Diagnosis(Severity.ERROR, 4, 15, "Type mismatch")));
                yield new KindAnswered(diagnostics.toJson(), new OperationResult.Diagnosed(diagnostics));
            }
            case "references" -> {
                var found = new FoundReferences(List.of(), List.of());
                yield new KindAnswered(found.toJson(), new OperationResult.Referenced(found));
            }
            case "actions" -> {
                var offered = new CodeActions(List.of());
                yield new KindAnswered(offered.toJson(), new OperationResult.Offered(offered));
            }
            default -> new KindAnswered(CHANGED.toJson(), new OperationResult.Changed(CHANGED));
        };
    }

    /** Values that satisfy the descriptor of an operation: what the operation declares, of the kind it declares. */
    private static Map<String, Object> validValuesOf(Operation operation) {
        var values = new LinkedHashMap<String, Object>();
        operation
                .parameters()
                .forEach(parameter -> values.put(
                        parameter.name(),
                        switch (parameter.type()) {
                            case STRING -> "x";
                            case INTEGER, POSITION -> 1;
                            case BOOLEAN -> false;
                            case ARRAY -> List.of("x");
                        }));

        return values;
    }

    private record Asked(String method, Map<String, String> params) {}

    /** The daemon of the tests: it keeps what it is asked and answers what it was given. */
    private static final class FakeDaemon implements Daemon {

        private final List<Asked> asked = new ArrayList<>();
        private final List<String> askedRunning = new ArrayList<>();
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
            askedRunning.add(method);

            return ask(method, Map.of());
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
