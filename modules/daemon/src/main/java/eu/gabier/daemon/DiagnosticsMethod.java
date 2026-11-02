package eu.gabier.daemon;

import eu.gabier.editing.application.Diagnose;
import eu.gabier.protocol.FileDiagnostics;
import eu.gabier.protocol.FileDiagnostics.Diagnosis;
import eu.gabier.protocol.FileDiagnostics.Severity;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * {@code diagnostics}: the problems the language server reports in a file, given as the parameter {@code file}. The
 * client sends an absolute path (the daemon has no working directory that means anything to it); the refusals of the
 * server (not ready, file outside its project) are answered as the problem objects they are.
 */
record DiagnosticsMethod(Diagnose diagnose) implements DaemonMethod {

    private static final String FILE = "file";

    @Override
    public String name() {
        return "diagnostics";
    }

    @Override
    public Set<String> parameters() {
        return Set.of(FILE);
    }

    @Override
    public Outcome call(Map<String, String> params) {
        var diagnostics = diagnose
                .diagnose(file(Objects.requireNonNull(params.get(FILE), "the handler refuses a request without it")))
                .stream()
                .map(diagnostic -> new Diagnosis(
                        Severity.valueOf(diagnostic.severity().name()),
                        diagnostic.position().line(),
                        diagnostic.position().column(),
                        diagnostic.message()))
                .toList();

        return Outcome.of(new FileDiagnostics(diagnostics).toJson());
    }

    private Path file(String value) {
        try {
            return Path.of(value);
        } catch (InvalidPathException e) {
            throw ParameterException.invalid(name(), FILE);
        }
    }
}
