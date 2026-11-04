package eu.gabier.cli;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.jspecify.annotations.Nullable;

/**
 * The guide of the improvements, {@code gabier help guide}: the page {@code docs/improve.md}, which the build copies
 * into the resources of the cli (and the native image keeps, see {@code resource-config.json}), so that it is read
 * without a clone of the repository.
 */
final class Guide {

    /** The word after {@code help} that asks for the guide. */
    static final String TOPIC = "guide";

    /** The option after {@code help} that asks the guide a question, as {@code --for=<task>}. */
    static final String FOR = "--for";

    /** What the guide is for, in one line. */
    static final String SUMMARY = "which problem each improvement of the code solves, and how to ask for it";

    private static final String RESOURCE = "improve.md";

    private Guide() {}

    /** The text of the guide. */
    static String text() {
        return read(Guide.class.getResourceAsStream(RESOURCE));
    }

    static String read(@Nullable InputStream resource) {
        if (resource == null) {
            throw new IllegalStateException("The resource " + RESOURCE + " is missing from the build");
        }

        try (resource) {
            return new String(resource.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("The resource " + RESOURCE + " cannot be read", e);
        }
    }
}
