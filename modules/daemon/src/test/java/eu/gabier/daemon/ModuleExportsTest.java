package eu.gabier.daemon;

import static org.assertj.core.api.Assertions.assertThat;

import eu.gabier.editing.domain.Position;
import eu.gabier.editing.domain.Range;
import eu.gabier.editing.domain.TextEdit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The edits of a module descriptor when the classes of a package all move to another: the {@code exports} and {@code
 * opens} of the package follow them. The tests apply the edits to the text, the effect being what a module is judged
 * on.
 */
class ModuleExportsTest {

    private static final Map<String, String> SHARED_TO_MONEY = Map.of("demo.shared", "demo.shared.money");

    @Test
    void renamesTheExportsAndTheOpensOfTheEmptiedPackage() {
        String descriptor = """
            module demo.shared {
                exports demo.shared;
                opens demo.shared;
            }
            """;

        assertThat(applied(descriptor, SHARED_TO_MONEY)).isEqualTo("""
            module demo.shared {
                exports demo.shared.money;
                opens demo.shared.money;
            }
            """);
    }

    @Test
    void keepsTheModulesThatTheDirectiveIsRestrictedTo() {
        String descriptor = """
            module demo.shared {
                exports demo.shared to demo.app, demo.test;
                opens demo.shared
                    to demo.app;
            }
            """;

        assertThat(applied(descriptor, SHARED_TO_MONEY)).isEqualTo("""
            module demo.shared {
                exports demo.shared.money to demo.app, demo.test;
                opens demo.shared.money
                    to demo.app;
            }
            """);
    }

    @Test
    void doesNotTouchAPackageWhoseNameStartsWithTheEmptiedOne() {
        String descriptor = """
            module demo.shared {
                exports demo.shared.api;
                exports demo.sharedtools;
                opens demo.shared.api.impl;
            }
            """;

        assertThat(editsOf(descriptor, SHARED_TO_MONEY)).isEmpty();
    }

    @Test
    void doesNotTouchWhatIsNotExportedOrOpened() {
        String descriptor = """
            module demo.shared {
                requires demo.shared.tools;
                uses demo.shared.Plugin;
                provides demo.shared.Plugin with demo.shared.Impl;
            }
            """;

        assertThat(editsOf(descriptor, SHARED_TO_MONEY)).isEmpty();
    }

    @Test
    void ignoresADirectiveInAComment() {
        String descriptor = """
            module demo.shared {
                // exports demo.shared;
                /* opens demo.shared;
                   exports demo.shared; */
                exports demo.shared;
            }
            """;

        assertThat(applied(descriptor, SHARED_TO_MONEY)).isEqualTo("""
            module demo.shared {
                // exports demo.shared;
                /* opens demo.shared;
                   exports demo.shared; */
                exports demo.shared.money;
            }
            """);
    }

    @Test
    void dropsTheDirectiveOfAPackageThatTheTargetAlreadyHasWithTheSameKeyword() {
        String descriptor = """
            module demo.shared {
                exports demo.shared;
                exports demo.shared.money;
                opens demo.shared;
            }
            """;

        assertThat(applied(descriptor, SHARED_TO_MONEY)).isEqualTo("""
            module demo.shared {
                exports demo.shared.money;
                opens demo.shared.money;
            }
            """);
    }

    @Test
    void dropsTheSecondRenameOntoThePackageTheFirstOneTook() {
        String descriptor = """
            module demo.shared {
                exports demo.a;
                exports demo.b;
            }
            """;

        assertThat(applied(descriptor, Map.of("demo.a", "demo.c", "demo.b", "demo.c")))
                .isEqualTo("""
                    module demo.shared {
                        exports demo.c;
                    }
                    """);
    }

    @Test
    void dropsOnlyTheDirectiveWhenTheLineHoldsOtherCode() {
        String descriptor = "module demo.shared { exports demo.shared; exports demo.shared.money; }\n";

        assertThat(applied(descriptor, SHARED_TO_MONEY))
                .isEqualTo("module demo.shared {  exports demo.shared.money; }\n");
    }

    @Test
    void dropsOnlyTheDirectiveWhenSomethingFollowsItOnTheLine() {
        String descriptor = "module demo.shared {\n"
                + "    exports demo.shared; opens demo.shared;\n"
                + "    exports demo.shared.money;\n"
                + "}\n";

        assertThat(applied(descriptor, SHARED_TO_MONEY))
                .isEqualTo("module demo.shared {\n     opens demo.shared.money;\n    exports demo.shared.money;\n}\n");
    }

    @Test
    void dropsTheLastLineOfATextWithNoLineBreakAtTheEnd() {
        String descriptor = "module demo.shared {\n    exports demo.shared.money;\n    exports demo.shared;";

        assertThat(applied(descriptor, SHARED_TO_MONEY))
                .isEqualTo("module demo.shared {\n    exports demo.shared.money;\n");
    }

    @Test
    void countsTheColumnsOfALineThatEndsWithACarriageReturn() {
        String descriptor = "module demo.shared {\r\n    exports demo.shared;\r\n    opens demo.shared;\r\n}\r\n";

        assertThat(applied(descriptor, SHARED_TO_MONEY))
                .isEqualTo("module demo.shared {\r\n"
                        + "    exports demo.shared.money;\r\n"
                        + "    opens demo.shared.money;\r\n"
                        + "}\r\n");
    }

    @Test
    void changesNothingWhenNoPackageIsEmptied() {
        String descriptor = """
            module demo.shared {
                exports demo.shared;
            }
            """;

        assertThat(editsOf(descriptor, Map.of())).isEmpty();
    }

    private static List<TextEdit> editsOf(String descriptor, Map<String, String> renamed) {
        return ModuleExports.following(descriptor, renamed);
    }

    /** The text once the edits are applied, the last first so that the earlier positions stay true. */
    private static String applied(String descriptor, Map<String, String> renamed) {
        List<TextEdit> edits = new ArrayList<>(editsOf(descriptor, renamed));
        edits.sort(Comparator.comparing((TextEdit edit) -> edit.range().start().line())
                .thenComparing(edit -> edit.range().start().column())
                .reversed());
        String text = descriptor;
        for (TextEdit edit : edits) {
            text = text.substring(0, offset(text, edit.range().start()))
                    + edit.newText()
                    + text.substring(offset(text, edit.range().end()));
        }

        return text;
    }

    private static int offset(String text, Position position) {
        int offset = 0;
        for (int line = 0; line < position.line(); line++) {
            offset = text.indexOf('\n', offset) + 1;
        }

        return offset + position.column();
    }

    @Test
    void writesEditsThatDoNotOverlap() {
        String descriptor = """
            module demo.shared {
                exports demo.shared;
                opens demo.shared;
            }
            """;

        List<Range> ranges = editsOf(descriptor, SHARED_TO_MONEY).stream()
                .map(TextEdit::range)
                .toList();

        assertThat(ranges).hasSize(2);
        assertThat(ranges.get(0).end().line()).isLessThan(ranges.get(1).start().line());
    }
}
