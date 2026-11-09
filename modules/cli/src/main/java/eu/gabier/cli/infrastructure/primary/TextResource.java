package eu.gabier.cli.infrastructure.primary;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.jspecify.annotations.Nullable;

/** A text the build puts in the resources of the cli, such as the guide: read as UTF-8, or an error that names it. */
final class TextResource {

    private TextResource() {}

    /** The text of the resource called {@code name}, from its stream ({@code null} when the build lacks it). */
    static String read(String name, @Nullable InputStream resource) {
        if (resource == null) {
            throw new IllegalStateException("The resource " + name + " is missing from the build");
        }

        try (resource) {
            return new String(resource.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("The resource " + name + " cannot be read", e);
        }
    }
}
