package fr.craft.gabier.cli;

import fr.craft.gabier.protocol.ChangedFiles;
import fr.craft.gabier.protocol.TerminalText;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The answer of a command that changes files, and how it is shown: what the commands that rename, organize or extract
 * share. It is held by composition: a capability asks, then prints, or says something else for an empty answer.
 *
 * @param invocation the run it answers
 * @param changed the files the daemon changed, or would change with a dry run
 */
record ChangedFilesReply(Invocation invocation, ChangedFiles changed) {

    /** Asks the daemon for a change of files. */
    static ChangedFilesReply ask(Invocation invocation, String method, Map<String, String> params) {
        return new ChangedFilesReply(
                invocation, ChangedFiles.of(invocation.ask(method, params).result()));
    }

    /** Whether the text of the answer holds nothing: no file changed and no diff. */
    boolean isEmpty() {
        return text().isEmpty();
    }

    /** The result of the daemon as it gave it with {@code --json}; else the dry run said, then the text. */
    void print() {
        var out = invocation.out();
        if (invocation.json()) {
            out.println(changed.toJson());

            return;
        }
        if (invocation.arguments().dryRun()) {
            out.println("dry run, nothing written");
        }
        text().forEach(line -> out.println(TerminalText.neutralise(line)));
    }

    /** One line per file changed, a blank line, then the diff; nothing for a result that holds nothing. */
    private List<String> text() {
        var lines = new ArrayList<String>();
        changed.modified().forEach(file -> lines.add("modified " + file));
        changed.created().forEach(file -> lines.add("created " + file));
        changed.renamed().forEach(moved -> lines.add("renamed " + moved.from() + " -> " + moved.to()));
        changed.deleted().forEach(file -> lines.add("deleted " + file));
        if (!changed.diff().isEmpty()) {
            lines.add("");
            changed.diff().lines().forEach(lines::add);
        }

        return lines;
    }
}
