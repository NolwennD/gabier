package eu.gabier.daemon;

import eu.gabier.editing.application.Diagnose;
import eu.gabier.editing.domain.Diagnostic;
import eu.gabier.protocol.DiagnosedFiles;
import eu.gabier.protocol.FileDiagnostics;
import eu.gabier.protocol.FileDiagnostics.Diagnosis;
import eu.gabier.protocol.FileDiagnostics.Severity;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * {@code diagnostics}: the problems the language server reports in files, given as the parameter {@code files}
 * (absolute paths, one per line, at least one, none blank). The client sends absolute paths (the daemon has no working directory
 * that means anything to it); the files are asked of the server together, and the result has one element per file, in the
 * order given, named as it was given. The refusals of the server (not ready, a file outside its project, missing or a
 * folder) are answered as the problem objects they are, for the whole call: no file is answered then.
 */
record DiagnosticsMethod(Diagnose diagnose) implements DaemonMethod {

    @Override
    public String name() {
        return "diagnostics";
    }

    @Override
    public Set<String> parameters() {
        return Set.of(ChangeRequest.FILES);
    }

    @Override
    public Outcome call(Map<String, String> params) {
        List<Path> files = new ChangeRequest(name(), params).files();
        if (files.isEmpty() || files.stream().anyMatch(file -> file.toString().isBlank())) {
            throw ParameterException.invalid(name(), ChangeRequest.FILES);
        }
        var found = diagnose.diagnose(files);
        var diagnosed = IntStream.range(0, files.size())
                .mapToObj(index -> new FileDiagnostics(files.get(index).toString(), diagnoses(found.get(index))))
                .toList();

        return Outcome.of(new DiagnosedFiles(diagnosed).toJson());
    }

    static List<Diagnosis> diagnoses(List<Diagnostic> diagnostics) {
        return diagnostics.stream()
                .map(diagnostic -> new Diagnosis(
                        Severity.valueOf(diagnostic.severity().name()),
                        diagnostic.position().line(),
                        diagnostic.position().column(),
                        diagnostic.message()))
                .toList();
    }
}
