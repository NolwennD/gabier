package eu.gabier.protocol;

import eu.gabier.shared.error.domain.Assert;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * The result of a method that changes the files of the project (the rename methods), written or only
 * planned: which files were created, moved, deleted or edited, and the unified diff of it all. Every path is absolute
 * and normalized, as the daemon names the files. The paths and the diff come from the project: whoever prints them for
 * a person goes through {@link TerminalText}.
 *
 * @param created the files created
 * @param renamed the files moved
 * @param deleted the files deleted
 * @param modified the files whose text changed, each once
 * @param diff the unified diff of every change, in order (the format of {@code diff -u}); empty when nothing changed
 * @param warnings what the daemon noticed on the way and the caller should know, such as two files of the project that
 *     contradict each other (text from the project: whoever prints it goes through {@link TerminalText}); a member of
 *     the result only when there is one
 */
public record ChangedFiles(
        List<Path> created,
        List<Moved> renamed,
        List<Path> deleted,
        List<Path> modified,
        String diff,
        List<String> warnings) {

    public ChangedFiles {
        Assert.field("created", created).notNull().noNullElement();
        Assert.field("renamed", renamed).notNull().noNullElement();
        Assert.field("deleted", deleted).notNull().noNullElement();
        Assert.field("modified", modified).notNull().noNullElement();
        Assert.notNull("diff", diff);
        Assert.field("warnings", warnings).notNull().noNullElement();

        created = List.copyOf(created);
        renamed = List.copyOf(renamed);
        deleted = List.copyOf(deleted);
        modified = List.copyOf(modified);
        warnings = List.copyOf(warnings);
    }

    /** A result with no warning. */
    public ChangedFiles(List<Path> created, List<Moved> renamed, List<Path> deleted, List<Path> modified, String diff) {
        this(created, renamed, deleted, modified, diff, List.of());
    }

    /** The same result with these warnings. */
    public ChangedFiles withWarnings(List<String> warnings) {
        return new ChangedFiles(created, renamed, deleted, modified, diff, warnings);
    }

    /**
     * A file moved.
     *
     * @param from where the file was
     * @param to where it is
     */
    public record Moved(Path from, Path to) {

        public Moved {
            Assert.notNull("from", from);
            Assert.notNull("to", to);
        }

        private String toJson() {
            return "{\"from\":" + Json.quote(from.toString()) + ",\"to\":" + Json.quote(to.toString()) + "}";
        }
    }

    /** The result as JSON, the members in a stable order. */
    public String toJson() {
        var warned = warnings.isEmpty()
                ? ""
                : ",\"warnings\":" + warnings.stream().map(Json::quote).collect(Collectors.joining(",", "[", "]"));

        return """
            {"created":%s,"renamed":%s,"deleted":%s,"modified":%s,"diff":%s%s}\
            """.formatted(
                        paths(created),
                        renamed.stream().map(Moved::toJson).collect(Collectors.joining(",", "[", "]")),
                        paths(deleted),
                        paths(modified),
                        Json.quote(diff),
                        warned);
    }

    private static String paths(List<Path> paths) {
        return paths.stream().map(path -> Json.quote(path.toString())).collect(Collectors.joining(",", "[", "]"));
    }

    /**
     * Reads the result of the response to a rename.
     *
     * @throws InvalidResponseException when a member is missing or has the wrong type
     */
    public static ChangedFiles of(Map<String, @Nullable Object> result) {
        if (!(result.get("diff") instanceof String diff)) {
            throw invalid("The result has no member diff of the right type");
        }

        return new ChangedFiles(
                pathsOf(result, "created"),
                list(result, "renamed").stream().map(ChangedFiles::moved).toList(),
                pathsOf(result, "deleted"),
                pathsOf(result, "modified"),
                diff,
                warnings(result.get("warnings")));
    }

    private static List<String> warnings(@Nullable Object warnings) {
        if (warnings == null) {
            return List.of();
        }
        if (warnings instanceof List<?> elements && elements.stream().allMatch(String.class::isInstance)) {
            return elements.stream().map(String.class::cast).toList();
        }

        throw invalid("The result has no member warnings of the right type");
    }

    private static List<?> list(Map<String, @Nullable Object> result, String name) {
        if (result.get(name) instanceof List<?> elements) {
            return elements;
        }

        throw invalid("The result has no member " + name + " of the right type");
    }

    private static List<Path> pathsOf(Map<String, @Nullable Object> result, String name) {
        return list(result, name).stream().map(element -> path(element, name)).toList();
    }

    private static Moved moved(@Nullable Object element) {
        if (!(element instanceof Map<?, ?> members)) {
            throw invalid("A file of renamed is not an object");
        }

        return new Moved(path(members.get("from"), "renamed"), path(members.get("to"), "renamed"));
    }

    private static Path path(@Nullable Object element, String name) {
        if (!(element instanceof String text)) {
            throw invalid("A path of " + name + " is not a string");
        }
        try {
            return Path.of(text);
        } catch (InvalidPathException e) {
            throw InvalidResponseException.of("A path of " + name + " is not a path", e);
        }
    }

    private static InvalidResponseException invalid(String message) {
        return InvalidResponseException.of(message, null);
    }
}
