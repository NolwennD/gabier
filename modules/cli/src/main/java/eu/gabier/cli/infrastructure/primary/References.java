package eu.gabier.cli.infrastructure.primary;

import eu.gabier.protocol.FoundReferences;
import eu.gabier.protocol.FoundReferences.Place;
import eu.gabier.protocol.TerminalText;
import java.util.List;
import java.util.Map;

/**
 * {@code references [--include-declaration] <file> <line>:<column>}: where the symbol at the position is used, one line
 * per place, {@code file:line:column: source}, the position from 1 as an editor shows it (the daemon counts from 0) and
 * the source line without its indentation. The files are written as the daemon gives them, absolute. The declaration is
 * listed apart, under {@code declaration:}, only when it is asked for; a symbol nothing uses is said ({@code no
 * references}) with the exit code 0. With {@code --json}, the result as the daemon gave it, the positions from 0 and the
 * source lines exact. In text, every line is neutralised as the text of a problem is ({@link TerminalText}): a source
 * line and a file name are hostile text. {@code --dry-run} is not an option of this command, it is a usage error.
 */
final class References implements Capability {

    static final String INCLUDE_DECLARATION = "--include-declaration";

    static final Command COMMAND = Command.called("references")
            .summary("where a symbol is used, in every file of the project and its tests")
            .usage("gabier [--json] references [--include-declaration] <file> <line>:<column>")
            .example("""
                $ gabier references src/main/java/demo/Ledger.java 11:17
                /work/demo/src/main/java/demo/app/Bank.java:8:16: ledger.deposit(10);
                /work/demo/src/test/java/demo/LedgerTest.java:6:16: ledger.deposit(20);\
                """)
            .asksTheDaemon(
                    "language-server-no-such-symbol: no symbol of the project at the position (a blank, a comment,"
                            + " a name that does not resolve)",
                    "editing-file-missing: a file that holds a reference is gone since the language server answered",
                    "editing-stale-edit: a file that holds a reference changed since the language server answered",
                    Refusal.FILE_OUTSIDE_PROJECT,
                    Refusal.FILE_MISSING,
                    Refusal.NOT_A_FILE,
                    Refusal.NOT_READY)
            .withNotes("""
                Nothing is written. The position is line:column, each from 1, on the name of a type, a method, a field, a local\
                 variable or a parameter, at its declaration or at a use.
                Without --include-declaration the declaration is not listed. With it, the declaration is listed apart, under\
                 "declaration:", and the uses under "references:".
                The places are ordered by file, then by position. The uses in test sources are listed.
                The accessors of a field are not references of the field: a getter is found by asking for the getter.
                A symbol nothing uses prints "no references" and exits with 0; with --json, two empty lists.
                Only the symbols of the project have references: a type of the JDK or of a library is refused as\
                 language-server-no-such-symbol, and no place in a library is ever listed.
                A name that does not resolve (its import names a package the project does not see) is refused the same\
                 way, not answered with the references of the type that holds it: gabier diagnostics tells why.\
                """);

    @Override
    public Command command() {
        return COMMAND;
    }

    @Override
    public void run(Invocation invocation) {
        var arguments = invocation.arguments();
        var operands = arguments.without(INCLUDE_DECLARATION).exactly(2);
        var position = operands.position(1);
        var result = invocation
                .daemon()
                .ask(
                        command().commandName(),
                        Map.of(
                                "file", operands.file(0).toString(),
                                "line", String.valueOf(position.line()),
                                "column", String.valueOf(position.column()),
                                "includeDeclaration", String.valueOf(arguments.has(INCLUDE_DECLARATION))));
        var found = FoundReferences.of(result.result());
        if (invocation.json()) {
            invocation.out().println(found.toJson());

            return;
        }
        print(invocation, found);
    }

    private static void print(Invocation invocation, FoundReferences found) {
        var out = invocation.out();
        if (!found.declarations().isEmpty()) {
            out.println("declaration:");
            lines(found.declarations()).forEach(out::println);
        }
        if (found.references().isEmpty()) {
            out.println("no references");
        } else {
            if (!found.declarations().isEmpty()) {
                out.println("references:");
            }
            lines(found.references()).forEach(out::println);
        }
    }

    private static List<String> lines(List<Place> places) {
        return places.stream().map(References::line).toList();
    }

    private static String line(Place place) {
        return TerminalText.neutralise(place.file() + ":" + (place.line() + 1) + ":" + (place.column() + 1) + ": "
                + place.source().strip());
    }
}
