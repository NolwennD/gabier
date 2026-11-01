package fr.craft.gabier.daemon;

import fr.craft.gabier.editing.application.ExtractMethod;
import fr.craft.gabier.editing.application.ExtractVariable;
import fr.craft.gabier.editing.application.Inline;
import fr.craft.gabier.editing.application.MoveTypeToFile;
import fr.craft.gabier.editing.application.OrganizeImports;
import fr.craft.gabier.editing.application.Rename;
import fr.craft.gabier.editing.application.RenameTarget;
import fr.craft.gabier.editing.domain.ChangeReport;
import fr.craft.gabier.editing.domain.Position;
import fr.craft.gabier.editing.domain.Range;
import fr.craft.gabier.protocol.ChangedFiles;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

/**
 * What the methods that change files share: how their parameters are read (every refusal names the parameter, as an
 * {@code invalid-parameter}), and how the report of the core becomes the result of the protocol. The request carries
 * the parameters that the method declares, so none is checked for presence here.
 */
final class ChangeRequest {

    static final String FILE = "file";
    static final String NEW_NAME = "newName";
    static final String NAME = "name";
    static final String DRY_RUN = "dryRun";
    static final String START_LINE = "startLine";
    static final String START_COLUMN = "startColumn";
    static final String END_LINE = "endLine";
    static final String END_COLUMN = "endColumn";

    private final String method;
    private final Map<String, String> params;

    ChangeRequest(String method, Map<String, String> params) {
        this.method = method;
        this.params = params;
    }

    Path file() {
        try {
            return Path.of(text(FILE));
        } catch (InvalidPathException e) {
            throw ParameterException.invalid(method, FILE);
        }
    }

    /** A number from 0, as the protocol counts lines and columns. */
    int number(String name) {
        try {
            int value = Integer.parseInt(text(name));
            if (value < 0) {
                throw ParameterException.invalid(method, name);
            }

            return value;
        } catch (NumberFormatException e) {
            throw ParameterException.invalid(method, name);
        }
    }

    /** An identifier to look for: text that is not blank. */
    String token(String name) {
        String value = text(name);
        if (value.isBlank()) {
            throw ParameterException.invalid(method, name);
        }

        return value;
    }

    /** What the rename does, written unless the request is a dry run; the answer says the same either way. */
    DaemonMethod.Outcome run(Rename rename, RenameTarget target) {
        String newName = text(NEW_NAME);
        ChangeReport report = dryRun() ? rename.dryRun(target, newName) : rename.apply(target, newName);

        return DaemonMethod.Outcome.of(changed(report).toJson());
    }

    /** What extracting the expression of the selection into a variable does, written unless the request is a dry run. */
    DaemonMethod.Outcome run(ExtractVariable extract, Range selection) {
        Path file = file();
        String name = token(NAME);
        ChangeReport report = dryRun() ? extract.dryRun(file, selection, name) : extract.apply(file, selection, name);

        return DaemonMethod.Outcome.of(changed(report).toJson());
    }

    /** What extracting the selection into a method does, written unless the request is a dry run. */
    DaemonMethod.Outcome run(ExtractMethod extract, Range selection) {
        Path file = file();
        String name = token(NAME);
        ChangeReport report = dryRun() ? extract.dryRun(file, selection, name) : extract.apply(file, selection, name);

        return DaemonMethod.Outcome.of(changed(report).toJson());
    }

    /** What moving the type at the position to a file of its own does, written unless the request is a dry run. */
    DaemonMethod.Outcome run(MoveTypeToFile move, Position position) {
        Path file = file();
        ChangeReport report = dryRun() ? move.dryRun(file, position) : move.apply(file, position);

        return DaemonMethod.Outcome.of(changed(report).toJson());
    }

    /** What inlining the symbol at the position does, written unless the request is a dry run. */
    DaemonMethod.Outcome run(Inline inline, Position position) {
        Path file = file();
        ChangeReport report = dryRun() ? inline.dryRun(file, position) : inline.apply(file, position);

        return DaemonMethod.Outcome.of(changed(report).toJson());
    }

    /** What organizing the imports of the file does, written unless the request is a dry run. */
    DaemonMethod.Outcome run(OrganizeImports organize) {
        Path file = file();
        ChangeReport report = dryRun() ? organize.dryRun(file) : organize.apply(file);

        return DaemonMethod.Outcome.of(changed(report).toJson());
    }

    private boolean dryRun() {
        return switch (text(DRY_RUN)) {
            case "true" -> true;
            case "false" -> false;
            default -> throw ParameterException.invalid(method, DRY_RUN);
        };
    }

    private String text(String name) {
        return Objects.requireNonNull(params.get(name), "the handler refuses a request without it");
    }

    private static ChangedFiles changed(ChangeReport report) {
        return new ChangedFiles(
                report.created(),
                report.renamed().stream()
                        .map(moved -> new ChangedFiles.Moved(moved.from(), moved.to()))
                        .toList(),
                report.deleted(),
                report.modified(),
                report.diff());
    }
}
