package eu.gabier.protocol;

import eu.gabier.shared.error.domain.Assert;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * The result of the {@code references} method: where a symbol is used, and where it is declared when that was asked
 * for. Positions are 0-based, as in the domain; showing them from 1 is the business of the client.
 *
 * @param declarations where the symbol is declared, none unless the declaration was asked for
 * @param references where the symbol is used, in the order of the files then of the positions
 */
public record FoundReferences(List<Place> declarations, List<Place> references) {

    public FoundReferences {
        Assert.field("declarations", declarations).notNull().noNullElement();
        Assert.field("references", references).notNull().noNullElement();

        declarations = List.copyOf(declarations);
        references = List.copyOf(references);
    }

    /**
     * One place, as the protocol carries it.
     *
     * @param file the absolute path of the file, as the language server gave it
     * @param line the line where the name starts, from 0
     * @param column the column where the name starts, from 0, in UTF-16 code units
     * @param source the line of the file that holds the place, as written: hostile text, whatever prints it for a
     *     person neutralises it
     */
    public record Place(String file, int line, int column, String source) {

        public Place {
            Assert.notNull("file", file);
            Assert.field("line", line).positive();
            Assert.field("column", column).positive();
            Assert.notNull("source", source);
        }

        private String toJson() {
            return """
                {"file":%s,"line":%d,"column":%d,"source":%s}\
                """.formatted(Json.quote(file), line, column, Json.quote(source));
        }
    }

    /** The result as JSON, the members in a stable order. */
    public String toJson() {
        return "{\"declarations\":" + array(declarations) + ",\"references\":" + array(references) + "}";
    }

    /**
     * Reads the result of a {@code references} response.
     *
     * @throws InvalidResponseException when a member is missing or has the wrong type
     */
    public static FoundReferences of(Map<String, @Nullable Object> result) {
        return new FoundReferences(places(result, "declarations"), places(result, "references"));
    }

    private static String array(List<Place> places) {
        return places.stream().map(Place::toJson).collect(Collectors.joining(",", "[", "]"));
    }

    private static List<Place> places(Map<String, @Nullable Object> result, String name) {
        if (!(result.get(name) instanceof List<?> elements)) {
            throw invalid("The references have no list " + name);
        }

        return elements.stream().map(FoundReferences::place).toList();
    }

    private static Place place(@Nullable Object element) {
        if (!(element instanceof Map<?, ?> members)) {
            throw invalid("A place of the references is not an object");
        }

        return new Place(
                text(members, "file"), integer(members, "line"), integer(members, "column"), text(members, "source"));
    }

    private static int integer(Map<?, ?> members, String name) {
        try {
            if (members.get(name) instanceof BigDecimal number) {
                return number.intValueExact();
            }
        } catch (ArithmeticException e) {
            throw InvalidResponseException.of("The member " + name + " of a place is not an integer", e);
        }

        throw invalid("The member " + name + " of a place is not a number");
    }

    private static String text(Map<?, ?> members, String name) {
        if (members.get(name) instanceof String text) {
            return text;
        }

        throw invalid("The member " + name + " of a place is not a string");
    }

    private static InvalidResponseException invalid(String message) {
        return InvalidResponseException.of(message, null);
    }
}
