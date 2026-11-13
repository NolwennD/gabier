package eu.gabier.daemon;

import eu.gabier.editing.domain.LanguageServerException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.jspecify.annotations.Nullable;

/** The files and the folders of the project jdtls imported, as the requests of the adapter need them. */
final class JdtlsProject {

    private JdtlsProject() {}

    /**
     * The source folders of the projects jdtls imported ({@code java.project.listSourcePaths}: a result whose {@code
     * data} lists them, each with its {@code path}).
     *
     * @throws JdtlsException if jdtls answers something else
     */
    static List<Path> sourceFolders(JdtlsSession session, JdtlsWatch watch) throws InterruptedException {
        var command = new ExecuteCommandParams("java.project.listSourcePaths", List.of());
        Object answer = watch.await(
                "the source folders of the project (java.project.listSourcePaths)",
                () -> session.server().getWorkspaceService().executeCommand(command));
        if (answer instanceof Map<?, ?> result
                && Boolean.TRUE.equals(result.get("status"))
                && result.get("data") instanceof List<?> sources) {
            List<Path> folders = new ArrayList<>();
            for (Object source : sources) {
                if (!(source instanceof Map<?, ?> entry && entry.get("path") instanceof String folder)) {
                    throw notSourceFolders(answer);
                }
                folders.add(normalized(Path.of(folder)));
            }
            return folders;
        }
        throw notSourceFolders(answer);
    }

    private static JdtlsException notSourceFolders(@Nullable Object answer) {
        return new JdtlsException(
                "java.project.listSourcePaths answered " + answer + ", which is not a list of source folders");
    }

    /**
     * The text of a file of the project.
     *
     * @throws LanguageServerException if the file is outside the project, does not exist or is a folder
     */
    static String textInProject(JdtlsSession session, Path file) {
        requireInProject(session, file);
        if (Files.isDirectory(file)) {
            throw LanguageServerException.notAFile(file);
        }
        try {
            return Files.readString(file);
        } catch (NoSuchFileException missing) {
            throw LanguageServerException.fileMissing(file);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    static void requireInProject(JdtlsSession session, Path file) {
        if (session.projects().stream().noneMatch(file::startsWith)) {
            throw LanguageServerException.fileOutsideProject(file);
        }
    }

    static Optional<String> textOnDisk(Path file) {
        try {
            return Optional.of(Files.readString(file));
        } catch (NoSuchFileException missing) {
            return Optional.empty();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    static Path normalized(Path file) {
        return file.toAbsolutePath().normalize();
    }
}
