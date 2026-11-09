package eu.gabier.cli.infrastructure.primary;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** A position that is not {@code line:column}, each from 1: the refusal names the operand and the form expected. */
class MalformedPositionTest extends CliTest {

    record Case(List<String> words, String position) {

        @Override
        public String toString() {
            return String.join(" ", words);
        }
    }

    static Stream<Case> badPositions() {
        return Stream.of(
                new Case(List.of("rename", "A.java", "3", "name"), "3"),
                new Case(List.of("rename", "A.java", "0:5", "name"), "0:5"),
                new Case(List.of("rename", "A.java", "3:0", "name"), "3:0"),
                new Case(List.of("rename", "A.java", "-1:5", "name"), "-1:5"),
                new Case(List.of("rename", "A.java", "three:5", "name"), "three:5"),
                new Case(List.of("rename", "A.java", "3:five", "name"), "3:five"),
                new Case(List.of("rename", "A.java", "3:1.5", "name"), "3:1.5"),
                new Case(List.of("rename", "A.java", "3:4:5", "name"), "3:4:5"),
                new Case(List.of("rename", "--dry-run", "A.java", "", "name"), ""),
                new Case(List.of("rename", "A.java", "6", "19", "welcome"), "6"),
                new Case(List.of("rename", "--dry-run", "A.java", "6", "19", "welcome"), "6"),
                new Case(List.of("rename", "A.java", "3", "5"), "3"),
                new Case(List.of("extract-variable", "A.java", "3", "3:9", "name"), "3"),
                new Case(List.of("extract-variable", "A.java", "3:4", "9", "name"), "9"),
                new Case(List.of("extract-method", "A.java", "3:4", "3:nine", "name"), "3:nine"),
                new Case(List.of("extract-constant", "A.java", "3", "3:9", "NAME"), "3"),
                new Case(List.of("extract-field", "A.java", "3", "3:9", "name"), "3"),
                new Case(List.of("move-type-to-file", "A.java", "0:4"), "0:4"),
                new Case(List.of("inline", "A.java", "3:four"), "3:four"),
                new Case(List.of("references", "A.java", "3"), "3"),
                new Case(List.of("actions", "A.java", "3", "3:9"), "3"),
                new Case(List.of("actions", "A.java", "0:4", "3:9"), "0:4"),
                new Case(List.of("apply-action", "A.java", "3:4", "0:9", "title"), "0:9"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("badPositions")
    void namesTheOperandAndTheFormExpected(Case refused) {
        assertThat(run(refused.words().toArray(String[]::new))).isEqualTo(2);

        assertThat(out()).isEmpty();
        assertThat(err())
                .isEqualTo("""
                    Usage error: Invalid position "%s"; expected <line>:<column>, each a number from 1, as in 12:5 \
                    (usage-error; arguments=%s, position=%s)
                    """.formatted(refused.position(), String.join(" ", refused.words()), refused.position()));
    }

    @Test
    void givesTheOperandAsAParameterOfTheProblemWithTheJsonOption() {
        assertThat(run("--json", "rename", "A.java", "6", "welcome")).isEqualTo(2);

        assertThat(err()).isEqualTo("""
            {"type":"urn:gabier:error:usage-error","title":"Usage error",\
            "detail":"Invalid position \\"6\\"; expected <line>:<column>, each a number from 1, as in 12:5",\
            "key":"usage-error","parameters":{"arguments":"rename A.java 6 welcome","position":"6"}}
            """);
    }
}
