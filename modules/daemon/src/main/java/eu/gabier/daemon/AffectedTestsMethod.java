package eu.gabier.daemon;

import eu.gabier.protocol.SelectedTests;
import eu.gabier.testing.application.AffectedTests;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@code affected-tests}: the tests that a change to some Java files affects, given as the parameter {@code files}
 * (absolute paths, one per line, at least one, none blank or relative). The result is the list of the binary names of
 * the test classes to run again, in the order of the names, empty when no test depends on the change. The refusals are
 * the ones of the language server that is not ready, and of the selection: a Java file that does not exist, that is in
 * no source folder of the project or that does not compile, a class or the file of filters that cannot be read, a
 * filter that is not a regular expression. They are answered as the problem objects they are, for the whole call.
 */
record AffectedTestsMethod(AffectedTests affectedTests) implements DaemonMethod {

    @Override
    public String name() {
        return "affected-tests";
    }

    @Override
    public Set<String> parameters() {
        return Set.of(ChangeRequest.FILES);
    }

    @Override
    public Outcome call(Map<String, String> params) {
        List<Path> files = new ChangeRequest(name(), params).files();
        if (files.isEmpty() || files.stream().anyMatch(file -> file.toString().isBlank() || !file.isAbsolute())) {
            throw ParameterException.invalid(name(), ChangeRequest.FILES);
        }

        return Outcome.of(new SelectedTests(affectedTests.affectedBy(files)).toJson());
    }
}
