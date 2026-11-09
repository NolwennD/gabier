package eu.gabier.cli.application;

import eu.gabier.protocol.ChangedFiles;
import eu.gabier.protocol.CodeActions;
import eu.gabier.protocol.FileDiagnostics;
import eu.gabier.protocol.FoundReferences;

/**
 * What an operation answered, decoded into the type of {@code protocol} that holds it. The kind of an operation is
 * fixed: the ones that change files give {@link Changed}, and {@code diagnostics}, {@code references} and {@code
 * actions} each their own.
 */
public sealed interface OperationResult {

    /**
     * The files an operation changed, or would change with a dry run.
     *
     * @param changed the change
     */
    record Changed(ChangedFiles changed) implements OperationResult {}

    /**
     * The problems of a file.
     *
     * @param diagnostics the problems
     */
    record Diagnosed(FileDiagnostics diagnostics) implements OperationResult {}

    /**
     * The places a symbol is used.
     *
     * @param found the places
     */
    record Referenced(FoundReferences found) implements OperationResult {}

    /**
     * What the language server offers on a range.
     *
     * @param offered the actions
     */
    record Offered(CodeActions offered) implements OperationResult {}
}
