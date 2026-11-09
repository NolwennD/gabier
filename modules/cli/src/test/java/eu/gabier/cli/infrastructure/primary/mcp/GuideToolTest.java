package eu.gabier.cli.infrastructure.primary.mcp;

import static eu.gabier.cli.infrastructure.primary.mcp.McpExchange.at;
import static eu.gabier.cli.infrastructure.primary.mcp.McpExchange.exchange;
import static eu.gabier.cli.infrastructure.primary.mcp.McpExchange.list;
import static eu.gabier.cli.infrastructure.primary.mcp.McpExchange.modern;
import static eu.gabier.cli.infrastructure.primary.mcp.McpExchange.modernCall;
import static eu.gabier.cli.infrastructure.primary.mcp.McpExchange.object;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * An agent that does not know which tool does a refactoring asks the {@code guide} tool, which is no operation of the
 * daemon: it answers with the guide of the cli, in the words {@code gabier help --for} uses, and asks no daemon.
 */
class GuideToolTest {

    private static Object content(Object answer) {
        return at(answer, "result", "content", 0, "text");
    }

    private static Map<String, Object> guideTool() {
        var answer = exchange(modern("1", "tools/list")).getFirst();

        return list(at(answer, "result", "tools")).stream()
                .map(McpExchange::object)
                .filter(tool -> "guide".equals(tool.get("name")))
                .findFirst()
                .orElseThrow();
    }

    @Nested
    class Listed {

        @Test
        void isTheLastTool() {
            var answer = exchange(modern("1", "tools/list")).getFirst();

            var names = list(at(answer, "result", "tools")).stream()
                    .map(tool -> object(tool).get("name"))
                    .toList();
            assertThat(names).last().isEqualTo("guide");
        }

        @Test
        void takesTheTaskInEnglishAsItsOnlyRequiredText() {
            var schema = object(guideTool().get("inputSchema"));

            assertThat(schema.get("required")).isEqualTo(List.of("task"));
            assertThat(schema).containsEntry("additionalProperties", false);
            var task = object(object(schema.get("properties")).get("task"));
            assertThat(task.get("type")).isEqualTo("string");
            assertThat((String) task.get("description")).contains("English");
        }

        @Test
        void isReadOnly() {
            assertThat(guideTool().get("annotations")).isEqualTo(Map.of("readOnlyHint", true));
        }

        @Test
        void saysWhenToCallIt() {
            assertThat((String) guideTool().get("description"))
                    .contains("before editing")
                    .contains("which tool")
                    .contains("name=true");
        }
    }

    @Nested
    class Answering {

        @Test
        void givesTheSectionsOfTheGuideThatAnswerTheTaskWithTheNamesOfTheToolsInPlaceOfTheCommands() {
            var daemon = new StubDaemon();

            var answer = exchange(daemon, modernCall("guide", "{\"task\":\"organize the imports of a file\"}"))
                    .getFirst();

            assertThat(at(answer, "result", "isError")).isEqualTo(false);
            assertThat((String) content(answer))
                    .startsWith("For \"organize the imports of a file\":\n\n1. Order the imports (confidence 0.")
                    .contains("\n   Ask: `organize-imports <file>`")
                    .doesNotContain("gabier organize-imports")
                    .doesNotContain("whole guide");
            assertThat(daemon.asked()).isEmpty();
        }

        @Test
        void givesNoStructuredContent() {
            var answer = exchange(modernCall("guide", "{\"task\":\"organize the imports of a file\"}"))
                    .getFirst();

            assertThat(object(at(answer, "result"))).doesNotContainKey("structuredContent");
        }

        @Test
        void saysByHandWhenTheLanguageServerDoesNotOfferTheMove() {
            var answer = exchange(modernCall("guide", "{\"task\":\"remove the unnecessary else after return\"}"))
                    .getFirst();

            assertThat((String) content(answer))
                    .contains("   By hand: remove the else")
                    .doesNotContain("Ask:");
        }

        @Test
        void saysNothingMatchesForATaskTheGuideDoesNotKnowAndItIsNoError() {
            var answer =
                    exchange(modernCall("guide", "{\"task\":\"bake bread\"}")).getFirst();

            assertThat(content(answer)).isEqualTo("Nothing in the guide matches \"bake bread\".");
            assertThat(at(answer, "result", "isError")).isEqualTo(false);
        }

        @Test
        void answersInTheEarlierEraToo() {
            var answers = exchange(
                    McpExchange.INITIALIZE,
                    McpExchange.request(
                            "2", "tools/call", "{\"name\":\"guide\",\"arguments\":{\"task\":\"bake bread\"}}"));

            assertThat(content(answers.get(1))).isEqualTo("Nothing in the guide matches \"bake bread\".");
            assertThat(at(answers.get(1), "result", "resultType")).isNull();
        }
    }

    @Nested
    class Refusing {

        @ParameterizedTest
        @ValueSource(strings = {"{\"task\":\"\"}", "{\"task\":\"  \\n\"}", "{\"task\":7}", "{\"task\":null}", "{}"})
        void tellsATaskThatIsMissingBlankOrNotTextAsAnInvalidValueWithAFailedCall(String arguments) {
            var daemon = new StubDaemon();

            var answer = exchange(daemon, modernCall("guide", arguments)).getFirst();

            assertThat(at(answer, "result", "isError")).isEqualTo(true);
            assertThat(at(answer, "result", "structuredContent", "key")).isEqualTo("operation-invalid-values");
            assertThat(at(answer, "result", "structuredContent", "parameters"))
                    .isEqualTo(Map.of("operation", "guide", "parameter", "task"));
            assertThat(at(answer, "error")).isNull();
            assertThat(daemon.asked()).isEmpty();
        }

        @Test
        void tellsACallWithNoArgumentsAtAllTheSame() {
            var answer = exchange(modernCall("\"name\":\"guide\"")).getFirst();

            assertThat(at(answer, "result", "isError")).isEqualTo(true);
            assertThat(content(answer))
                    .isEqualTo("Operation invalid values: The operation guide needs the value task"
                            + " (operation-invalid-values; operation=guide, parameter=task)");
        }

        @Test
        void tellsAValueTheGuideDoesNotTake() {
            var answer = exchange(modernCall("guide", "{\"task\":\"sort the imports\",\"verbose\":true}"))
                    .getFirst();

            assertThat(at(answer, "result", "isError")).isEqualTo(true);
            assertThat(at(answer, "result", "structuredContent", "detail"))
                    .isEqualTo("The operation guide takes no value named verbose");
        }
    }
}
