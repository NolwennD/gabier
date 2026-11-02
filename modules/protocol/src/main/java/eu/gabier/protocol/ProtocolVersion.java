package eu.gabier.protocol;

import eu.gabier.shared.error.domain.Assert;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Properties;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * The version of gabier, a semantic version. {@link #CURRENT} is the one the build was given ({@code -PgabierVersion},
 * which the release workflow takes from the tag), written in the resource {@code version.properties}.
 */
public record ProtocolVersion(String value) {

    private static final String RESOURCE = "version.properties";

    private static final Pattern SEMANTIC_VERSION =
            Pattern.compile("(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)(-[0-9A-Za-z.-]+)?(\\+[0-9A-Za-z.-]+)?");

    public static final ProtocolVersion CURRENT = read(ProtocolVersion.class.getResourceAsStream(RESOURCE));

    public ProtocolVersion {
        Assert.field("value", value).notBlank().matchesPattern(SEMANTIC_VERSION);
    }

    static ProtocolVersion read(@Nullable InputStream resource) {
        if (resource == null) {
            throw new IllegalStateException("The resource " + RESOURCE + " is missing from the build");
        }

        var properties = new Properties();
        try (resource) {
            properties.load(resource);
        } catch (IOException e) {
            throw new UncheckedIOException("The resource " + RESOURCE + " cannot be read", e);
        }
        var version = properties.getProperty("version");
        if (version == null) {
            throw new IllegalStateException("The resource " + RESOURCE + " has no version");
        }

        return new ProtocolVersion(version);
    }

    @Override
    public String toString() {
        return value;
    }
}
