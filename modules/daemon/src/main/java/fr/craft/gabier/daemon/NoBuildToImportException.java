package fr.craft.gabier.daemon;

import fr.craft.gabier.shared.error.domain.GeneratorException;
import java.nio.file.Path;

/** The folder of the project has nothing jdtls can import: no Gradle, Maven or Eclipse build. */
final class NoBuildToImportException extends GeneratorException {

    private NoBuildToImportException(GeneratorExceptionBuilder builder) {
        super(builder);
    }

    static NoBuildToImportException of(Path project) {
        return new NoBuildToImportException(GeneratorException.badRequest(DaemonErrorKey.NO_BUILD_TO_IMPORT)
                .message("There is no build to import in " + project
                        + ": jdtls found no Gradle, Maven or Eclipse project there, so it has nothing to serve")
                .addParameter("project", project.toString()));
    }
}
