package eu.gabier.editing.fixtures;

import static eu.gabier.editing.fixtures.Scenarios.edits;
import static eu.gabier.editing.fixtures.Scenarios.replace;

import eu.gabier.editing.domain.ChangeSet;
import eu.gabier.editing.domain.LanguageServerErrorKey;
import eu.gabier.editing.domain.OfferedAction;
import eu.gabier.editing.domain.OfferedActions;
import eu.gabier.editing.domain.Position;
import eu.gabier.editing.domain.Range;
import eu.gabier.editing.fixtures.Scenario.Answer;
import eu.gabier.editing.fixtures.Scenario.Request;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * The contract scenarios of the code actions of a range ({@code gabier actions}, {@code gabier apply-action}), kept
 * apart from {@link Scenarios}: they are part of its catalogue ({@link Scenarios#all()}) and are replayed like the
 * others.
 *
 * <p>jdtls is configured, not fought: these are the standard requests {@code textDocument/codeAction} and {@code
 * codeAction/resolve} (Eclipse JDT LS, {@code CodeActionHandler}), and what the adapter sets is what the handler reads.
 *
 * <ul>
 *   <li>A fix is computed only for the diagnostics the request carries in its context: without them jdtls offers none
 *       ({@link #CODE_ACTIONS_ON_A_TYPE_THAT_IS_NOT_IMPORTED}), so the adapter sends those of the file that touch the
 *       range, which it has from the publication of the diagnostics.
 *   <li>The request names the kinds it wants ({@code only}: {@code quickfix}, {@code refactor} and {@code quickassist}).
 *       The {@code source} actions (organize the imports, generate accessors) are about a whole file and jdtls offers
 *       them on every range: they are left out, so that a range with nothing to offer has an empty answer ({@link
 *       #CODE_ACTIONS_WHERE_NOTHING_IS_OFFERED}). {@code quickassist} is jdtls's own kind for the rewrites of code
 *       (a concatenation to a text block, {@link #CODE_ACTIONS_ON_A_CONCATENATION}): jdtls drops an action whose kind the
 *       client has not declared, so the adapter declares it ({@code JdtlsCapabilities.QUICK_ASSIST}).
 *   <li>The edit of an action is computed when it is chosen ({@code codeAction/resolve}, the client declares {@code
 *       resolveSupport} for {@code edit}). An action with no edit once resolved is a command for an editor, as the
 *       "Change signature" that opens a dialog: gabier refuses it ({@link #APPLY_AN_ACTION_THAT_IS_A_COMMAND}).
 *   <li>The titles are the server's text and gabier does not read them. What these scenarios hold is what the replay
 *       checks the server to offer, at least: a server that offers more is not at fault.
 * </ul>
 */
public final class CodeActionScenarios {

    private static final Path OFFERS = Path.of("src/main/java/demo/Offers.java");
    private static final Path ELSEWHERE = Path.of("../outside/Elsewhere.java");

    /** A method whose return type {@code List} is not imported, a blank line, and a method with nothing to offer. */
    private static final Map<Path, String> PROJECT = Map.of(OFFERS, """
        package demo;

        public class Offers {
            List<String> names() {
                return null;
            }

            int count() {
                return 1;
            }
        }
        """);

    private static final OfferedAction IMPORT_LIST = new OfferedAction("quickfix", "Import 'List' (java.util)");
    private static final OfferedAction TEXT_BLOCK =
            new OfferedAction("quickassist", "Convert String concatenation to Text Block");
    private static final OfferedAction CHANGE_SIGNATURE =
            new OfferedAction("refactor.change.signature", "Change signature for 'names'");

    /** {@code List} in the return type of {@code names}. */
    private static final Range THE_TYPE = range(3, 4, 3, 8);

    /** The name {@code names}, where jdtls offers to change the signature. */
    private static final Range THE_NAME = range(3, 17, 3, 22);

    /** The three literals of the concatenation of {@code Banner}, from the first character to the end of the last. */
    private static final Range THE_CONCATENATION = range(4, 15, 6, 26);

    /** The blank line between the two methods of {@code Offers}. */
    private static final Range A_BLANK_LINE = range(6, 0, 6, 0);

    /**
     * {@code List} is not imported, so it is an error: jdtls offers to import it, from the diagnostic that the adapter
     * puts in the context of the request.
     */
    public static final Scenario CODE_ACTIONS_ON_A_TYPE_THAT_IS_NOT_IMPORTED = new Scenario(
            "code-actions-on-a-type-that-is-not-imported",
            PROJECT,
            new Request.CodeActions(OFFERS, THE_TYPE),
            new Answer.Offered(new OfferedActions(List.of(IMPORT_LIST))));

    /**
     * A concatenation of three literals, selected from the first to the end of the last: jdtls offers to write it as a
     * text block (a quick assist, the kind {@code quickassist}).
     */
    public static final Scenario CODE_ACTIONS_ON_A_CONCATENATION = new Scenario(
            "code-actions-on-a-concatenation",
            CleanUpScenarios.PROJECT,
            new Request.CodeActions(CleanUpScenarios.BANNER, THE_CONCATENATION),
            new Answer.Offered(new OfferedActions(List.of(TEXT_BLOCK))));

    /**
     * The name of a method: jdtls offers to change its signature, with a command for the editor to run and no edit
     * ({@link #APPLY_AN_ACTION_THAT_IS_A_COMMAND}).
     */
    public static final Scenario CODE_ACTIONS_ON_THE_NAME_OF_A_METHOD = new Scenario(
            "code-actions-on-the-name-of-a-method",
            PROJECT,
            new Request.CodeActions(OFFERS, THE_NAME),
            new Answer.Offered(new OfferedActions(List.of(CHANGE_SIGNATURE))));

    /** A blank line holds no problem and no code: jdtls offers nothing there (once the actions of a file are left out). */
    public static final Scenario CODE_ACTIONS_WHERE_NOTHING_IS_OFFERED = new Scenario(
            "code-actions-where-nothing-is-offered",
            PROJECT,
            new Request.CodeActions(OFFERS, A_BLANK_LINE),
            new Answer.Offered(new OfferedActions(List.of())));

    /** The project is still being imported: the server cannot say what it offers. */
    public static final Scenario CODE_ACTIONS_WHILE_THE_SERVER_IMPORTS = new Scenario(
            "code-actions-while-the-server-imports",
            PROJECT,
            new Request.CodeActions(OFFERS, THE_TYPE),
            new Answer.Refused(LanguageServerErrorKey.NOT_READY));

    /** A file that is not in the project the server was started on. */
    public static final Scenario CODE_ACTIONS_OUTSIDE_THE_PROJECT = new Scenario(
            "code-actions-of-a-file-outside-the-project",
            PROJECT,
            new Request.CodeActions(ELSEWHERE, THE_TYPE),
            new Answer.Refused(LanguageServerErrorKey.FILE_OUTSIDE_PROJECT));

    /**
     * The import of {@code List}: the one edit, which replaces the {@code package} line and the blank line after it by
     * themselves and the import, the whole text of a file being the unit jdtls edits for imports.
     */
    public static final Scenario APPLY_THE_IMPORT_OF_A_TYPE = new Scenario(
            "apply-the-import-of-a-type",
            PROJECT,
            new Request.ApplyCodeAction(OFFERS, THE_TYPE, IMPORT_LIST),
            new Answer.CodeActionApplied(new ChangeSet(List.of(edits(OFFERS, replace(0, 0, 2, 0, """
                package demo;

                import java.util.List;

                """))))));

    /** The text block: the one edit that replaces the concatenation. */
    public static final Scenario APPLY_A_REWRITE_OF_A_CONCATENATION = new Scenario(
            "apply-a-rewrite-of-a-concatenation",
            CleanUpScenarios.PROJECT,
            new Request.ApplyCodeAction(CleanUpScenarios.BANNER, THE_CONCATENATION, TEXT_BLOCK),
            new Answer.CodeActionApplied(
                    new ChangeSet(List.of(edits(CleanUpScenarios.BANNER, replace(4, 15, 6, 26, """
                        \"""
                                    Hello, "world"!
                                    Welcome to demo.
                                    Bye.
                                    \"\"\"\
                        """))))));

    /**
     * "Change signature" is offered with a command and no edit: it opens a dialog in an editor. Once resolved it still
     * has no edit, and gabier refuses it.
     */
    public static final Scenario APPLY_AN_ACTION_THAT_IS_A_COMMAND = new Scenario(
            "apply-an-action-that-is-a-command",
            PROJECT,
            new Request.ApplyCodeAction(OFFERS, THE_NAME, CHANGE_SIGNATURE),
            new Answer.Refused(LanguageServerErrorKey.NOT_APPLICABLE));

    private CodeActionScenarios() {}

    /** Every scenario of the code actions. */
    public static List<Scenario> all() {
        return List.of(
                CODE_ACTIONS_ON_A_TYPE_THAT_IS_NOT_IMPORTED,
                CODE_ACTIONS_ON_A_CONCATENATION,
                CODE_ACTIONS_ON_THE_NAME_OF_A_METHOD,
                CODE_ACTIONS_WHERE_NOTHING_IS_OFFERED,
                CODE_ACTIONS_WHILE_THE_SERVER_IMPORTS,
                CODE_ACTIONS_OUTSIDE_THE_PROJECT,
                APPLY_THE_IMPORT_OF_A_TYPE,
                APPLY_A_REWRITE_OF_A_CONCATENATION,
                APPLY_AN_ACTION_THAT_IS_A_COMMAND);
    }

    private static Range range(int startLine, int startColumn, int endLine, int endColumn) {
        return new Range(new Position(startLine, startColumn), new Position(endLine, endColumn));
    }
}
