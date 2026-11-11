package eu.gabier.cli.application;

import eu.gabier.protocol.ChangedFiles;
import eu.gabier.protocol.CodeActions;
import eu.gabier.protocol.DaemonStatus;
import eu.gabier.protocol.DiagnosedFiles;
import eu.gabier.protocol.FoundReferences;
import eu.gabier.protocol.SelectedTests;

/**
 * What an operation answered, decoded into the type of {@code protocol} that holds it. The kind of an operation is
 * fixed: the ones that change files give {@link Changed}, and {@code diagnostics}, {@code references}, {@code actions},
 * {@code affected-tests} and {@code status} each their own.
 */
public sealed interface OperationResult {

    /**
     * The files an operation changed, or would change with a dry run.
     *
     * @param changed the change
     */
    record Changed(ChangedFiles changed) implements OperationResult {}

    /**
     * What the daemon that runs says of itself.
     *
     * @param status the status
     */
    record Reported(DaemonStatus status) implements OperationResult {}

    /**
     * The problems of the files asked for.
     *
     * @param diagnostics the problems of each file
     */
    record Diagnosed(DiagnosedFiles diagnostics) implements OperationResult {}

    /**
     * The places a symbol is used.
     *
     * @param found the places
     */
    record Referenced(FoundReferences found) implements OperationResult {}

    /**
     * The tests that a change affects.
     *
     * @param selected the binary names of the test classes
     */
    record Selected(SelectedTests selected) implements OperationResult {}

    /**
     * What the language server offers on a range.
     *
     * @param offered the actions
     */
    record Offered(CodeActions offered) implements OperationResult {}
}
