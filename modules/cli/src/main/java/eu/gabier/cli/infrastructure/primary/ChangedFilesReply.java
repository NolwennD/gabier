package eu.gabier.cli.infrastructure.primary;

import eu.gabier.cli.application.OperationResult;
import eu.gabier.cli.application.ResultText;
import eu.gabier.cli.domain.Operation;
import eu.gabier.protocol.ChangedFiles;
import java.util.Map;

/**
 * The answer of a command that changes files, and how it is shown: what the commands that rename, organize or extract
 * share. It is held by composition: a capability asks, then prints.
 *
 * @param invocation the run it answers
 * @param changed the files the daemon changed, or would change with a dry run
 */
record ChangedFilesReply(Invocation invocation, ChangedFiles changed) {

    /** Runs an operation that changes files, with these values. */
    static ChangedFilesReply ask(Invocation invocation, Operation operation, Map<String, Object> values) {
        var result = (OperationResult.Changed) invocation.operations().run(operation, values);

        return new ChangedFilesReply(invocation, result.changed());
    }

    /** The result of the daemon as it gave it with {@code --json}; else the text of the answer, empty when it is. */
    void print() {
        show(false);
    }

    /**
     * The result of the daemon as it gave it with {@code --json}; else the text of the answer, which says {@code
     * nothing to change} when the daemon changed nothing.
     */
    void printSayingNothingToChange() {
        show(true);
    }

    private void show(boolean saysNothingToChange) {
        var out = invocation.out();
        if (invocation.json()) {
            out.println(changed.toJson());

            return;
        }
        ResultText.report(changed, invocation.arguments().dryRun(), saysNothingToChange)
                .forEach(out::println);
    }
}
