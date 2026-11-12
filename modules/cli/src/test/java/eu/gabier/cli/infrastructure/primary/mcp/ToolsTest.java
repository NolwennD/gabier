package eu.gabier.cli.infrastructure.primary.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import eu.gabier.cli.domain.Operation;
import eu.gabier.cli.domain.Operations;
import eu.gabier.cli.domain.Parameter;
import eu.gabier.cli.domain.Parameter.Type;
import eu.gabier.protocol.Json;
import java.util.List;
import java.util.Map;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The tools that the server lists are generated from the descriptors of the operations, and from nothing else. */
class ToolsTest {

    /** The operations that are no tool: the ones the server runs itself, and the ones that answer more than once. */
    private static final List<Operation> NOT_TOOLS =
            List.of(Operations.START, Operations.STOP, Operations.WATCH, Operations.WATCH_ALL);

    private static List<Map<String, @Nullable Object>> tools(Tools tools) {
        return list(Json.parse(tools.json())).stream().map(ToolsTest::object).toList();
    }

    private static Map<String, @Nullable Object> tool(Tools tools, String name) {
        return tools(tools).stream()
                .filter(tool -> name.equals(tool.get("name")))
                .findFirst()
                .orElseThrow();
    }

    private static Map<String, @Nullable Object> schemaOf(Map<String, @Nullable Object> tool) {
        return object(tool.get("inputSchema"));
    }

    @SuppressWarnings("unchecked") // The tools are JSON objects, written by the code under test.
    private static Map<String, @Nullable Object> object(@Nullable Object json) {
        return (Map<String, @Nullable Object>) json;
    }

    @SuppressWarnings("unchecked") // The tools are a JSON array, written by the code under test.
    private static List<@Nullable Object> list(@Nullable Object json) {
        return (List<@Nullable Object>) json;
    }

    /**
     * The words that only a person at a command line reads: an option ({@code --json}), or a command run as {@code
     * gabier <command>} ({@code gabier} followed by a word that is not prose is not looked at: "which gabier does not
     * interpret" is a sentence).
     */
    private static List<String> commandLineWords(String text) {
        var commands = new java.util.ArrayList<>(
                Operations.all().stream().map(Operation::method).toList());
        commands.add("help");

        return Pattern.compile("--[a-z][a-z-]*|\\bgabier (?:" + String.join("|", commands) + ")\\b")
                .matcher(text)
                .results()
                .map(MatchResult::group)
                .toList();
    }

    private static Operation operation(String method, String notes, Parameter... parameters) {
        return new Operation(method, "do " + method, notes, List.of(), List.of(parameters));
    }

    @Nested
    class OfTheDaemon {

        private final Tools served = Tools.served();

        @Test
        void
                hasOneToolForEachOperationButStartStopAndTheOnesThatFollowNamedAsTheOperationInTheOrderOfTheDescriptorsThenTheGuide() {
            var operations = Operations.all().stream()
                    .filter(operation -> !NOT_TOOLS.contains(operation))
                    .map(Operation::method)
                    .toList();

            assertThat(tools(served).stream().map(tool -> tool.get("name")))
                    .containsExactlyElementsOf(Stream.concat(operations.stream(), Stream.of("guide"))
                            .toList());
        }

        @Test
        void knowsTheNamesOfItsToolsTheGuideIncluded() {
            assertThat(served.has("rename")).isTrue();
            assertThat(served.has("guide")).isTrue();
            assertThat(served.has("start")).isFalse();
            assertThat(served.has("frobnicate")).isFalse();
        }

        @Test
        void describesATooByItsSummaryThenItsNotes() {
            var rename = Operations.RENAME;

            assertThat(tool(served, "rename").get("description")).isEqualTo(rename.summary() + "\n\n" + rename.notes());
        }

        @Test
        void describesATooWithNoNotesByItsSummaryAlone() {
            assertThat(tool(served, "extract-variable").get("description"))
                    .isEqualTo(Operations.EXTRACT_VARIABLE.summary());
        }

        @Test
        void followsTheParametersOfEachOperationInTheSchemaOfItsTool() {
            for (var operation : Operations.all()) {
                if (NOT_TOOLS.contains(operation)) {
                    continue;
                }
                var schema = schemaOf(tool(served, operation.method()));
                Map<String, @Nullable Object> properties =
                        operation.parameters().isEmpty() ? Map.of() : object(schema.get("properties"));

                assertThat(properties.keySet()).containsExactlyElementsOf(operation.parameterNames());
                assertThat(schema).containsEntry("type", "object").containsEntry("additionalProperties", false);
            }
        }

        @Test
        void speaksOfNoWordOfTheCommandLineInTheDescriptionOfAToolOrOfItsParameters() {
            for (var tool : tools(served)) {
                var properties = tool.get("inputSchema") instanceof Map<?, ?> schema
                                && schema.get("properties") instanceof Map<?, ?> found
                        ? found.values().stream()
                                .map(property -> object(property).get("description"))
                                .toList()
                        : List.of();

                assertThat(commandLineWords((String) tool.get("description")))
                        .as("the description of the tool %s", tool.get("name"))
                        .isEmpty();
                assertThat(properties)
                        .allSatisfy(description -> assertThat(commandLineWords((String) description))
                                .as("a parameter of the tool %s", tool.get("name"))
                                .isEmpty());
            }
        }

        @Test
        void tellsPositionsAreIntegersCountedFromOne() {
            var properties = object(schemaOf(tool(served, "apply-action")).get("properties"));

            for (var position : List.of("startLine", "startColumn", "endLine", "endColumn")) {
                var property = object(properties.get(position));
                assertThat(property.get("type")).isEqualTo("integer");
                assertThat(property.get("minimum")).isEqualTo(java.math.BigDecimal.ONE);
                assertThat((String) property.get("description")).contains("counted from 1");
            }
        }

        @Test
        void tellsFilesAndCleanUpsAreArraysOfTexts() {
            var files = object(
                    object(schemaOf(tool(served, "move")).get("properties")).get("files"));
            var cleanUps = object(
                    object(schemaOf(tool(served, "cleanup")).get("properties")).get("cleanUps"));

            for (var array : List.of(files, cleanUps)) {
                assertThat(array.get("type")).isEqualTo("array");
                assertThat(array.get("items")).isEqualTo(Map.of("type", "string"));
            }
        }

        @Test
        void tellsTextsAndBooleans() {
            var properties = object(schemaOf(tool(served, "rename")).get("properties"));

            assertThat(properties.get("newName")).isEqualTo(Map.of("type", "string", "description", "the new name"));
            assertThat(object(properties.get("dryRun")).get("type")).isEqualTo("boolean");
        }

        @Test
        void requiresEveryParameterTheDescriptorRequires() {
            assertThat(schemaOf(tool(served, "rename")).get("required"))
                    .isEqualTo(List.of("file", "line", "column", "newName", "dryRun"));
        }

        @Test
        void givesAToolWithNoParameterTheSchemaOfAnEmptyObject() {
            assertThat(schemaOf(tool(served, "status")))
                    .isEqualTo(Map.of("type", "object", "additionalProperties", false));
        }

        @ParameterizedTest
        @ValueSource(strings = {"diagnostics", "references", "actions", "affected-tests", "status"})
        void marksTheToolsThatWriteNothingAsReadOnly(String name) {
            assertThat(tool(served, name).get("annotations")).isEqualTo(Map.of("readOnlyHint", true));
        }

        @ParameterizedTest
        @ValueSource(strings = {"rename", "move", "cleanup", "apply-action", "create", "affected-tests-changed"})
        void saysNothingOfTheHintForTheToolsThatWrite(String name) {
            assertThat(tool(served, name)).doesNotContainKey("annotations");
        }
    }

    @Nested
    class TheCheckOfTheCommandLineWords {

        @Test
        void findsAnOptionAndACommandRunAsOnTheCommandLine() {
            assertThat(commandLineWords(
                            "With --include-declaration, run gabier organize-imports then gabier help move"))
                    .containsExactly("--include-declaration", "gabier organize-imports", "gabier help");
        }

        @Test
        void leavesProseAloneThatNamesTheProgramOrAToolWithoutTheProgram() {
            assertThat(commandLineWords("a title which gabier does not interpret; run organize-imports on the files"))
                    .isEmpty();
        }
    }

    @Nested
    class GeneratedFromTheDescriptors {

        @Test
        void showsAParameterAddedToAnOperationInItsSchemaWithNoOtherFileEdited() {
            var before = operation("diagnostics", "", Parameter.string("file", "the file"));
            var after = operation(
                    "diagnostics",
                    "",
                    Parameter.string("file", "the file"),
                    Parameter.integer("limit", "how many"),
                    Parameter.bool("strict", "whether"));

            assertThat(object(schemaOf(tool(new Tools(List.of(before)), "diagnostics"))
                            .get("properties")))
                    .containsOnlyKeys("file");
            var properties = object(
                    schemaOf(tool(new Tools(List.of(after)), "diagnostics")).get("properties"));
            assertThat(properties).containsOnlyKeys("file", "limit", "strict");
            assertThat(properties.get("limit")).isEqualTo(Map.of("type", "integer", "description", "how many"));
        }

        @Test
        void leavesAParameterThatIsNotRequiredOutOfRequired() {
            var tools = new Tools(List.of(operation(
                    "inline",
                    "",
                    Parameter.string("file", "the file"),
                    new Parameter("hint", Type.STRING, false, "a hint"))));

            assertThat(schemaOf(tool(tools, "inline")).get("required")).isEqualTo(List.of("file"));
        }

        @Test
        void givesNoRequiredWhenNoParameterIsRequired() {
            var tools =
                    new Tools(List.of(operation("inline", "", new Parameter("hint", Type.STRING, false, "a hint"))));

            assertThat(schemaOf(tool(tools, "inline")))
                    .doesNotContainKey("required")
                    .containsKey("properties");
        }

        @Test
        void writesTextThatIsNotPlainAsJsonThatReadsBackTheSame() {
            var notes = "quote \" backslash \\ new\nline tab\t café ☃";
            var tools = new Tools(List.of(operation("inline", notes, Parameter.string("file", "a \"file\""))));

            assertThat(tool(tools, "inline").get("description")).isEqualTo("do inline\n\n" + notes);
            assertThat(object(object(schemaOf(tool(tools, "inline")).get("properties"))
                            .get("file")))
                    .containsEntry("description", "a \"file\"");
        }

        @Test
        void hasNoGuideUnlessTheToolsAreTheServedOnes() {
            var onlyOne = new Tools(List.of(operation("inline", "")));

            assertThat(onlyOne.isGuide("guide")).isFalse();
            assertThat(onlyOne.has("guide")).isFalse();
            assertThat(tools(onlyOne)).extracting(tool -> tool.get("name")).containsExactly("inline");
        }

        @Test
        void hasNoToolForNoOperation() {
            assertThat(tools(new Tools(List.of()))).isEmpty();
        }
    }
}
