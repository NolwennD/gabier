package eu.gabier.cli.application;

import eu.gabier.protocol.ChangedFiles;
import eu.gabier.protocol.CodeActions;
import eu.gabier.protocol.FileDiagnostics;
import eu.gabier.protocol.FoundReferences;
import eu.gabier.protocol.FoundReferences.Place;
import eu.gabier.protocol.TerminalText;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

/**
 * The text of a result of the daemon, as a person reads it: one function per kind of result, of the result alone (a
 * file as the user typed it, for diagnostics), so that every way of showing a result, the command line and any other
 * front, says the same thing. Positions are shown from 1, as an editor does (the daemon counts from 0). Every line
 * is neutralised ({@link TerminalText}): the files, the messages and the source lines come from the project or from a
 * language server, and none may drive a terminal or split a line.
 */
public final class ResultText {

    private ResultText() {}

    /** One line per file changed, a blank line, then the diff; nothing for a result that holds nothing. */
    public static List<String> lines(ChangedFiles changed) {
        var lines = new ArrayList<String>();
        changed.modified().forEach(file -> lines.add("modified " + file));
        changed.created().forEach(file -> lines.add("created " + file));
        changed.renamed().forEach(moved -> lines.add("renamed " + moved.from() + " -> " + moved.to()));
        changed.deleted().forEach(file -> lines.add("deleted " + file));
        if (!changed.diff().isEmpty()) {
            lines.add("");
            changed.diff().lines().forEach(lines::add);
        }

        return neutralised(lines);
    }

    /**
     * One line per problem, {@code file:line:column: severity: message}; nothing for a file with no problem.
     *
     * @param file the file as the user gave it
     */
    public static List<String> lines(String file, FileDiagnostics diagnostics) {
        return neutralised(diagnostics.diagnostics().stream()
                .map(diagnosis -> file + ":" + (diagnosis.line() + 1) + ":" + (diagnosis.column() + 1) + ": "
                        + diagnosis.severity().text() + ": " + diagnosis.message())
                .toList());
    }

    /**
     * One line per place, {@code file:line:column: source}, the source line without its indentation. The declaration,
     * when the result holds it, is listed apart, under {@code declaration:}, and the uses under {@code references:};
     * a symbol that nothing uses says {@code no references}.
     */
    public static List<String> lines(FoundReferences found) {
        var lines = new ArrayList<String>();
        if (!found.declarations().isEmpty()) {
            lines.add("declaration:");
            found.declarations().forEach(place -> lines.add(line(place)));
        }
        if (found.references().isEmpty()) {
            lines.add("no references");
        } else {
            if (!found.declarations().isEmpty()) {
                lines.add("references:");
            }
            found.references().forEach(place -> lines.add(line(place)));
        }

        return neutralised(lines);
    }

    /**
     * One line per action, {@code <number> <kind> <title>}, the number from 1 in the order given, followed by {@code ->
     * gabier <command>} when the daemon names the command of gabier that does the same; {@code -} stands for a kind
     * that is missing; {@code no actions} for a range with nothing offered.
     */
    public static List<String> lines(CodeActions offered) {
        if (offered.actions().isEmpty()) {
            return List.of("no actions");
        }

        return neutralised(IntStream.range(0, offered.actions().size())
                .mapToObj(index -> line(index + 1, offered.actions().get(index)))
                .toList());
    }

    /** What the daemon noticed on the way, one {@code warning:} line each. */
    public static List<String> warnings(ChangedFiles changed) {
        return neutralised(changed.warnings().stream()
                .map(warning -> "warning: " + warning)
                .toList());
    }

    private static String line(Place place) {
        return place.file() + ":" + (place.line() + 1) + ":" + (place.column() + 1) + ": "
                + place.source().strip();
    }

    private static String line(int number, CodeActions.Action action) {
        var reference = action.command().isEmpty() ? "" : " -> gabier " + action.command();

        return number + " " + (action.kind().isEmpty() ? "-" : action.kind()) + " " + action.title() + reference;
    }

    private static List<String> neutralised(List<String> lines) {
        return lines.stream().map(TerminalText::neutralise).toList();
    }
}
