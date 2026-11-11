package eu.gabier.daemon;

import eu.gabier.editing.application.DiagnoseProject;
import eu.gabier.protocol.DiagnosedFiles;
import eu.gabier.protocol.FileDiagnostics;
import java.util.Map;

/**
 * {@code diagnostics-all}: the problems the language server reports in every Java file of the project, with no
 * parameter. The answer is what the language server published once it built the project, so it holds the files that
 * have a problem (the tests and the other source sets included) and not the clean ones; the result is the one of {@code
 * diagnostics}: one element per file, named by its absolute path, in the order of the paths. The refusals are the ones
 * of a server that is not ready or that fails the build, as the problem object each is.
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
