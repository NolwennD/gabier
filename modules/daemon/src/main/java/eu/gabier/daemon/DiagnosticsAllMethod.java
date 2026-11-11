package eu.gabier.daemon;

import eu.gabier.editing.application.DiagnoseProject;
import eu.gabier.protocol.DiagnosedFiles;
import eu.gabier.protocol.FileDiagnostics;
import java.util.Map;

/**
 * {@code diagnostics-all}: the problems the language server reports in every Java file of the project, with no
 * parameter. The language server chooses the files (the source folders it knows of the project, the tests and the other
 * source sets included), and the result is the one of {@code diagnostics}: one element per file, named by its absolute
 * path, in the order of the paths, with an empty list for a file that is clean. The refusal is the one of a server that
 * is not ready, as the problem object it is.
 */
record DiagnosticsAllMethod(DiagnoseProject diagnose) implements DaemonMethod {

    @Override
    public String name() {
        return "diagnostics-all";
    }

    @Override
    public Outcome call(Map<String, String> params) {
        var diagnosed = diagnose.diagnose().stream()
                .map(file ->
                        new FileDiagnostics(file.file().toString(), DiagnosticsMethod.diagnoses(file.diagnostics())))
                .toList();

        return Outcome.of(new DiagnosedFiles(diagnosed).toJson());
    }
}
