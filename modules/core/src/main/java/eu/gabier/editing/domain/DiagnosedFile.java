package eu.gabier.editing.domain;

import eu.gabier.shared.error.domain.Assert;
import java.nio.file.Path;
import java.util.List;

/**
 * A source file and the problems a language server reports in it.
 *
 * @param file the file, as an absolute path
 * @param diagnostics what the server reports, in its order, none for a file that is clean
 */
public record DiagnosedFile(Path file, List<Diagnostic> diagnostics) {

    public DiagnosedFile {
        Assert.notNull("file", file);
        Assert.field("diagnostics", diagnostics).notNull().noNullElement();

        diagnostics = List.copyOf(diagnostics);
    }
}
