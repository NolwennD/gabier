package fr.craft.gabier.editing.domain;

import fr.craft.gabier.shared.error.domain.Assert;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * The name of a Java package, in dotted form ({@code demo.billing}): identifiers that Java allows, none of them a
 * keyword, a literal or {@code _} (JLS 3.8, 6.5.3), separated by single dots. The default package, which has no name,
 * is not one. The restricted identifiers ({@code var}, {@code record}, ...) can name a package.
 *
 * @param value the name, as written in a {@code package} line
 */
public record PackageName(String value) {

    private static final String SEPARATOR = ".";

    public PackageName {
        Assert.notBlank("value", value);
    }

    /**
     * The name a text spells.
     *
     * @throws EditingException if the text is not the name of a package
     */
    public static PackageName of(String text) {
        Assert.notNull("text", text);

        if (!Arrays.stream(text.split("\\.", -1)).allMatch(PackageName::isSegment)) {
            throw EditingException.notAPackageName(text);
        }

        return new PackageName(text);
    }

    /** The folder of the package under a source folder: one folder per segment. */
    public Path folder() {
        String[] segments = value.split("\\" + SEPARATOR);

        return Path.of(segments[0], Arrays.copyOfRange(segments, 1, segments.length));
    }

    private static boolean isSegment(String segment) {
        return TypeName.isIdentifier(segment) && !TypeName.isKeyword(segment);
    }
}
