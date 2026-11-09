package eu.gabier.cli.infrastructure.primary;

import eu.gabier.cli.application.ResultText;
import eu.gabier.protocol.ChangedFiles;
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
                invocation,
                ChangedFiles.of(invocation.daemon().ask(method, params).result()));
    }

    /** Whether the text of the answer holds nothing: no file changed and no diff. */
    boolean isEmpty() {
        return ResultText.lines(changed).isEmpty();
    }

    /** Says what the daemon noticed on the way, one {@code warning:} line each. */
    void printWarnings() {
        ResultText.warnings(changed).forEach(invocation.out()::println);
    }

    /**
     * The result of the daemon as it gave it with {@code --json}; else the dry run said, the warnings of the daemon,
     * then the text.
     */
    void print() {
        var out = invocation.out();
        if (invocation.json()) {
            out.println(changed.toJson());

            return;
        }
        if (invocation.arguments().dryRun()) {
            out.println("dry run, nothing written");
        }
        printWarnings();
        ResultText.lines(changed).forEach(out::println);
    }
}
