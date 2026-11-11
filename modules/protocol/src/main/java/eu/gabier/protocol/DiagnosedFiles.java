package eu.gabier.protocol;

import eu.gabier.shared.error.domain.Assert;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * The result of the {@code diagnostics} method: the problems a language server reports in each file asked for, one
 * element per file, in the order of the request and for a file given twice twice. A file that is clean has an empty
 * list.
 *
 * @param files the files, in the order of the request
 */
public record DiagnosedFiles(List<FileDiagnostics> files) {

    public DiagnosedFiles {
        Assert.field("files", files).notNull().noNullElement();

        files = List.copyOf(files);
    }

    /** The result as JSON, the members in a stable order. */
    public String toJson() {
        return files.stream().map(FileDiagnostics::toJson).collect(Collectors.joining(",", "{\"files\":[", "]}"));
    }

    /**
     * Reads the result of a {@code diagnostics} response.
     *
     * @throws InvalidResponseException when a member is missing or has the wrong type
     */
    public static DiagnosedFiles of(Map<String, @Nullable Object> result) {
        if (!(result.get("files") instanceof List<?> elements)) {
            throw InvalidResponseException.of("The diagnostics have no list of files", null);
        }

        return new DiagnosedFiles(elements.stream().map(FileDiagnostics::of).toList());
    }
}
