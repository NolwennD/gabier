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
                stops by itself. The next command starts a new daemon, so an agent does nothing about it.
                It also shows the import settings that organize-imports follows: for each of the three, its value and\
                 where it comes from, .settings (.settings/org.eclipse.jdt.ui.prefs), .vscode (.vscode/settings.json) or\
                 the default of the language server. The files are read when status is asked, so a file edited a moment\
                 ago is already in it. They are the member imports of the result (importOrder, starThreshold,\
                 staticStarThreshold, each with its value and its source).\
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

    public static final Operation DIAGNOSTICS_ALL = new Operation(
            "diagnostics-all",
            "the errors and warnings of every Java file of the project, without a build",
            """
            The answer is what the language server published about the project once it has built it, with no file
            opened: every .java file of the project that has a problem, the test sources and the other source sets
            included, whatever the number of files. The result is the one of diagnostics, with one element per file in
            the order of the paths; a file that is clean is not in it, and a project with no problem answers nothing.
            Files created or changed on disk since the last call are told to the language server first, and the call
            waits for its build to be over, so a build that is running is not taken for a clean project. What is not a
            .java file of the project (a pom.xml, a file of another directory) is left out and does not refuse the
            call.\
            """,
            List.of(Refusals.REQUEST_FAILED, Refusals.NOT_READY),
            List.of());

    public static final Operation WATCH = new Operation(
            "watch",
            "follow the errors and warnings of Java files as they change, until the connection is closed",
            """
            The answer is the one of diagnostics (one element per file, in the order given, a clean file included). The
            daemon then writes, on the same connection and with no request, one notification (a JSON-RPC message with no
            id) each time the problems of a file changed after the language server built the project: problems, with the
            files whose list changed in the shape of the result of diagnostics (a file that became clean has an empty
            list), notice, with a message for the person who reads (the daemon can no longer follow the disk and looks at
            the files every second), and ended, with the problem that ends the subscription, after which nothing more is
            written. The daemon follows the disk for the files that jdtls asked to have watched, while at least one client
            watches, and reads the project once whatever the number of watchers. A client that reads slowly is told the
            latest state, not every state: a file that breaks and is fixed between two of its reads is never shown. The
            subscription ends when the connection does. A connection follows one thing: a second watch on it is refused.\
            """,
            List.of(
                    Refusals.FILE_OUTSIDE_PROJECT,
                    Refusals.FILE_MISSING,
                    Refusals.NOT_A_FILE,
                    Refusals.NOT_READY,
                    "already-watching: the connection already follows something"),
            List.of(Parameter.paths("files", "the Java files, absolute paths in the project")));

    public static final Operation WATCH_ALL = new Operation(
            "watch-all",
            "follow the errors and warnings of every Java file of the project as they change",
            """
            As watch, for the whole project: the answer is the one of diagnostics-all (the files that have a problem), and
            a notification names each file whose list of problems changed, a file that became clean included.\
            """,
            List.of(
                    Refusals.REQUEST_FAILED,
                    Refusals.NOT_READY,
                    "already-watching: the connection already follows something"),
            List.of());

    public static final Operation WATCH_AFFECTED_TESTS = new Operation(
            "watch-affected-tests",
            "follow the tests to run again after what changes on disk, until the connection is closed",
            """
            The answer is the one of affected-tests-changed, for the changes on disk now (the daemon uses them up as that\
             call does). The daemon then writes, on the same connection and with no request, one notification (a JSON-RPC\
             message with no id) after each series of changes on disk, once the language server built the project:\
             affected-tests, with the tests the change affects in the shape of the result of affected-tests (never with\
             no test: a change that affects none is not told), refusal, with the error object of a refusal of the testing\
             context (a changed file does not compile, a filter is not a regular expression) after which the daemon goes\
             on, keeps the changes and lists their tests once they can be told, notice, with a message for the person\
             who reads (the daemon can no longer follow the disk and looks at the files every second), and ended, with\
             the problem that ends the subscription, after which nothing more is written. The answer to the request is\
             refused only for what refuses affected-tests-changed apart from the refusals of the testing context, which\
             are told as a refusal notification after the answer.
            The daemon follows the disk with the one watcher that the other streams use, while at least one client\
             follows something, and reads the tests once whatever the number of watchers of the tests, only while one\
             watches them: every watcher is told the same tests. The changes belong to the daemon, as for\
             affected-tests-changed: what a watcher is told is no longer told to another client, a call that asks\
             affected-tests-changed included, and the one that is told first uses them up. A client that reads slowly is\
             told the tests of the changes it missed together, none is lost. The subscription ends when the connection\
             does. A connection follows one thing: a second watch on it is refused.\
            """,
            List.of(
                    Refusals.REQUEST_FAILED,
                    Refusals.NOT_READY,
                    "already-watching: the connection already follows something"),
            List.of());

    public static final Operation RENAME = new Operation(
            "rename",
            "rename a symbol, and everything that refers to it, in every file",
            """
            A position on the name in a package line renames the package, with its imports and its references: its files
            move to the folder of the new name and the folders left empty are removed. Give the whole new name
            (demo.shop); the segment the position is on decides how much of the old name is renamed (on app in
            demo.app.sub, demo.app); a package with no class of its own (demo in demo.app) is refused as
            language-server-empty-package. Sub-packages keep their names, and the same package in another source set
            (the tests) is a rename of its own: a second call, on a file of that source set. The test classes keep the
            import that the first call gave them, which shows as an unused import: organize-imports removes it. A package that exists, or a name that is not a package name, is
            refused as language-server-invalid-name. module-info.java, string literals and build files are not
            changed: edit them as text.

            A failure of jdtls is relayed, not guessed in advance: when it answers an internal error of its own the
            refusal is language-server-request-failed, with its code and its message, and nothing is written. jdtls
            1.63.0 fails so on the name of a module in a module-info.java (a position on its first segment; a later
            segment is refused as language-server-no-such-symbol): edit the module line and the requires that name
            the module as text. It fails so too on a component of a record that has a compact constructor or an
            explicit canonical constructor: edit the record by hand (Eclipse bug 566507). Without such a
            constructor it renames the component, its accessor calls and this.name, and may leave a use by simple
            name in the body of the record (seen for a component named file, and for a field of a class named file),
            which no longer compiles: check the files of the record for errors. A position on a call of the accessor
            is refused as language-server-no-such-symbol; with an explicit accessor (public String label()) jdtls
            renames it as a getter (label() becomes getCaption()).\
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
                    Refusals.REQUEST_FAILED,
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
                    Refusals.REQUEST_FAILED,
                    Refusals.NOT_READY),
            List.of(
                    FILE,
                    Parameter.string("token", "an identifier that occurs in the file, which names the symbol"),
                    NEW_NAME,
                    DRY_RUN));

    public static final Operation ORGANIZE_IMPORTS = new Operation(
            "organize-imports",
            "add the missing imports, remove the unused ones and order the rest as the project does",
            """
            The imports are written as the project writes them: the order, the groups and the star-import
            thresholds come from .settings/org.eclipse.jdt.ui.prefs (importorder, ondemandthreshold,
            staticondemandthreshold) or from .vscode/settings.json (java.completion.importOrder,
            java.sources.organizeImports.starThreshold and staticStarThreshold) at the root of the project. A
            setting that neither file gives keeps the default: the groups java, javax, org and com, a blank line
            between them, and a star import from 99 imports. status shows each setting and where it comes from.
            When the two files give different values for a setting, .settings is followed and the answer carries
            a warning, as it does for a file that cannot be read or a setting of the wrong type.
            A file whose imports are already in order answers nothing to change.
            A type that several packages declare (Date) and that the file uses without importing it is refused with
            its candidates: choose, add the import by hand, then organize.
            An import that the file still uses and that the server reports an error on (a package of a module the
            file cannot read) would be deleted: it is refused and the file is not touched. Run diagnostics first.
            The types of the packages com.sun, sun and jdk are never offered for a missing import.\
            """,
            List.of(
                    "language-server-ambiguous-imports: several packages declare a type the file uses without"
                            + " importing it; the candidates are in the problem",
                    "language-server-unresolved-imports: jdtls would delete imports that the file uses and it reports"
                            + " an error on",
                    Refusals.FILE_OUTSIDE_PROJECT,
                    Refusals.FILE_MISSING,
                    Refusals.NOT_A_FILE,
                    Refusals.STALE_EDIT_OF_A_FILE,
                    Refusals.REQUEST_FAILED,
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
            stringConcatToTextBlock rewrites a concatenation of three literals or more, and leaves one of two
            alone; the escaped quotes of the literals are plain quotes in the block.
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
                    Refusals.REQUEST_FAILED,
                    Refusals.NOT_READY),
            List.of(
                    FILE,
                    Parameter.array("cleanUps", "the identifiers of the clean-ups, applied in the order given"),
                    DRY_RUN));

    public static final Operation EXTRACT_VARIABLE = extraction(
            "extract-variable",
            "turn an expression into a local variable",
            """
            The selection is replaced by the name, and the variable is declared on the line above the statement that
            holds it. Only the selected expression is replaced, not its other occurrences.
            A keyword, a whole statement and a call of a void method cannot be extracted.
            The name asked for replaces the one the server proposes (string for a String).\
            """,
            "language-server-invalid-name: the name is not a Java identifier, or is a parameter of the method");

    public static final Operation EXTRACT_METHOD = extraction(
            "extract-method",
            "turn statements, or an expression, into a method",
            """
            The selection is replaced by a call to a new private method, declared after the method that holds it. The
            parameters (the local variables and parameters the selection reads) and the return value (the local
            variable it assigns that the rest of the method uses, or the value of an expression) are worked out.
            Half a statement, or an if whose branch returns while the code after it still runs, cannot be
            extracted.
            A method of the class with the same name and other parameter types is an overload and is accepted.\
            """,
            "language-server-invalid-name: the name is not a Java identifier, or another method of the class"
                    + " has it with the same parameters");

    public static final Operation EXTRACT_CONSTANT = extraction(
            "extract-constant",
            "turn an expression into a constant",
            """
            The selection is replaced by the name, and a private static final field of that name, initialised with
            the expression, is declared at the top of the class. Only the selected expression is replaced, not its
            other occurrences.
            An expression that reads a local variable ("count: " + count) or a keyword cannot be extracted.\
            """,
            "language-server-invalid-name: the name is not a Java identifier");

    public static final Operation EXTRACT_FIELD = extraction(
            "extract-field",
            "turn an expression into a field",
            """
            The selection is replaced by the name, and a private field of that name is declared at the top of the
            class. It is initialised in the current method, as an assignment before the statement that holds the
            expression; there is no option for the declaration of the field or for the constructors.
            Only the selected expression is replaced, not its other occurrences.
            A keyword, a whole statement and a selection outside a class body (the name in the package line) cannot
            be extracted.\
            """,
            "language-server-invalid-name: the name is not a Java identifier");

    public static final Operation MOVE_TYPE_TO_FILE = new Operation(
            "move-type-to-file",
            "make a nested type a top-level type in a file of its own",
            """
            The position is inside the type to move, on its name or in one of its methods: the innermost type that
            encloses it moves.
            The type goes to a file of its own, named after it, in the package of the outer class; the imports and
            the references of the project follow it. A static nested class, and an inner class that does not use the
            outer instance, move as they are.
            An inner class that uses its outer instance gets a field and a constructor parameter of the outer type.
            A JUnit @Nested test class can then no longer be instantiated by JUnit.
            To avoid it, first make the class independent of the outer instance,
            for example by having it extend the shared base class, then move it.
            A field the class read from the outer class that was private is no longer private.
            Each annotation of the class, such as @Nested, stays on it after the move: a top-level class still
            annotated @Nested is not run by JUnit, so remove it when the moved class is a test that must run alone.
            The new file has no line break at its end, and the field and the constructor added to an inner class
            carry an empty Javadoc: run the formatter of the project on it.\
            """,
            List.of(
                    "language-server-not-movable: no nested type can be moved at the position (outside any type, a"
                            + " top-level type, or a file of the name of the type exists in its package)",
                    Refusals.FILE_OUTSIDE_PROJECT,
                    Refusals.FILE_MISSING,
                    Refusals.NOT_A_FILE,
                    "editing-file-exists: the file to create appeared since the language server answered",
                    Refusals.STALE_EDIT_OF_FILES,
                    Refusals.REQUEST_FAILED,
                    Refusals.NOT_READY),
            List.of(FILE, LINE, COLUMN, DRY_RUN));

    public static final Operation MOVE = new Operation(
            "move",
            "move Java classes to a package, creating the package when it does not exist",
            """
            The last operand is the package, such as demo.billing; the other operands are the files to move.
            The files must be Java sources of one source folder of the project; the package is made in that folder,
            so a test class goes to the test sources.
            The package is created, with its folder, when it does not exist; a dry run creates nothing.
            The default package cannot be the target.
            The folders the move empties are removed, up to the source folder; a folder that still holds a file
            stays. A dry run removes none.
            The imports, the static imports and the fully qualified names of the project follow the moved classes,
            in the other packages and the other modules of the build. A nested type keeps its outer type
            (Invoice.Line). A name in a string literal or a comment, and a file that is not Java, are not rewritten:
            search them for the old name.
            The other modules are rewritten only when the build links them. A Gradle build with the plugin
            org.gradlex.extra-java-module-info links none by default: set skipLocalJars = true in its
            extraJavaModuleInfo block, or only the module of the class is rewritten.
            The imports it writes are not sorted: run organize-imports on the modified files, after diagnostics (a
            file whose imports cannot be resolved loses them).
            When no Java file is left in the old package, its exports and opens in module-info.java follow it, with
            their to clause. The new package is not exported or opened if the old one was not. uses and provides
            are not changed.
            A member that was package-private in the old package and is used across the move is no longer
            reachable, and nothing says so: run diagnostics on the moved files and on the files that use them.\
            """,
            List.of(
                    """
                    language-server-not-movable-to-package: the files cannot go to the package; the parameter reason\
                     says why: already-in-package, name-taken (a file of that name is there, or two files of the\
                     list share a name), not-a-java-source, outside-source-folders, several-source-folders,\
                     folder-not-made (a file is where a folder of the package goes) or nothing-to-move (no change to make)\
                    """,
                    "editing-not-a-package-name: the last operand is not a package name (the default package, a"
                            + " keyword, an empty part)",
                    Refusals.FILE_OUTSIDE_PROJECT,
                    Refusals.FILE_MISSING,
                    Refusals.NOT_A_FILE,
                    "editing-file-exists: a file to create appeared since the language server answered",
                    Refusals.STALE_EDIT_OF_FILES,
                    Refusals.REQUEST_FAILED,
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
            a position on one use inlines that use only and keeps the declaration.
            Parentheses are added where the value could otherwise read differently (in a concatenation), and left
            out where precedence already holds.\
            """,
            List.of(
                    "language-server-not-inlinable: nothing the server can inline at the position (not on a variable,"
                            + " a method or a constant; a variable assigned more than once; a recursive or abstract"
                            + " method; a declaration that nothing uses)",
                    Refusals.FILE_OUTSIDE_PROJECT,
                    Refusals.FILE_MISSING,
                    Refusals.NOT_A_FILE,
                    Refusals.STALE_EDIT_OF_FILES,
                    Refusals.REQUEST_FAILED,
                    Refusals.NOT_READY),
            List.of(FILE, LINE, COLUMN, DRY_RUN));

    public static final Operation CREATE = new Operation(
            "create",
            "create a file that holds an empty class, interface, record or enum, or the package-info.java of a package",
            """
            The name of the type is the name of the file; the package line comes from the folder of the file.
            A file in the default package has no package line.
            The new type is public, has no comment and an empty body (a record has empty parentheses).
            The file must not exist: it is never overwritten.
            The kind package is for a package-info.java: the file holds the package line under\
             @org.jspecify.annotations.NullMarked, and nothing else. The package is the folder of the file under its\
             source folder. The file must be named package-info.java, and cannot be in the default package.
            The folders of the file that do not exist are made with it: a new package needs no mkdir.
            A dry run makes none. A file where a folder would go is refused and nothing is made.\
            """,
            List.of(
                    "editing-file-exists: a file is already there; nothing is overwritten",
                    "editing-folder-missing: a file is where a folder of the file would go; nothing is made",
                    "editing-not-a-type-name: the name of the file, without .java, is not the name of a type",
                    "editing-not-a-package-info: the kind is package and the file is not named package-info.java",
                    "editing-not-a-package-name: the folder of a package-info.java is not the name of a package (a"
                            + " folder that is not a Java identifier)",
                    "language-server-not-creatable: the file is under no source folder of the project, or in the"
                            + " default package of a package-info.java, or the Java level of the project has no such"
                            + " type",
                    Refusals.FILE_OUTSIDE_PROJECT,
                    "invalid-parameter: the kind is not class, interface, record, enum or package",
                    Refusals.REQUEST_FAILED,
                    Refusals.NOT_READY),
            List.of(
                    FILE,
                    Parameter.string(
                            "kind",
                            "what the file holds: class, interface, record, enum, or package for a package-info.java"),
                    DRY_RUN));

    public static final Operation REFERENCES = new Operation(
            "references",
            "where a symbol is used, in every file of the project and its tests",
            """
            Nothing is written. The position is on the name of a type, a method, a field, a local variable or a\
             parameter, at its declaration or at a use.
            The declaration is not listed unless includeDeclaration is true; it is then listed apart, under\
             "declaration:", and the uses under "references:".
            The places are ordered by file, then by position. The uses in test sources are listed.
            The accessors of a field are not references of the field: a getter is found by asking for the getter.
            A symbol nothing uses has no references: the text says "no references", and the lists are empty.
            Only the symbols of the project have references: a type of the JDK or of a library is refused as\
             language-server-no-such-symbol, and no place in a library is ever listed.
            A name that does not resolve (its import names a package the project does not see) is refused the same\
             way, not answered with the references of the type that holds it: diagnostics tells why. A fully\
             qualified name that does not resolve is answered as a symbol nothing uses.\
            """,
            List.of(
                    "language-server-no-such-symbol: no symbol of the project at the position (a blank, a comment,"
                            + " a name that does not resolve)",
                    "editing-file-missing: a file that holds a reference is gone since the language server answered",
                    "editing-stale-edit: a file that holds a reference changed since the language server answered",
                    Refusals.FILE_OUTSIDE_PROJECT,
                    Refusals.FILE_MISSING,
                    Refusals.NOT_A_FILE,
                    Refusals.REQUEST_FAILED,
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
            Nothing is written. To ask about a point, give the position twice. The fixes need the problems of the\
             range, so the call waits for the language server to be quiet, about 5 seconds.
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
                    Refusals.REQUEST_FAILED,
                    Refusals.NOT_READY),
            onARange());

    public static final Operation APPLY_ACTION = new Operation(
            "apply-action",
            "apply the fix or the rewrite the language server offers on a range, chosen by its title",
            """
            The title is the one actions lists for the same file and range, exactly as written: the number\
             of an action changes from one call to the next, the title does not.
            A title that no action has is refused with the titles the range offers; one that several have, with\
             those actions: ask on a narrower range. Nothing is written.
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
                    Refusals.REQUEST_FAILED,
                    Refusals.NOT_READY),
            onARange(
                    Parameter.string("title", "the title of the action, as the actions of the same range list it"),
                    DRY_RUN));

    public static final Operation AFFECTED_TESTS = new Operation(
            "affected-tests",
            "the tests to run again after a change to Java files, with no build tool",
            """
            Nothing is written and nothing is run: the answer is the list of the test classes to run, as the binary\
             names of their classes (app.GreeterTest, app.Outer$InnerTest), one per test, in the order of the names;\
             a change that affects no test answers an empty list.
            A changed test class is affected itself. A changed class that the project's own tests depend on, directly\
             or through other classes, affects those tests: the dependencies are read in the classes the language\
             server compiled, so the files are told to it first and the call waits for its build, whatever the build\
             tool of the project (Maven, Gradle or none). JUnit 3, 4 and 5 and TestNG tests are recognised. The tests\
             that load classes by themselves (ArchUnit) are always listed when a Java file changed. The file\
             infinitest.filters at the root of the project leaves tests out: its include and exclude lines are regular\
             expressions on the whole class name (exclude .*IT leaves out the integration tests); its groups lines are\
             not read. The patterns of a line are separated by commas, a line that starts with # or ! is a comment, a\
             line that is no directive is an exclude, and a pattern that is not a regular expression is refused.
            A file that is not a Java source (a pom.xml, a readme) is left out and does not refuse the call.\
             What the selection does not see: a constant that the compiler inlined and a test reads through a static\
             import, a class used through reflection or injection, a class linked to a test only through an interface that it implements.
            A Java file that does not compile is refused, with its name: the language server writes a class for it all\
             the same, which is not the class of its text, so the tests it affects cannot be told. One refused file\
             refuses the whole call.\
            """,
            List.of(
                    Refusals.TESTING_COMPILATION_FAILED,
                    "testing-file-missing: a Java file given does not exist (a deleted file is refused too)",
                    "testing-not-in-a-source-folder: a Java file given is under no source folder of the project",
                    Refusals.TESTING_INVALID_TEST_FILTER,
                    Refusals.TESTING_READ_FAILED,
                    Refusals.REQUEST_FAILED,
                    Refusals.NOT_READY),
            List.of(Parameter.paths("files", "the Java files that changed, absolute paths in the project")));

    public static final Operation AFFECTED_TESTS_CHANGED = new Operation(
            "affected-tests-changed",
            "the tests to run again after what changed on disk since the last answer, with no file to name",
            """
            The same answer as affected-tests, for the Java files that changed on disk since the daemon last answered\
             this call, with none to give: the daemon knows the files it was told of when any request took in the\
             changes on disk (an edit by hand, a text edit, a rename: all of them). The answer forgets them, so a second\
             call with no new change answers an empty list; a call that is refused keeps them, and the next call that\
             is answered lists their tests. The changes belong to the daemon of the project, not to the client: two\
             clients share them, and whichever asks first is answered. Nothing changed answers an empty list.
            The changes are those since the daemon started its language server: what changes while it imports the\
             project counts, what changed before does not. A daemon started by this very call has seen nothing change,\
             so its first call answers an empty list (never every test of the project by surprise): start the daemon\
             before editing. A daemon that stops, or that stopped itself after an hour with no request, forgets the\
             changes. A Java file that was deleted, or is under no source folder, is left out and never refused, and\
             so is a file that is not Java. The tests that depended on a deleted class are listed when their own file\
             changed (a rename does), and not otherwise.
            A Java file that changed and does not compile is refused, with its name, as affected-tests does.\
            """,
            List.of(
                    Refusals.TESTING_COMPILATION_FAILED,
                    Refusals.TESTING_INVALID_TEST_FILTER,
                    Refusals.TESTING_READ_FAILED,
                    Refusals.REQUEST_FAILED,
                    Refusals.NOT_READY),
            List.of());

    private Operations() {}

    /** Every operation of the daemon, in the order of the table of {@code docs/protocol.md}. */
    public static List<Operation> all() {
        return List.of(
                STATUS,
                START,
                STOP,
                DIAGNOSTICS,
                DIAGNOSTICS_ALL,
                WATCH,
                WATCH_ALL,
                WATCH_AFFECTED_TESTS,
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
                APPLY_ACTION,
                AFFECTED_TESTS,
                AFFECTED_TESTS_CHANGED);
    }

    /** An extraction of a selection into something named: the four commands share their parameters and their refusals. */
    private static Operation extraction(String method, String summary, String notes, String invalidName) {
        return new Operation(
                method,
                summary,
                notes,
                List.of(
                        Refusals.NOT_EXTRACTABLE,
                        invalidName,
                        Refusals.FILE_OUTSIDE_PROJECT,
                        Refusals.FILE_MISSING,
                        Refusals.NOT_A_FILE,
                        Refusals.STALE_EDIT_OF_A_FILE,
                        Refusals.REQUEST_FAILED,
                        Refusals.NOT_READY),
                onARange(NAME, DRY_RUN));
    }
}
