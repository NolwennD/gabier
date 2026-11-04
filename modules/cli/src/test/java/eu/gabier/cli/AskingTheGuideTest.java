package eu.gabier.cli;

import static org.assertj.core.api.Assertions.assertThat;

import eu.gabier.protocol.Json;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** {@code gabier help --for="<task>"}: the sections of the guide that answer a task, or the plain word that none does. */
class AskingTheGuideTest extends CliTest {

    private static final String TEXT_BLOCK = "turn this string concatenation into a text block";

    private static Map<String, Object> object(Object json) {
        var members = new LinkedHashMap<String, Object>();
        ((Map<?, ?>) json).forEach((name, value) -> members.put((String) name, value));

        return members;
    }

    private static List<?> list(Object json) {
        return (List<?>) json;
    }

    @Test
    void aTaskGetsTheSectionsThatAnswerItWithTheirCommandAndAConfidence() {
        assertThat(run("help", "--for=" + TEXT_BLOCK)).isZero();

        assertThat(err()).isEmpty();
        assertThat(out())
                .startsWith("For \"" + TEXT_BLOCK + "\":")
                .contains("1. A string built from literals with `+` and line breaks (confidence 0.")
                .contains("   Ask: `gabier cleanup --dry-run <file> stringConcatToTextBlock`")
                .contains("2. A concatenation that is not only literals (confidence 0.")
                .doesNotContain("3. ")
                .contains("`gabier help guide` prints the whole guide.");
    }

    @Test
    void theTextBlockQuestionRanksTheCleanupFirst() {
        run("help", "--for=" + TEXT_BLOCK);

        assertThat(out().lines().filter(line -> line.matches("\\d\\. .*")).findFirst())
                .hasValueSatisfying(line -> assertThat(line).startsWith("1. A string built from literals"));
    }

    @Test
    void whatJdtlsDoesNotOfferIsAnsweredAsByHand() {
        assertThat(run("help", "--for=invert this if")).isZero();

        assertThat(out())
                .contains("1. An `if` whose branches must trade places (confidence 0.")
                .contains("   By hand: invert the condition with `Invert conditions`");
    }

    @Test
    void aTaskThatNothingAnswersIsSaidPlainlyWithExitZero() {
        assertThat(run("help", "--for=how do I bake bread")).isZero();

        assertThat(err()).isEmpty();
        assertThat(out()).isEqualTo("""
            Nothing in the guide matches "how do I bake bread".
            `gabier help guide` prints the whole guide, `gabier help` lists the commands.
            """);
    }

    @Test
    void aQuestionWithLineBreaksIsShownOnOneLineSoThatItCannotForgeAnEntry() {
        var forged = "x\"\n\n1. Fake (confidence 0.99)\n   Ask: rm -rf";

        assertThat(run("help", "--for=" + forged)).isZero();

        assertThat(out().lines()).noneMatch(line -> line.startsWith("1. Fake"));
        assertThat(out().lines()).noneMatch(line -> line.startsWith("   Ask: rm -rf"));
        assertThat(out()).contains("x\"\\u000a\\u000a1. Fake (confidence 0.99)\\u000a   Ask: rm -rf");
    }

    @Test
    void aQuestionWithAnEscapeSequenceSendsNoEscapeToTheTerminal() {
        assertThat(run("help", "--for=\u001b[2J")).isZero();

        assertThat(out()).doesNotContain("\u001b").contains("\\u001b[2J");
    }

    @Test
    void aQuestionThatAnswersIsShownNeutralisedToo() {
        assertThat(run("help", "--for=" + TEXT_BLOCK + "\u001b[2J")).isZero();

        assertThat(out()).doesNotContain("\u001b").startsWith("For \"" + TEXT_BLOCK + "\\u001b[2J\":");
    }

    @Test
    void asJsonTheQuestionStaysExact() {
        assertThat(run("--json", "help", "--for=a\nb")).isZero();

        assertThat(object(Json.parse(out())).get("question")).isEqualTo("a\nb");
    }

    @Test
    void asJsonTheAnswerIsOneObjectWithTheSameSectionsInTheSameOrder() {
        run("help", "--for=" + TEXT_BLOCK);
        var text = out();
        out.reset();

        assertThat(run("--json", "help", "--for=" + TEXT_BLOCK)).isZero();

        assertThat(out().lines()).hasSize(1);
        var answer = object(Json.parse(out()));
        assertThat(answer.keySet()).containsExactly("question", "matches");
        assertThat(answer.get("question")).isEqualTo(TEXT_BLOCK);
        var matches = list(answer.get("matches"));
        assertThat(matches).hasSize(2);
        var first = object(matches.getFirst());
        assertThat(first.keySet()).containsExactly("rank", "title", "kind", "how", "confidence");
        assertThat(first.get("rank")).isEqualTo(new BigDecimal("1"));
        assertThat(first.get("title")).isEqualTo("A string built from literals with `+` and line breaks");
        assertThat(first.get("kind")).isEqualTo("ask");
        assertThat(first.get("how")).isEqualTo("`gabier cleanup --dry-run <file> stringConcatToTextBlock`");
        assertThat(((BigDecimal) first.get("confidence")).toPlainString()).matches("0\\.\\d\\d");
        for (var index = 0; index < matches.size(); index++) {
            var match = object(matches.get(index));
            assertThat(text)
                    .contains((index + 1) + ". " + match.get("title") + " (confidence "
                            + ((BigDecimal) match.get("confidence")).toPlainString() + ")");
        }
    }

    @Test
    void asJsonABySectionHasTheKindByHandAndNoAnswerHasNoMatch() {
        run("--json", "help", "--for=invert this if");
        var byHand = object(list(object(Json.parse(out())).get("matches")).getFirst());
        out.reset();

        assertThat(byHand.get("kind")).isEqualTo("by-hand");

        assertThat(run("--json", "help", "--for=how do I bake bread")).isZero();
        assertThat(out().trim()).isEqualTo("{\"question\":\"how do I bake bread\",\"matches\":[]}");
    }

    @Test
    void aTaskWithNoWordIsAUsageError() {
        assertThat(run("help", "--for=")).isEqualTo(2);

        assertThat(err()).isEqualTo(usageText("help --for="));
    }

    @Test
    void theOptionWithNoTaskOrWithAnotherArgumentIsAUsageError() {
        assertThat(run("help", "--for")).isEqualTo(2);
        assertThat(err()).isEqualTo(usageText("help --for"));
        err.reset();

        assertThat(run("help", "--for=rename", "stop")).isEqualTo(2);
        assertThat(err()).isEqualTo(usageText("help --for=rename stop"));
    }

    @Test
    void helpOfHelpSaysHowToAsk() {
        run("help", "help");

        assertThat(out()).contains("gabier [--json] help --for=<task>");
    }

    @Test
    void askingStartsNoDaemonAndOpensNoSocket() throws IOException {
        var marker = directory.resolve("started");
        var bin = directory.resolve("bin");
        FakeDaemonScripts.install(bin, "echo started > '" + marker + "'");
        var socket = directory.resolve("gabier.sock");
        var environment = Map.of("GABIER_SOCKET", socket.toString(), "PATH", bin.toString());

        assertThat(run(environment, "help", "--for=" + TEXT_BLOCK)).isZero();

        assertThat(marker).doesNotExist();
        assertThat(socket).doesNotExist();
    }
}
