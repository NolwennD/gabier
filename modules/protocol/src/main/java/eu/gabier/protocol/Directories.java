package eu.gabier.protocol;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/** What the daemon does to folders on disk. */
public final class Directories {

    private Directories() {}

    /** Deletes the folder and all it holds; a link in it is deleted, never followed. */
    public static void delete(Path directory) throws IOException {
        try (Stream<Path> files = Files.walk(directory)) {
            for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(file);
            }
        }
    }
}
