package fr.craft.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NewTypeTextTest {

    @Test
    void dropsTheLineOfTheCursorOfABodyAndEndsTheFileWithALineBreak() {
        assertThat(NewTypeText.of("package demo;\n\npublic class Widget {\n\n\t${0}\n}"))
                .isEqualTo("package demo;\n\npublic class Widget {\n\n}\n");
    }

    @Test
    void emptiesTheParenthesesOfARecord() {
        assertThat(NewTypeText.of("package demo;\n\npublic record Point(${0}) {\n}"))
                .isEqualTo("package demo;\n\npublic record Point() {\n}\n");
    }

    @Test
    void keepsTheLineBreakThatEndsTheTextAlready() {
        assertThat(NewTypeText.of("public class Widget {\n\t${0}\n}\n")).isEqualTo("public class Widget {\n}\n");
    }

    @Test
    void dropsTheCursorWhateverItsIndentation() {
        assertThat(NewTypeText.of("public enum Color {\n    ${0}   \n}")).isEqualTo("public enum Color {\n}\n");
    }
}
