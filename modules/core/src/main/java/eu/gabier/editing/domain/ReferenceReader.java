package eu.gabier.editing.domain;

import eu.gabier.shared.error.domain.Assert;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Gives the locations a language server answers the line of source that holds them, read from the files of a {@link
 * FileStore}: the answer of a server is places, and what a person needs to judge a place is the line.
 */
public final class ReferenceReader {

    private final FileStore store;

    public ReferenceReader(FileStore store) {
        Assert.notNull("store", store);

        this.store = store;
    }

    /**
     * Reads the line of each location. A file is read once, however many locations it holds.
     *
     * @return one reference per location, in the order of the locations
     * @throws EditingException if a file is missing, cannot be read or is not UTF-8, or if a location is not in its
     *     file any more (the file changed since the server answered)
     */
    public List<Reference> read(List<Location> locations) {
        Assert.field("locations", locations).notNull().noNullElement();

        Map<Path, List<String>> lines = new HashMap<>();

        return locations.stream()
                .map(location -> new Reference(
                        location.file(),
                        location.position(),
                        line(location, lines.computeIfAbsent(location.file(), this::linesOf))))
                .toList();
    }

    private static String line(Location location, List<String> lines) {
        Position position = location.position();
        if (position.line() >= lines.size()
                || position.column() > lines.get(position.line()).length()) {
            throw EditingException.staleEdit(location.file(), position);
        }

        return lines.get(position.line());
    }

    private List<String> linesOf(Path file) {
        if (!store.exists(file)) {
            throw EditingException.fileMissing(file);
        }

        try {
            return TextFile.decode(file, store.read(file)).lines();
        } catch (IOException failure) {
            throw EditingException.readFailed(file, failure);
        }
    }
}
