package eu.gabier.cli.ranking;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class WordsTest {

    static Stream<Arguments> texts() {
        return Stream.of(
                Arguments.of("stringConcatToTextBlock", List.of("string", "concat", "to", "text", "block")),
                Arguments.of("text-block, multi-line", List.of("text", "block", "multi", "line")),
                Arguments.of("XMLParser", List.of("xml", "parser")),
                Arguments.of("getHTTPResponse", List.of("get", "http", "response")),
                Arguments.of("IOException", List.of("io", "exception")),
                Arguments.of("the HTTP", List.of("the", "http")),
                Arguments.of("MAX_VALUE", List.of("max", "value")),
                Arguments.of("Java25 java.util.List", List.of("java", "java", "util", "list")),
                Arguments.of("Text BLOCK", List.of("text", "block")),
                Arguments.of("École élève", List.of("école", "élève")),
                Arguments.of("x = s.length()", List.of("length")),
                Arguments.of("  ", List.of()),
                Arguments.of("", List.of()));
    }

    @ParameterizedTest
    @MethodSource("texts")
    void aTextIsCutAtCaseChangesPunctuationHyphensAndDigitsAndLowerCased(String text, List<String> words) {
        assertThat(Words.split(text)).isEqualTo(words);
    }
}
