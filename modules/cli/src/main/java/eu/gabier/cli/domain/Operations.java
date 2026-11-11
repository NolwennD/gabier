package eu.gabier.cli.domain;

import static eu.gabier.cli.domain.Parameters.COLUMN;
import static eu.gabier.cli.domain.Parameters.DRY_RUN;
import static eu.gabier.cli.domain.Parameters.FILE;
import static eu.gabier.cli.domain.Parameters.LINE;
import static eu.gabier.cli.domain.Parameters.NAME;
import static eu.gabier.cli.domain.Parameters.NEW_NAME;
import static eu.gabier.cli.domain.Parameters.onARange;

import java.util.List;

/**
 * The operations the daemon serves, each declared once: the one place that names its parameters. {@code
 * docs/protocol.md} lists the same methods, and a test fails when the two disagree. A new operation is declared here,
 * and added to {@link #all()}.
 */
public final class Operations {

    public static final Operation STATUS =
            new Operation("status", "the project the daemon serves, and where jdtls is", """
                jdtls is in one of four states: not-started (no request has needed it yet), starting, ready, or
                failed (the reason follows in parentheses when it is known). While it is starting, the status also says
                what it is doing and for how long it has been doing it: launching jdtls (its process and its runtime
                come up), then importing the project (for a Gradle project, jdtls runs the build), with the last task
                it reported. That is the member jdtlsProgress (phase, message, spentMillis) of the result, present
                only while jdtls is starting. A request made while jdtls is starting waits for it. A daemon stops by
                itself, with jdtls, once no client has been connected for a delay (60 minutes by default, set with
                GABIER_IDLE_TIMEOUT in minutes, 0 for never): the member idle (timeoutMillis, leftMillis) of the
                result says that delay and what is left of it before this call, and is absent when the daemon never
                stops by itself. The next command starts a new daemon, so an agent does nothing about it.\
                """, List.of(), List.of());

    public static final Operation START = new Operation(
            "start",
            "start the daemon and jdtls, so that the first command finds them ready",
            """
            Start it first thing in a session, in the background, and work on something else: jdtls takes about a
            minute to start and import a Gradle project, and the first command that needs it waits for all of it.
            The answer is at once, unless the call asks to wait for jdtls: the daemon is started if none runs (its log
            is named then), jdtls starts in it unless it is already starting or ready, and the answer says where jdtls
            is. A call that waits is answered when jdtls is ready, after at most a time-out (300 seconds by default);
            while it waits, a line on the standard error says what jdtls is doing, as for any command that waits for
            it. A jdtls that is ready is left alone. When the time passes, the refusal gives the last state of jdtls;
            jdtls goes on starting in the daemon.\
            """,
            List.of(
                    "jdtls-start-failed: when waiting, jdtls failed to start (the reason follows when the daemon knows"
                            + " it)",
                    "jdtls-start-timeout: when waiting, jdtls was not ready after the time given; it keeps starting,"
                            + " and the problem says where it was"),
            List.of());

    public static final Operation STOP = new Operation("stop", "stop the daemon", "", List.of(), List.of());

    public static final Operation DIAGNOSTICS = new Operation(
            "diagnostics",
            "the errors and warnings of Java files, without a build",
            """
            The files are asked of the language server together, which waits for it to be quiet once for all of them:
            ask several files in one call rather than one call each. The result has one element per file, in the order
            given, with the problems of that file, none when it is clean. One file the language server refuses (outside
            the project, missing, a folder) refuses the whole call: no file is answered.\
            """,
            List.of(Refusals.FILE_OUTSIDE_PROJECT, Refusals.FILE_MISSING, Refusals.NOT_A_FILE, Refusals.NOT_READY),
            List.of(Parameter.paths("files", "the Java files, absolute paths in the project")));

    public static final Operation RENAME = new Operation(
            "rename",
            "rename a symbol, and everything that refers to it, in every file",
            """
            A position on the name in a package line renames the package, with its imports and its references: its files
            move to the folder of the new name and the folders left empty are removed. Give the whole new name
            (demo.shop); the segment the position is on decides how much of the old name is renamed (on app in
            demo.app.sub, demo.app); a package with no class of its own (demo in demo.app) is refused as
            language-server-empty-package. Sub-packages keep their names, and the same package in another source set
            (the tests) is a rename of its own. A package that exists, or a name that is not a package name, is
            refused as language-server-invalid-name. module-info.java, string literals and build files are not
            changed: edit them as text.\
            """,
            List.of(
                    "language-server-no-such-symbol: no symbol at the position",
                    "language-server-invalid-name: the language server declines the new name",
                    "language-server-empty-package: the package named up to the position holds no class of its own,"
                            + " so there is nothing to rename (put the position on its last segment)",
                    Refusals.FILE_OUTSIDE_PROJECT,
                    Refusals.FILE_MISSING,
                    Refusals.NOT_A_FILE,
                    Refusals.STALE_EDIT_OF_FILES,
                    Refusals.NOT_READY),
            List.of(FILE, LINE, COLUMN, NEW_NAME, DRY_RUN));

    public static final Operation RENAME_TOKEN = new Operation(
            "rename-token",
            "rename the symbol that an identifier of a file names, and everything that refers to it, in every file",
            "",
            List.of(
                    "editing-token-not-found: the identifier is not in the file",
                    "editing-file-missing: when renaming by identifier, no file is at the path",
                    Refusals.FILE_OUTSIDE_PROJECT,
                    Refusals.FILE_MISSING,
                    Refusals.NOT_A_FILE,
                    Refusals.STALE_EDIT_OF_FILES,
                    Refusals.NOT_READY),
            List.of(
                    FILE,
                    Parameter.string("token", "an identifier that occurs in the file, which names the symbol"),
                    NEW_NAME,
                    DRY_RUN));

    public static final Operation ORGANIZE_IMPORTS = new Operation(
            "organize-imports",
            "add the missing imports, remove the unused ones and order the rest as the project does",
            "",
            List.of(
                    "language-server-ambiguous-imports: several packages declare a type the file uses without"
                            + " importing it; the candidates are in the problem",
                    "language-server-unresolved-imports: jdtls would delete imports that the file uses and it reports"
                            + " an error on",
                    Refusals.FILE_OUTSIDE_PROJECT,
                    Refusals.FILE_MISSING,
                    Refusals.NOT_A_FILE,
                    Refusals.STALE_EDIT_OF_A_FILE,
                    Refusals.NOT_READY),
            List.of(FILE, DRY_RUN));

    public static final Operation CLEANUP = new Operation(
            "cleanup",
            "apply clean-ups of the language server to a file, such as stringConcatToTextBlock",
            """
            Each clean-up rewrites the whole file, in the order given, on the text the one before left.
            A clean-up applies to the whole file; for one place only, ask actions for the range, then give\
             the title of the action to apply-action (for a text block, "Convert String concatenation to\
             Text Block").
            The identifiers are those of jdtls: addDeprecated, addFinalModifier, addOverride,
            instanceofPatternMatch, invertEquals, lambdaExpression, lambdaExpressionFromAnonymousClass,
            qualifyMembers, qualifyStaticMembers, redundantComparisonStatement,
            redundantFallingThroughBlockEnd, redundantIfCondition, redundantModifiers, redundantSuperCall,
            renameUnusedLocalVariables, stringConcatToTextBlock, switchExpression, tryWithResource,
            useSwitchForInstanceofPattern.
            A file with nothing to clean up is not an error: the answer is nothing to change.
            To organize the imports, use organize-imports.\
            """,
            List.of(
                    "editing-unknown-clean-up: an identifier is not a clean-up of the language server; the problem"
                            + " lists the ones it knows (organizeImports is not one: use organize-imports)",
                    Refusals.FILE_OUTSIDE_PROJECT,
                    Refusals.FILE_MISSING,
                    Refusals.NOT_A_FILE,
                    Refusals.STALE_EDIT_OF_A_FILE,
                    Refusals.NOT_READY),
            List.of(
                    FILE,
                    Parameter.array("cleanUps", "the identifiers of the clean-ups, applied in the order given"),
                    DRY_RUN));

    public static final Operation EXTRACT_VARIABLE = extraction(
            "extract-variable",
            "turn an expression into a local variable",
            "language-server-invalid-name: the name is not a Java identifier, or is a parameter of the method");

    public static final Operation EXTRACT_METHOD = extraction(
            "extract-method",
            "turn statements, or an expression, into a method",
            "language-server-invalid-name: the name is not a Java identifier, or another method of the class"
                    + " has it with the same parameters");

    public static final Operation EXTRACT_CONSTANT = extraction(
            "extract-constant",
            "turn an expression into a constant",
            "language-server-invalid-name: the name is not a Java identifier");

    public static final Operation EXTRACT_FIELD = extraction(
            "extract-field",
            "turn an expression into a field",
            "language-server-invalid-name: the name is not a Java identifier");

    public static final Operation MOVE_TYPE_TO_FILE = new Operation(
            "move-type-to-file",
            "make a nested type a top-level type in a file of its own",
            """
            The position is inside the type to move: the innermost type that encloses it moves.
            An inner class that uses its outer instance gets a field and a constructor parameter of the outer type.
            A JUnit @Nested test class can then no longer be instantiated by JUnit.
            To avoid it, first make the class independent of the outer instance,
            for example by having it extend the shared base class, then move it.
            Each annotation of the class, such as @Nested, stays on it after the move.\
            """,
            List.of(
                    "language-server-not-movable: no nested type can be moved at the position (outside any type, a"
                            + " top-level type, or a file of the name of the type exists in its package)",
                    Refusals.FILE_OUTSIDE_PROJECT,
                    Refusals.FILE_MISSING,
                    Refusals.NOT_A_FILE,
                    "editing-file-exists: the file to create appeared since the language server answered",
                    Refusals.STALE_EDIT_OF_FILES,
                    Refusals.NOT_READY),
            List.of(FILE, LINE, COLUMN, DRY_RUN));

    public static final Operation MOVE = new Operation(
            "move",
            "move Java classes to a package, creating the package when it does not exist",
            """
            The last operand is the package, such as demo.billing; the other operands are the files to move.
            The package is created, with its folder, when it does not exist; a dry run creates nothing.
            The default package cannot be the target.
            The files must be Java sources of one source folder of the project.
            The references and the imports of the project follow the moved classes, the fully qualified names
            too, and a nested type keeps its outer type (Invoice.Line). A name in a string literal or a comment
            is not rewritten.
            jdtls does not sort the imports it writes: run organize-imports on the modified files.
            When no Java file is left in the old package, its exports and opens in module-info.java follow it.
            A member that was package-private in the old package, used from there, is no longer reachable.
            Run diagnostics on the files that use it after the move.\
            """,
            List.of(
                    """
                    language-server-not-movable-to-package: the files cannot go to the package (the reason says why:\
                     already in it, a file of that name there, not a Java source, in no source folder, in\
                     several source folders, the folder cannot be made, or the language server has no change\
                     to make)\
                    """,
                    "editing-not-a-package-name: the last operand is not a package name (the default package, a"
                            + " keyword, an empty part)",
                    Refusals.FILE_OUTSIDE_PROJECT,
                    Refusals.FILE_MISSING,
                    Refusals.NOT_A_FILE,
                    "editing-file-exists: a file to create appeared since the language server answered",
                    Refusals.STALE_EDIT_OF_FILES,
                    Refusals.NOT_READY),
            List.of(
                    Parameter.paths("files", "the Java files to move, absolute paths"),
                    Parameter.string("package", "the package to move them to, in dotted form"),
                    DRY_RUN));

    public static final Operation INLINE = new Operation(
            "inline",
            "replace a local variable, a method or a constant by its value",
            """
            The position is on the name of a local variable, a method or a constant.
            A local variable is inlined everywhere, whatever the position.
            A position on the declaration of a method or a constant inlines every use and removes it;
            a position on one use inlines that use only and keeps the declaration.\
            """,
            List.of(
                    "language-server-not-inlinable: nothing the server can inline at the position (not on a variable,"
                            + " a method or a constant; a variable assigned more than once; a recursive or abstract"
                            + " method; a declaration that nothing uses)",
                    Refusals.FILE_OUTSIDE_PROJECT,
                    Refusals.FILE_MISSING,
                    Refusals.NOT_A_FILE,
                    Refusals.STALE_EDIT_OF_FILES,
                    Refusals.NOT_READY),
            List.of(FILE, LINE, COLUMN, DRY_RUN));

    public static final Operation CREATE = new Operation(
            "create",
            "create a file that holds an empty class, interface, record or enum",
            """
            The name of the type is the name of the file; the package line comes from the folder of the file.
            A file in the default package has no package line.
            The new type has no comment and an empty body.
            The folders of the file that do not exist are made with it: a new package needs no mkdir.
            A dry run makes none. A file where a folder would go is refused and nothing is made.\
            """,
            List.of(
                    "editing-file-exists: a file is already there; nothing is overwritten",
                    "editing-folder-missing: a file is where a folder of the file would go; nothing is made",
                    "editing-not-a-type-name: the name of the file, without .java, is not the name of a type",
                    "language-server-not-creatable: the file is under no source folder of the project, or the Java"
                            + " level of the project has no such type",
                    Refusals.FILE_OUTSIDE_PROJECT,
                    "invalid-parameter: the kind is not class, interface, record or enum",
                    Refusals.NOT_READY),
            List.of(FILE, Parameter.string("kind", "what the type is: class, interface, record or enum"), DRY_RUN));

    public static final Operation REFERENCES = new Operation(
            "references",
            "where a symbol is used, in every file of the project and its tests",
            """
            Nothing is written. The position is a line and a column, each from 1, on the name of a type, a method, a field,\
             a local variable or a parameter, at its declaration or at a use.
            The declaration is not listed unless includeDeclaration is true; it is then listed apart, under\
             "declaration:", and the uses under "references:".
            The places are ordered by file, then by position. The uses in test sources are listed.
            The accessors of a field are not references of the field: a getter is found by asking for the getter.
            A symbol nothing uses has no references: the text says "no references", and the lists are empty.
            Only the symbols of the project have references: a type of the JDK or of a library is refused as\
             language-server-no-such-symbol, and no place in a library is ever listed.
            A name that does not resolve (its import names a package the project does not see) is refused the same\
             way, not answered with the references of the type that holds it: diagnostics tells why.\
            """,
            List.of(
                    "language-server-no-such-symbol: no symbol of the project at the position (a blank, a comment,"
                            + " a name that does not resolve)",
                    "editing-file-missing: a file that holds a reference is gone since the language server answered",
                    "editing-stale-edit: a file that holds a reference changed since the language server answered",
                    Refusals.FILE_OUTSIDE_PROJECT,
                    Refusals.FILE_MISSING,
                    Refusals.NOT_A_FILE,
                    Refusals.NOT_READY),
            List.of(
                    FILE,
                    LINE,
                    COLUMN,
                    Parameter.bool("includeDeclaration", "true to list the declaration apart from the uses")));

    public static final Operation ACTIONS = new Operation(
            "actions",
            "list the fixes and the rewrites the language server offers on a range",
            """
            Nothing is written. The range is two positions, each a line and a column from 1, the end excluded; to ask\
             about a point, give it twice.
            The actions are the fixes of the problems the range holds (a type that is not imported, a method to\
             create) and the rewrites of the code it covers (a concatenation to a text block, an extraction).\
             The actions of a whole file (organize the imports, generate accessors) are not listed: they are not\
             about a range.
            Each line is the number of the action in the list, its kind (quickfix, refactor.extract.variable,\
             quickassist, ...) and its title. The kind and the title are the language server's own text, which\
             gabier does not interpret. A range with nothing offered gives no actions.
            An action that another operation of gabier also does is followed by an arrow and the name of that\
             operation, read from its kind (and, for the kind refactor.inline that "Make Static" shares, from its\
             title, which must start with "Inline"): extract-variable, extract-field, extract-method and\
             extract-constant (the same file and range, then the name to give), and inline (the file and one\
             position, on the name). The operation is not always what the action does: extract-variable replaces the\
             selected expression only, so the action "Extract to local variable (replace all occurrences)" is linked\
             to it but replaces every occurrence, which only apply-action does. Prefer the operation when the name\
             matters: apply-action takes the name jdtls guesses. The structured result gives it as the member\
             "command" of the action, only when there is one.
            To apply one as the server proposes it, give its title to apply-action with the same range.\
            """,
            List.of(
                    "editing-invalid-range: the range ends before it starts",
                    Refusals.FILE_OUTSIDE_PROJECT,
                    Refusals.FILE_MISSING,
                    Refusals.NOT_A_FILE,
                    Refusals.NOT_READY),
            onARange());

    public static final Operation APPLY_ACTION = new Operation(
            "apply-action",
            "apply the fix or the rewrite the language server offers on a range, chosen by its title",
            """
            The title is the one actions lists for the same file and range, exactly as written: the number\
             of an action changes from one call to the next, the title does not.
            A title that no action has is refused with the titles the range offers; one that several have, with\
             those actions. Nothing is written.
            Some actions of the language server are commands for an editor: they ask a question (a dialog) or a\
             place before they change anything, and give no edit. They are listed by actions and refused\
             here as language-server-not-applicable.\
            """,
            List.of(
                    "editing-no-such-code-action: the server offers no action with this title on the range; the problem"
                            + " lists the ones it offers",
                    "editing-ambiguous-code-action: several actions have this title on the range; the problem lists"
                            + " them, and a narrower range may leave one",
                    "language-server-not-applicable: the action gives no edit (it is a command for an editor, such as a"
                            + " dialog to fill in, or it changes nothing)",
                    "editing-invalid-range: the range ends before it starts",
                    Refusals.FILE_OUTSIDE_PROJECT,
                    Refusals.FILE_MISSING,
                    Refusals.NOT_A_FILE,
                    Refusals.STALE_EDIT_OF_FILES,
                    Refusals.NOT_READY),
            onARange(
                    Parameter.string("title", "the title of the action, as the actions of the same range list it"),
                    DRY_RUN));

    private Operations() {}

    /** Every operation of the daemon, in the order of the table of {@code docs/protocol.md}. */
    public static List<Operation> all() {
        return List.of(
                STATUS,
                START,
                STOP,
                DIAGNOSTICS,
                RENAME,
                RENAME_TOKEN,
                ORGANIZE_IMPORTS,
                CLEANUP,
                EXTRACT_VARIABLE,
                EXTRACT_METHOD,
                EXTRACT_CONSTANT,
                EXTRACT_FIELD,
                MOVE_TYPE_TO_FILE,
                MOVE,
                INLINE,
                CREATE,
                REFERENCES,
                ACTIONS,
                APPLY_ACTION);
    }

    /** An extraction of a selection into something named: the four commands share their parameters and their refusals. */
    private static Operation extraction(String method, String summary, String invalidName) {
        return new Operation(
                method,
                summary,
                "",
                List.of(
                        Refusals.NOT_EXTRACTABLE,
                        invalidName,
                        Refusals.FILE_OUTSIDE_PROJECT,
                        Refusals.FILE_MISSING,
                        Refusals.NOT_A_FILE,
                        Refusals.STALE_EDIT_OF_A_FILE,
                        Refusals.NOT_READY),
                onARange(NAME, DRY_RUN));
    }
}
