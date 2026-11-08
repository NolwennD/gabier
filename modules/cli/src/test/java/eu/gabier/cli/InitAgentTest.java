package eu.gabier.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.gabier.protocol.Json;
import eu.gabier.protocol.ProtocolVersion;
import java.io.IOException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * {@code gabier init-agent} writes the instructions for a coding agent into the project, in the form the maintainers
 * choose: a skill, a rule or a block of {@code AGENTS.md}. It never talks to the daemon. The tests run on a temporary
 * project folder, given with {@code --target=}.
 */
class InitAgentTest extends CliTest {

    private static final String START = "<!-- gabier:start -->";
    private static final String END = "<!-- gabier:end -->";
    private static final String POINTER = "Usage and other ways: gabier help init-agent";

    static Stream<String> forms() {
        return Stream.of("skill", "rule", "snippet");
    }

    private Path fileOf(String form) {
        return switch (form) {
            case "skill" -> directory.resolve(".claude/skills/gabier/SKILL.md");
            case "rule" -> directory.resolve(".claude/rules/gabier.md");
            default -> directory.resolve("AGENTS.md");
        };
    }

    private int init(String... arguments) {
        var words = Stream.concat(Stream.of("init-agent", "--target=" + directory), Stream.of(arguments))
                .toArray(String[]::new);

        return run(words);
    }

    private String read(Path file) throws IOException {
        return Files.readString(file);
    }

    /** The lines of the instructions after their heading: what the three forms hold the same. */
    private static List<String> bodyOfTheInstructions() {
        return AgentInstructions.text().stripTrailing().lines().skip(1).toList();
    }

    @ParameterizedTest
    @MethodSource("forms")
    void eachFormIsWrittenInItsPlaceWithTheInstructionsAndTheVersionOfGabier(String form) throws IOException {
        assertThat(init(form)).isZero();

        assertThat(err()).isEmpty();
        assertThat(out()).isEqualTo("created " + fileOf(form) + System.lineSeparator());
        var written = read(fileOf(form));
        assertThat(written).contains(START + "\n").contains("\n" + END + "\n");
        assertThat(written).contains("gabier " + ProtocolVersion.CURRENT + " wrote this");
        assertThat(written.lines().toList()).containsSubsequence(bodyOfTheInstructions());
    }

    @Test
    void theSkillHasItsFrontmatterBeforeTheBlock() throws IOException {
        init("skill");

        var lines = read(fileOf("skill")).lines().toList();
        assertThat(lines.subList(0, 2)).containsExactly("---", "name: gabier");
        assertThat(lines.get(2)).startsWith("description: Use when ");
        assertThat(lines.get(3)).isEqualTo("---");
        assertThat(lines.indexOf(START)).isGreaterThan(3);
        assertThat(lines).contains("# Java with gabier");
    }

    @Test
    void theDescriptionOfTheSkillIsOnePlainLineOfYamlWithoutAColonAndASpace() throws IOException {
        init("skill");

        var description = read(fileOf("skill")).lines().toList().get(2).substring("description: ".length());
        assertThat(description).doesNotContain(": ").doesNotContain(" #").isNotBlank();
    }

    @Test
    void theRuleHasItsHeadingInTheBlockAndNoFrontmatter() throws IOException {
        init("rule");

        var lines = read(fileOf("rule")).lines().toList();
        assertThat(lines.getFirst()).isEqualTo(START);
        assertThat(lines).contains("# Java with gabier").doesNotContain("---");
    }

    @Test
    void theSnippetKeepsTheHeadingOfTheInstructionsAsTheyAreToPaste() throws IOException {
        init("snippet");

        assertThat(read(fileOf("snippet")).lines().toList()).contains("## Java with gabier");
    }

    @Test
    void aSnippetIsAppendedAfterTheTextOfAnExistingFile() throws IOException {
        Files.writeString(fileOf("snippet"), "# Working on demo\n\nBuild with make.\n");

        assertThat(init("snippet")).isZero();

        assertThat(out()).isEqualTo("appended the gabier block to " + fileOf("snippet") + System.lineSeparator());
        var written = read(fileOf("snippet"));
        assertThat(written).startsWith("# Working on demo\n\nBuild with make.\n\n" + START + "\n");
        assertThat(written).endsWith("\n" + END + "\n");
    }

    @Test
    void anEmptyFileGetsTheBlockAlone() throws IOException {
        Files.writeString(fileOf("snippet"), "");

        init("snippet");

        assertThat(read(fileOf("snippet"))).startsWith(START + "\n");
    }

    @Test
    void aFileThatDoesNotEndWithALineBreakGetsOneBeforeTheBlock() throws IOException {
        Files.writeString(fileOf("snippet"), "Build with make.");

        init("snippet");

        assertThat(read(fileOf("snippet"))).startsWith("Build with make.\n\n" + START + "\n");
    }

    @ParameterizedTest
    @MethodSource("forms")
    void aSecondRunReplacesTheBlockInPlaceAndKeepsWhatIsAroundIt(String form) throws IOException {
        init(form);
        var first = read(fileOf(form));
        var stale = first.replace("\n" + END, "\nA stale line.\n" + END);
        Files.writeString(fileOf(form), stale.replace(START, "Before.\n" + START) + "After.\n");
        out.reset();

        assertThat(init(form)).isZero();

        assertThat(out()).isEqualTo("replaced the gabier block of " + fileOf(form) + System.lineSeparator());
        assertThat(read(fileOf(form))).isEqualTo(first.replace(START, "Before.\n" + START) + "After.\n");
    }

    @Test
    void aSecondRunOfTheSkillKeepsTheFrontmatterTheMaintainersEdited() throws IOException {
        init("skill");
        var edited = read(fileOf("skill")).replace("name: gabier", "name: gabier\nallowed-tools: Bash");
        Files.writeString(fileOf("skill"), edited);

        init("skill");

        assertThat(read(fileOf("skill"))).isEqualTo(edited);
    }

    @Test
    void aSkillWithABlockAndNoFrontmatterGetsTheFrontmatterBack() throws IOException {
        init("skill");
        var complete = read(fileOf("skill"));
        var frontmatterEnd = complete.indexOf(START);
        Files.writeString(fileOf("skill"), "Notes.\n" + complete.substring(frontmatterEnd));
        out.reset();

        assertThat(init("skill")).isZero();

        assertThat(out()).startsWith("replaced the gabier block of ");
        assertThat(read(fileOf("skill")))
                .isEqualTo(complete.substring(0, frontmatterEnd) + "Notes.\n" + complete.substring(frontmatterEnd));
    }

    @ParameterizedTest
    @MethodSource("forms")
    void aWriteLeavesNoTemporaryFileBehind(String form) throws IOException {
        init(form);
        init(form);

        try (var files = Files.walk(directory)) {
            assertThat(files.filter(Files::isRegularFile).count()).isEqualTo(1);
        }
    }

    @Test
    void aWriteKeepsTheModeOfTheFileItReplacesTheBlockOf() throws IOException {
        Files.writeString(fileOf("snippet"), "Build with make.\n");
        var ownerOnly = PosixFilePermissions.fromString("rwxr-x---");
        Files.setPosixFilePermissions(fileOf("snippet"), ownerOnly);

        assertThat(init("snippet")).isZero();

        assertThat(Files.getPosixFilePermissions(fileOf("snippet"))).isEqualTo(ownerOnly);
    }

    @ParameterizedTest
    @ValueSource(strings = {"skill", "rule"})
    void aFileThatGabierDidNotWriteIsRefusedForTheSkillAndTheRule(String form) throws IOException {
        Files.createDirectories(fileOf(form).getParent());
        Files.writeString(fileOf(form), "My own text.\n");

        assertThat(init(form)).isEqualTo(2);

        assertThat(out()).isEmpty();
        assertThat(err())
                .startsWith("Init agent file exists: ")
                .contains("gabier did not write")
                .contains("init-agent-file-exists")
                .endsWith(System.lineSeparator() + POINTER + System.lineSeparator());
        assertThat(read(fileOf(form))).isEqualTo("My own text.\n");
    }

    @ParameterizedTest
    @ValueSource(strings = {"skill", "rule"})
    void forceOverwritesASkillOrARuleThatGabierDidNotWrite(String form) throws IOException {
        Files.createDirectories(fileOf(form).getParent());
        Files.writeString(fileOf(form), "My own text.\n");

        assertThat(init("--force", form)).isZero();

        assertThat(out()).isEqualTo("overwrote " + fileOf(form) + System.lineSeparator());
        assertThat(read(fileOf(form))).contains(START).doesNotContain("My own text.");
    }

    @Test
    void forceChangesNothingForASnippetWhichIsAppendedAnyway() throws IOException {
        Files.writeString(fileOf("snippet"), "My own text.\n");

        assertThat(init("--force", "snippet")).isZero();

        assertThat(out()).isEqualTo("appended the gabier block to " + fileOf("snippet") + System.lineSeparator());
        assertThat(read(fileOf("snippet"))).startsWith("My own text.\n\n" + START + "\n");
    }

    @ParameterizedTest
    @MethodSource("forms")
    void aBlockWithNoEndIsRefusedAndTheFileIsLeftAlone(String form) throws IOException {
        Files.createDirectories(fileOf(form).getParent());
        var broken = "My text.\n" + START + "\nhalf a block\n";
        Files.writeString(fileOf(form), broken);

        assertThat(init("--force", form)).isEqualTo(2);

        assertThat(out()).isEmpty();
        assertThat(err()).contains("init-agent-block-unfinished").endsWith(POINTER + System.lineSeparator());
        assertThat(read(fileOf(form))).isEqualTo(broken);
    }

    @ParameterizedTest
    @MethodSource("forms")
    void aFileWithTwoBlocksIsRefusedAndTheFileIsLeftAlone(String form) throws IOException {
        Files.createDirectories(fileOf(form).getParent());
        var twice = START + "\nfirst\n" + END + "\nBetween.\n" + START + "\nsecond\n" + END + "\n";
        Files.writeString(fileOf(form), twice);

        assertThat(init("--force", form)).isEqualTo(2);

        assertThat(out()).isEmpty();
        assertThat(err()).contains("init-agent-block-repeated").endsWith(POINTER + System.lineSeparator());
        assertThat(read(fileOf(form))).isEqualTo(twice);
    }

    @Test
    void aMarkerQuotedInACodeFenceIsNotABlock() throws IOException {
        var documented = "The block looks like:\n\n```\n" + START + "\nsome text\n" + END + "\n```\n";
        Files.writeString(fileOf("snippet"), documented);

        assertThat(init("snippet")).isZero();

        assertThat(out()).startsWith("appended the gabier block to ");
        assertThat(read(fileOf("snippet"))).startsWith(documented + "\n" + START + "\n");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                " ```\n%1$s\n%2$s\n ```\n",
                "   ~~~~\n%1$s\n%2$s\n   ~~~~\n",
                "````\n```\n%1$s\n%2$s\n```\n````\n",
                "```\n~~~\n%1$s\n```java\n%2$s\n````\n",
                "~~~text\n%1$s\n```\n%2$s\n~~~\n",
                "```\n    ```\n%1$s\n%2$s\n```\n"
            })
    void aMarkerInsideAFenceIsNotABlockWhateverTheFence(String fenced) throws IOException {
        var documented = "Documented:\n\n" + fenced.formatted(START, END);
        Files.writeString(fileOf("snippet"), documented);

        assertThat(init("snippet")).isZero();

        assertThat(out()).startsWith("appended the gabier block to ");
        assertThat(read(fileOf("snippet"))).startsWith(documented + "\n" + START + "\n");
    }

    @Test
    void aFileThatEndsInsideAFenceIsRefusedForASnippetAndLeftAlone() throws IOException {
        var unclosed = "Example:\n\n```\nsome code\n";
        Files.writeString(fileOf("snippet"), unclosed);

        assertThat(init("snippet")).isEqualTo(2);

        assertThat(out()).isEmpty();
        assertThat(err()).contains("init-agent-fence-unclosed").endsWith(POINTER + System.lineSeparator());
        assertThat(read(fileOf("snippet"))).isEqualTo(unclosed);
    }

    @Test
    void aBlockBeforeAFenceThatIsNeverClosedIsStillReplaced() throws IOException {
        init("snippet");
        var block = read(fileOf("snippet"));
        var unclosed = "\n```\n" + START + "\nquoted\n";
        Files.writeString(fileOf("snippet"), block.replace("\n" + END, "\nStale.\n" + END) + unclosed);

        assertThat(init("snippet")).isZero();

        assertThat(read(fileOf("snippet"))).isEqualTo(block + unclosed);
    }

    @Test
    void aFileThatEndsInsideAFenceIsNotCheckedForTheFormsThatOverwrite() throws IOException {
        Files.createDirectories(fileOf("rule").getParent());
        Files.writeString(fileOf("rule"), "```\nsome code\n");

        assertThat(init("--force", "rule")).isZero();

        assertThat(out()).startsWith("overwrote ");
    }

    @Test
    void aLineWithBackticksAfterTheOpeningRunIsNotAFence() throws IOException {
        init("snippet");
        var block = read(fileOf("snippet"));
        var line = "```not a fence``` but a line of text\n";
        Files.writeString(fileOf("snippet"), line + block.replace("\n" + END, "\nStale.\n" + END));

        assertThat(init("snippet")).isZero();

        assertThat(read(fileOf("snippet"))).isEqualTo(line + block);
    }

    @Test
    void aLineIndentedByFourSpacesDoesNotOpenAFence() throws IOException {
        init("snippet");
        var block = read(fileOf("snippet"));
        Files.writeString(fileOf("snippet"), "    ```\n" + block.replace("\n" + END, "\nStale.\n" + END));

        assertThat(init("snippet")).isZero();

        assertThat(read(fileOf("snippet"))).isEqualTo("    ```\n" + block);
    }

    @Test
    void aBlockAfterAQuotedMarkerIsTheOneThatIsReplaced() throws IOException {
        init("snippet");
        var block = read(fileOf("snippet"));
        var documented = "Quoted:\n\n~~~\n" + START + "\n" + END + "\n~~~\n\n";
        Files.writeString(fileOf("snippet"), documented + block.replace("\n" + END, "\nStale.\n" + END));

        assertThat(init("snippet")).isZero();

        assertThat(read(fileOf("snippet"))).isEqualTo(documented + block);
    }

    @Test
    void replacingTheBlockOfAFileWithWindowsLineEndingsKeepsThem() throws IOException {
        init("snippet");
        var block = read(fileOf("snippet"));
        var stale = block.replace("\n" + END, "\nStale.\n" + END);
        Files.writeString(fileOf("snippet"), ("Before.\n" + stale + "After.\n").replace("\n", "\r\n"));

        assertThat(init("snippet")).isZero();

        assertThat(read(fileOf("snippet"))).isEqualTo(("Before.\n" + block + "After.\n").replace("\n", "\r\n"));
    }

    @Test
    void appendingToAFileWithWindowsLineEndingsKeepsThem() throws IOException {
        Files.writeString(fileOf("snippet"), "Build with make.\r\nThen test.\r\n");

        assertThat(init("snippet")).isZero();

        var written = read(fileOf("snippet"));
        assertThat(written).startsWith("Build with make.\r\nThen test.\r\n\r\n" + START + "\r\n");
        assertThat(written.replace("\r\n", "")).doesNotContain("\n");
    }

    @Test
    void theBlockTakesTheLineEndingsOfItsOwnStartLine() throws IOException {
        init("snippet");
        var block = read(fileOf("snippet"));
        var stale = block.replace("\n" + END, "\nStale.\n" + END);
        Files.writeString(fileOf("snippet"), "Before.\r\n" + stale + "After.\r\n");

        init("snippet");

        assertThat(read(fileOf("snippet"))).isEqualTo("Before.\r\n" + block + "After.\r\n");
    }

    @Test
    void aWindowsBlockInAFileOfUnixLinesStaysWindows() throws IOException {
        init("snippet");
        var block = read(fileOf("snippet"));
        var windows = block.replace("\n" + END, "\nStale.\n" + END).replace("\n", "\r\n");
        Files.writeString(fileOf("snippet"), "Before.\n" + windows + "After.\n");

        init("snippet");

        assertThat(read(fileOf("snippet"))).isEqualTo("Before.\n" + block.replace("\n", "\r\n") + "After.\n");
    }

    @Test
    void aFileThatStartsWithWindowsLinesGetsTheBlockWithWindowsLines() throws IOException {
        Files.writeString(fileOf("snippet"), "Build.\r\nThen test.\n");

        init("snippet");

        assertThat(read(fileOf("snippet"))).startsWith("Build.\r\nThen test.\n\r\n" + START + "\r\n");
    }

    @Test
    void aSkillThatOpensWithAByteOrderMarkBeforeItsFrontmatterKeepsIt() throws IOException {
        init("skill");
        var withMark = "\uFEFF" + read(fileOf("skill"));
        Files.writeString(fileOf("skill"), withMark);

        init("skill");

        assertThat(read(fileOf("skill"))).isEqualTo(withMark);
    }

    @Test
    void aSkillThatOpensWithAByteOrderMarkAndNoFrontmatterGetsItAfterTheMark() throws IOException {
        init("skill");
        var complete = read(fileOf("skill"));
        var start = complete.indexOf(START);
        Files.writeString(fileOf("skill"), "\uFEFFNotes.\n" + complete.substring(start));

        init("skill");

        assertThat(read(fileOf("skill")))
                .isEqualTo("\uFEFF" + complete.substring(0, start) + "Notes.\n" + complete.substring(start));
    }

    @Test
    void aNewFileHasUnixLineEndings() throws IOException {
        init("snippet");

        assertThat(read(fileOf("snippet"))).doesNotContain("\r");
    }

    @Test
    void anEndBeforeTheStartDoesNotCloseTheBlock() throws IOException {
        var broken = END + "\n" + START + "\n";
        Files.writeString(fileOf("snippet"), broken);

        assertThat(init("snippet")).isEqualTo(2);

        assertThat(err()).contains("init-agent-block-unfinished");
        assertThat(read(fileOf("snippet"))).isEqualTo(broken);
    }

    @ParameterizedTest
    @MethodSource("forms")
    void aDryRunWritesNothingAndPrintsWhatItWouldWrite(String form) throws IOException {
        assertThat(init("--dry-run", form)).isZero();

        assertThat(err()).isEmpty();
        assertThat(out())
                .startsWith("dry run, nothing written" + System.lineSeparator() + "would create " + fileOf(form)
                        + System.lineSeparator() + System.lineSeparator())
                .contains(START + "\n")
                .contains("## Java with gabier".substring(form.equals("snippet") ? 0 : 1));
        try (var files = Files.list(directory)) {
            assertThat(files).isEmpty();
        }
    }

    @Test
    void aDryRunOnAnExistingFileSaysWhatItWouldDoAndLeavesItAlone() throws IOException {
        Files.writeString(fileOf("snippet"), "Build with make.\n");

        assertThat(init("--dry-run", "snippet")).isZero();

        assertThat(out()).contains("would append the gabier block to " + fileOf("snippet"));
        assertThat(read(fileOf("snippet"))).isEqualTo("Build with make.\n");
    }

    @Test
    void aDryRunStillRefusesWhatARealRunWouldRefuse() throws IOException {
        Files.createDirectories(fileOf("rule").getParent());
        Files.writeString(fileOf("rule"), "My own text.\n");

        assertThat(init("--dry-run", "rule")).isEqualTo(2);

        assertThat(out()).isEmpty();
        assertThat(err()).contains("init-agent-file-exists");
    }

    @Test
    void aDryRunWithForceSaysItWouldOverwrite() throws IOException {
        Files.createDirectories(fileOf("rule").getParent());
        Files.writeString(fileOf("rule"), "My own text.\n");

        assertThat(init("--dry-run", "--force", "rule")).isZero();

        assertThat(out()).contains("would overwrite " + fileOf("rule"));
        assertThat(read(fileOf("rule"))).isEqualTo("My own text.\n");
    }

    @Test
    void aDryRunOfAReplacementPrintsTheBlockOnly() throws IOException {
        init("snippet");
        out.reset();

        assertThat(init("--dry-run", "snippet")).isZero();

        assertThat(out())
                .startsWith("dry run, nothing written" + System.lineSeparator() + "would replace the gabier"
                        + " block of " + fileOf("snippet"));
    }

    @Test
    void theOptionsMayStandAnywhereAfterTheCommand() throws IOException {
        assertThat(run("init-agent", "snippet", "--dry-run", "--target=" + directory))
                .isZero();

        assertThat(out()).contains("would create " + fileOf("snippet"));
    }

    @Test
    void onlyKeepsTheLinesThatCiteNoCommandAndTheBulletsThatCiteAChosenOne() throws IOException {
        assertThat(init("--only=rename,diagnostics", "snippet")).isZero();

        var written = read(fileOf("snippet"));
        assertThat(written)
                .contains("## Java with gabier")
                .contains("Change Java through a language server with `gabier`, not by editing text.")
                .contains("`gabier rename`")
                .contains("`gabier extract-method`")
                .contains("and `gabier create`, not by editing every occurrence.")
                .contains("`gabier diagnostics <file>`")
                .doesNotContain("`gabier start`")
                .doesNotContain("`gabier stop`")
                .contains("Try a change with `--dry-run` first")
                .doesNotContain("organize-imports")
                .doesNotContain("Before editing Java by hand");
    }

    @Test
    void onlyDropsAWholeBulletEvenWhenItRunsOverSeveralLines() throws IOException {
        init("--only=stop", "snippet");

        var written = read(fileOf("snippet"));
        assertThat(written).contains("`gabier stop`").doesNotContain("rename").doesNotContain("every occurrence");
        assertThat(written).doesNotContain("`gabier diagnostics").doesNotContain("`gabier actions`");
    }

    @Test
    void onlyHelpKeepsTheBulletsThatAskTheHelp() throws IOException {
        init("--only=help", "rule");

        assertThat(read(fileOf("rule")))
                .contains("`gabier help --for=")
                .contains("`gabier help <command>`")
                .doesNotContain("`gabier start`");
    }

    @Test
    void onlyWithACommandThatDoesNotExistIsRefusedAndWritesNothing() throws IOException {
        assertThat(init("--only=rename,frobnicate", "skill")).isEqualTo(2);

        assertThat(out()).isEmpty();
        assertThat(err())
                .startsWith("Init agent unknown command: ")
                .contains("frobnicate")
                .contains("init-agent-unknown-command")
                .contains("command=frobnicate")
                .endsWith(POINTER + System.lineSeparator());
        try (var files = Files.list(directory)) {
            assertThat(files).isEmpty();
        }
    }

    @Test
    void onlyWithATopicOfTheHelpIsNotACommand() {
        assertThat(init("--only=guide", "skill")).isEqualTo(2);

        assertThat(err()).contains("init-agent-unknown-command");
    }

    @Test
    void onlyWithNothingIsAUsageError() {
        assertThat(init("--only=", "skill")).isEqualTo(2);

        assertThat(err()).isEqualTo(usageText("init-agent --target=" + directory + " --only= skill"));
    }

    @Test
    void onlyWithAnEmptyNameInTheListIsAUsageError() {
        assertThat(init("--only=rename,,move", "skill")).isEqualTo(2);

        assertThat(err()).isEqualTo(usageText("init-agent --target=" + directory + " --only=rename,,move skill"));
    }

    @Test
    void aRefusalAsJsonIsTheProblemObjectWithThePointer() throws IOException {
        Files.createDirectories(fileOf("rule").getParent());
        Files.writeString(fileOf("rule"), "My own text.\n");

        assertThat(run("--json", "init-agent", "--target=" + directory, "rule")).isEqualTo(2);

        assertThat(out()).isEmpty();
        assertThat(err())
                .startsWith("{\"type\":\"urn:gabier:error:init-agent-file-exists\"")
                .contains("\"key\":\"init-agent-file-exists\"")
                .endsWith(",\"help\":\"" + POINTER + "\"}" + System.lineSeparator());
    }

    @Test
    void theAnswerAsJsonNamesTheFormTheFileAndWhatWasDone() throws IOException {
        assertThat(run("--json", "init-agent", "--target=" + directory, "rule")).isZero();

        assertThat(out().lines()).hasSize(1);
        var answer = Json.parse(out());
        assertThat(answer)
                .isEqualTo(Map.of(
                        "form", "rule", "file", fileOf("rule").toString(), "action", "created", "dryRun", false));
    }

    @Test
    void theDryRunAsJsonHasTheContentItWouldWrite() throws IOException {
        assertThat(run("--json", "init-agent", "--target=" + directory, "--dry-run", "snippet"))
                .isZero();

        var answer = (Map<?, ?>) Json.parse(out());
        assertThat(answer.get("dryRun")).isEqualTo(true);
        assertThat(answer.get("action")).isEqualTo("created");
        assertThat((String) answer.get("content")).startsWith(START + "\n").endsWith(END + "\n");
        assertThat(fileOf("snippet")).doesNotExist();
    }

    @Test
    void withoutATargetTheFolderTheCliRunsInIsTheProject() {
        assertThat(run("init-agent", "--dry-run", "snippet")).isZero();

        assertThat(out())
                .contains("would " + "create "
                        + Path.of("").toAbsolutePath().resolve("AGENTS.md").normalize() + System.lineSeparator())
                .doesNotContain(directory.toString());
    }

    @Test
    void aTargetThatDoesNotExistIsRefused() {
        assertThat(run("init-agent", "--target=" + directory.resolve("nowhere"), "snippet"))
                .isEqualTo(2);

        assertThat(err()).contains("init-agent-no-such-folder").endsWith(POINTER + System.lineSeparator());
        assertThat(directory.resolve("nowhere")).doesNotExist();
    }

    @Test
    void aTargetThatIsAFileIsRefused() throws IOException {
        var file = Files.writeString(directory.resolve("file"), "x");

        assertThat(run("init-agent", "--target=" + file, "snippet")).isEqualTo(2);

        assertThat(err()).contains("init-agent-no-such-folder");
    }

    @Test
    void anEmptyTargetIsAUsageError() {
        assertThat(run("init-agent", "--target=", "snippet")).isEqualTo(2);

        assertThat(err()).isEqualTo(usageText("init-agent --target= snippet"));
    }

    @ParameterizedTest
    @MethodSource("badArguments")
    void aCallThatIsNotTheUsageIsAUsageErrorAndWritesNothing(List<String> arguments) throws IOException {
        var words = Stream.concat(Stream.of("init-agent", "--target=" + directory), arguments.stream())
                .toArray(String[]::new);

        assertThat(run(words)).isEqualTo(2);

        assertThat(err()).isEqualTo(usageText(String.join(" ", words)));
        try (var files = Files.list(directory)) {
            assertThat(files).isEmpty();
        }
    }

    static Stream<List<String>> badArguments() {
        return Stream.of(
                List.of(),
                List.of("poster"),
                List.of("skill", "rule"),
                List.of("skill", "--bogus"),
                List.of("--dry-run"),
                List.of("Skill"));
    }

    @Test
    void theFolderOfAFormThatIsAFileIsAFailureOfTheEnvironmentNotARefusal() throws IOException {
        Files.writeString(directory.resolve(".claude"), "not a folder");

        assertThat(init("skill")).isEqualTo(1);

        assertThat(err()).contains("init-agent-write-failed").doesNotContain(POINTER);
    }

    @Test
    void aFileThatCannotBeReadIsAFailureOfTheEnvironment() throws IOException {
        Files.createDirectory(directory.resolve("AGENTS.md"));

        assertThat(init("snippet")).isEqualTo(1);

        assertThat(err()).contains("init-agent-write-failed");
    }

    @Test
    void aFolderOfTheFormThatLeadsOutsideTheTargetIsRefusedAndNothingIsWrittenThere() throws IOException {
        var outside = Files.createTempDirectory("outside");
        try {
            var project = Files.createDirectory(directory.resolve("project"));
            Files.createSymbolicLink(project.resolve(".claude"), outside);

            assertThat(run("init-agent", "--target=" + project, "skill")).isEqualTo(2);

            assertThat(err()).contains("init-agent-outside-folder").endsWith(POINTER + System.lineSeparator());
            try (var files = Files.list(outside)) {
                assertThat(files).isEmpty();
            }
        } finally {
            Files.deleteIfExists(outside);
        }
    }

    @Test
    void aFileThatIsALinkToAFileOutsideTheTargetIsRefusedAndTheOutsideFileIsLeftAlone() throws IOException {
        var outside = Files.writeString(Files.createTempFile("outside", ".md"), "Not yours.\n");
        try {
            var project = Files.createDirectory(directory.resolve("project"));
            Files.createSymbolicLink(project.resolve("AGENTS.md"), outside);

            assertThat(run("init-agent", "--target=" + project, "snippet")).isEqualTo(2);

            assertThat(err()).contains("init-agent-outside-folder");
            assertThat(read(outside)).isEqualTo("Not yours.\n");
        } finally {
            Files.deleteIfExists(outside);
        }
    }

    @Test
    void aFileThatIsALinkToAFileInsideTheTargetIsWrittenThroughAndTheLinkIsKept() throws IOException {
        var claude = Files.writeString(directory.resolve("CLAUDE.md"), "Claude text.\n");
        Files.createSymbolicLink(fileOf("snippet"), claude.getFileName());

        assertThat(init("snippet")).isZero();

        assertThat(Files.isSymbolicLink(fileOf("snippet"))).isTrue();
        assertThat(read(claude)).startsWith("Claude text.\n\n" + START + "\n");
    }

    @Test
    void aFileThatIsADanglingLinkIsReplacedByARegularFileAndNothingIsWrittenThroughIt() throws IOException {
        Files.createSymbolicLink(fileOf("snippet"), Path.of("missing.md"));

        assertThat(init("snippet")).isZero();

        assertThat(out()).isEqualTo("created " + fileOf("snippet") + System.lineSeparator());
        assertThat(Files.isSymbolicLink(fileOf("snippet"))).isFalse();
        assertThat(read(fileOf("snippet"))).startsWith(START + "\n");
        assertThat(directory.resolve("missing.md")).doesNotExist();
    }

    @Test
    void aTargetThatIsALinkToAFolderIsTheProject() throws IOException {
        var project = Files.createDirectory(directory.resolve("project"));
        var link = Files.createSymbolicLink(directory.resolve("link"), project);

        assertThat(run("init-agent", "--target=" + link, "snippet")).isZero();

        assertThat(out()).isEqualTo("created " + link.resolve("AGENTS.md") + System.lineSeparator());
        assertThat(read(project.resolve("AGENTS.md"))).startsWith(START + "\n");
    }

    @Test
    void aDryRunRefusesALinkThatLeadsOutOfTheTargetAndWritesNothing() throws IOException {
        var outside = Files.writeString(Files.createTempFile("outside", ".md"), "Not yours.\n");
        try {
            var project = Files.createDirectory(directory.resolve("project"));
            Files.createSymbolicLink(project.resolve("AGENTS.md"), outside);

            assertThat(run("init-agent", "--dry-run", "--target=" + project, "snippet"))
                    .isEqualTo(2);

            assertThat(out()).isEmpty();
            assertThat(err()).contains("init-agent-outside-folder");
            assertThat(read(outside)).isEqualTo("Not yours.\n");
        } finally {
            Files.deleteIfExists(outside);
        }
    }

    @Test
    void aFileThatAppearsBetweenThePlanAndTheWriteIsNotClobbered() throws IOException {
        var plan = AgentFile.plan(AgentForm.SNIPPET, directory, "block", false);
        Files.writeString(fileOf("snippet"), "Written by someone else.\n");

        assertThatThrownBy(() -> AgentFile.write(plan)).isInstanceOfSatisfying(InitAgentException.class, e -> {
            assertThat(e.key().get()).isEqualTo("init-agent-file-exists");
            assertThat(e.getMessage())
                    .contains("created while gabier was writing")
                    .contains("run the command again")
                    .doesNotContain("--force")
                    .doesNotContain("did not write");
        });

        assertThat(read(fileOf("snippet"))).isEqualTo("Written by someone else.\n");
        assertThat(filesIn(directory)).containsExactly(fileOf("snippet"));
    }

    private static void noHardLinks(Path link, Path existing) throws IOException {
        throw new FileSystemException(link.toString(), existing.toString(), "Operation not permitted");
    }

    @Test
    void aNewFileIsCreatedOnAFileSystemWithNoHardLinks() throws IOException {
        var plan = AgentFile.plan(AgentForm.SNIPPET, directory, "block", false);

        AgentFile.write(plan, InitAgentTest::noHardLinks);

        assertThat(read(fileOf("snippet"))).isEqualTo(plan.content());
        assertThat(filesIn(directory)).containsExactly(fileOf("snippet"));
    }

    @Test
    void aFileThatAppearsOnAFileSystemWithNoHardLinksIsNotClobberedEither() throws IOException {
        var plan = AgentFile.plan(AgentForm.SNIPPET, directory, "block", false);
        Files.writeString(fileOf("snippet"), "Written by someone else.\n");

        assertThatThrownBy(() -> AgentFile.write(plan, InitAgentTest::noHardLinks))
                .isInstanceOfSatisfying(
                        InitAgentException.class, e -> assertThat(e.key().get()).isEqualTo("init-agent-file-exists"));

        assertThat(read(fileOf("snippet"))).isEqualTo("Written by someone else.\n");
        assertThat(filesIn(directory)).containsExactly(fileOf("snippet"));
    }

    @Test
    void aMoveThatFailsLeavesNoTemporaryFileBehind() throws IOException {
        Files.writeString(fileOf("snippet"), "Build with make.\n");
        var plan = AgentFile.plan(AgentForm.SNIPPET, directory, "block", false);
        Files.delete(fileOf("snippet"));
        Files.createDirectories(fileOf("snippet").resolve("not-empty"));

        assertThatThrownBy(() -> AgentFile.write(plan))
                .isInstanceOfSatisfying(
                        InitAgentException.class, e -> assertThat(e.key().get()).isEqualTo("init-agent-write-failed"));

        assertThat(filesIn(directory)).isEmpty();
        try (var entries = Files.list(directory)) {
            assertThat(entries).containsExactly(fileOf("snippet"));
        }
    }

    private static List<Path> filesIn(Path folder) throws IOException {
        try (var files = Files.walk(folder)) {
            return files.filter(Files::isRegularFile).toList();
        }
    }

    @Test
    void helpOfTheCommandListsItsUsageAndItsRefusals() {
        assertThat(run("help", "init-agent")).isZero();

        assertThat(out())
                .contains("gabier [--json] init-agent [--dry-run] [--force]")
                .contains("<skill|rule|snippet>")
                .contains("init-agent-file-exists:")
                .contains("init-agent-block-repeated:")
                .contains("init-agent-fence-unclosed:")
                .contains("cuts per bullet: a bullet that cites several commands is kept whole")
                .contains("init-agent-unknown-command:");
    }

    @Test
    void theCommandNeverStartsADaemonNorOpensTheSocket() throws IOException {
        var marker = directory.resolve("started");
        var bin = directory.resolve("bin");
        FakeDaemonScripts.install(bin, "echo started > '" + marker + "'");
        var socket = directory.resolve("gabier.sock");
        var environment = Map.of("GABIER_SOCKET", socket.toString(), "PATH", bin.toString());

        assertThat(run(environment, "init-agent", "--target=" + directory, "snippet"))
                .isZero();

        assertThat(marker).doesNotExist();
        assertThat(socket).doesNotExist();
    }

    @Test
    void theHelpListsTheCommand() {
        run("help");

        assertThat(out()).contains("  init-agent");
    }
}
