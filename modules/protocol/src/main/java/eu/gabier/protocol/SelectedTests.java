package eu.gabier.protocol;

import eu.gabier.shared.error.domain.Assert;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * The result of the {@code affected-tests} method: the tests that a change to some files affects, as the binary names
 * of their classes ({@code app.GreeterTest}, {@code app.Outer$Inner}), in the order of the names, none when no test
 * depends on the change. The names come from class files: hostile text, whatever prints them for a person neutralises
 * them.
 *
 * @param tests the binary names of the classes of the tests
 */
public record SelectedTests(List<String> tests) {

    public SelectedTests {
        Assert.field("tests", tests).notNull().noNullElement();

        tests = List.copyOf(tests);
    }

    /** The result as JSON. */
    public String toJson() {
        return tests.stream().map(Json::quote).collect(Collectors.joining(",", "{\"tests\":[", "]}"));
    }

    /**
     * Reads the result of an {@code affected-tests} response.
     *
     * @throws InvalidResponseException when the list of tests is missing or holds something else than names
     */
    public static SelectedTests of(Map<String, @Nullable Object> result) {
        if (!(result.get("tests") instanceof List<?> names)) {
            throw InvalidResponseException.of("The selected tests have no list of tests", null);
        }

        return new SelectedTests(names.stream().map(SelectedTests::name).toList());
    }

    private static String name(@Nullable Object element) {
        if (element instanceof String name) {
            return name;
        }

        throw InvalidResponseException.of("A selected test is not a name", null);
    }
}
