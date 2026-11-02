package fr.craft.gabier.editing.fixtures;

import static fr.craft.gabier.editing.fixtures.Scenarios.edits;
import static fr.craft.gabier.editing.fixtures.Scenarios.replace;

import fr.craft.gabier.editing.domain.ChangeSet;
import fr.craft.gabier.editing.domain.CleanUp;
import fr.craft.gabier.editing.domain.LanguageServerErrorKey;
import fr.craft.gabier.editing.fixtures.Scenario.Answer;
import fr.craft.gabier.editing.fixtures.Scenario.Request;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * The contract scenarios of the clean-ups of a file ({@code gabier cleanup}), kept apart from {@link Scenarios}: they
 * are part of its catalogue ({@link Scenarios#all()}) and are replayed like the others.
 *
 * <p>jdtls is configured, not fought: the request {@code java/cleanup} runs the identifiers of its setting {@code
 * java.cleanup.actions} (Eclipse JDT LS, {@code SaveActionHandler.performManualCleanupActions}), so the adapter sets
 * the setting to the identifiers asked for, in the order given, before the request. jdtls answers one edit that covers
 * the whole text of the file (the clean-ups may overlap, which the protocol does not allow), and none when no clean-up
 * changes the text ({@link #CLEAN_UP_A_CONCATENATION_INTO_A_TEXT_BLOCK}, {@link #CLEAN_UP_A_FILE_THAT_IS_CLEAN}). Several
 * identifiers are applied one after the other on the text the previous one left ({@link
 * #CLEAN_UP_WITH_TWO_CLEAN_UPS}). An identifier jdtls does not know is only logged by jdtls, so it has no scenario:
 * gabier refuses it before it asks ({@link CleanUp}).
 */
public final class CleanUpScenarios {

    static final Path BANNER = Path.of("src/main/java/demo/Banner.java");
    private static final Path NOTICE = Path.of("src/main/java/demo/Notice.java");
    private static final Path PLAIN = Path.of("src/main/java/demo/Plain.java");
    private static final Path ELSEWHERE = Path.of("../outside/Elsewhere.java");

    static final Map<Path, String> PROJECT = Map.of(
            BANNER, """
            package demo;

            public class Banner {
                String greeting() {
                    return "Hello, \\"world\\"!\\n"
                            + "Welcome to demo.\\n"
                            + "Bye.\\n";
                }
            }
            """,
            NOTICE, """
            package demo;

            public class Notice {
                String text() {
                    String title = "Notice: \\"closed\\"\\n"
                            + "Back soon.\\n"
                            + "Thanks.\\n";
                    return title;
                }
            }
            """,
            PLAIN, """
            package demo;

            public class Plain {
                String name() {
                    return "plain";
                }
            }
            """);

    /**
     * {@code Banner}: the concatenation of three literals, one of them with escaped quotes, becomes a text block, in
     * which the quotes are no longer escaped, its lines indented four columns more than the {@code return} (as jdtls
     * writes them). The edit covers the whole file, to the start of the line after the last. jdtls leaves alone a
     * concatenation of two literals: three are the least it rewrites (see {@link #CLEAN_UP_WITH_TWO_CLEAN_UPS}).
     */
    public static final Scenario CLEAN_UP_A_CONCATENATION_INTO_A_TEXT_BLOCK = new Scenario(
            "clean-up-a-concatenation-into-a-text-block",
            PROJECT,
            new Request.CleanUpFile(BANNER, List.of(CleanUp.STRING_CONCAT_TO_TEXT_BLOCK)),
            new Answer.CleanedUp(new ChangeSet(List.of(edits(BANNER, replace(0, 0, 9, 0, """
                package demo;

                public class Banner {
                    String greeting() {
                        return \"""
                            Hello, "world"!
                            Welcome to demo.
                            Bye.
                            \""";
                    }
                }
                """))))));

    /**
     * {@code Notice}, with {@code addFinalModifier} then {@code stringConcatToTextBlock}: the local variable becomes
     * {@code final} and its concatenation of three literals a text block, both in the one edit that jdtls answers.
     */
    public static final Scenario CLEAN_UP_WITH_TWO_CLEAN_UPS = new Scenario(
            "clean-up-with-two-clean-ups",
            PROJECT,
            new Request.CleanUpFile(NOTICE, List.of(CleanUp.ADD_FINAL_MODIFIER, CleanUp.STRING_CONCAT_TO_TEXT_BLOCK)),
            new Answer.CleanedUp(new ChangeSet(List.of(edits(NOTICE, replace(0, 0, 10, 0, """
                package demo;

                public class Notice {
                    String text() {
                        final String title = \"""
                            Notice: "closed"
                            Back soon.
                            Thanks.
                            \""";
                        return title;
                    }
                }
                """))))));

    /** {@code Plain} has no concatenation: jdtls has no edit to give, which is not a refusal. */
    public static final Scenario CLEAN_UP_A_FILE_THAT_IS_CLEAN = new Scenario(
            "clean-up-a-file-that-is-clean",
            PROJECT,
            new Request.CleanUpFile(PLAIN, List.of(CleanUp.STRING_CONCAT_TO_TEXT_BLOCK)),
            new Answer.AlreadyClean());

    /** The project is still being imported: the server cannot say what the clean-up changes. */
    public static final Scenario CLEAN_UP_WHILE_THE_SERVER_IMPORTS = new Scenario(
            "clean-up-while-the-server-imports",
            PROJECT,
            new Request.CleanUpFile(BANNER, List.of(CleanUp.STRING_CONCAT_TO_TEXT_BLOCK)),
            new Answer.Refused(LanguageServerErrorKey.NOT_READY));

    /** A file that is not in the project the server was started on. */
    public static final Scenario CLEAN_UP_OUTSIDE_THE_PROJECT = new Scenario(
            "clean-up-a-file-outside-the-project",
            PROJECT,
            new Request.CleanUpFile(ELSEWHERE, List.of(CleanUp.STRING_CONCAT_TO_TEXT_BLOCK)),
            new Answer.Refused(LanguageServerErrorKey.FILE_OUTSIDE_PROJECT));

    private CleanUpScenarios() {}

    /** Every scenario of the clean-ups. */
    public static List<Scenario> all() {
        return List.of(
                CLEAN_UP_A_CONCATENATION_INTO_A_TEXT_BLOCK,
                CLEAN_UP_WITH_TWO_CLEAN_UPS,
                CLEAN_UP_A_FILE_THAT_IS_CLEAN,
                CLEAN_UP_WHILE_THE_SERVER_IMPORTS,
                CLEAN_UP_OUTSIDE_THE_PROJECT);
    }
}
