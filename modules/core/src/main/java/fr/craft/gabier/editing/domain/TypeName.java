package fr.craft.gabier.editing.domain;

import fr.craft.gabier.shared.error.domain.Assert;
import java.nio.file.Path;
import java.util.Set;

/**
 * The name of the type a source file holds: the name of the file without {@code .java}. A file can name a type only if
 * that is an identifier Java allows for a type (JLS 3.8, 3.9): not a keyword, not a literal, not {@code _}, and not one
 * of the restricted identifiers that no type can be called ({@code var}, {@code yield}, {@code record}, {@code
 * sealed}, {@code permits}).
 *
 * @param value the name, without extension
 */
public record TypeName(String value) {

    private static final String EXTENSION = ".java";

    /** The words no name can be: the keywords, the literals and {@code _} (JLS 3.8, 3.9). */
    private static final Set<String> KEYWORDS = Set.of(
            "abstract",
            "assert",
            "boolean",
            "break",
            "byte",
            "case",
            "catch",
            "char",
            "class",
            "const",
            "continue",
            "default",
            "do",
            "double",
            "else",
            "enum",
            "extends",
            "final",
            "finally",
            "float",
            "for",
            "goto",
            "if",
            "implements",
            "import",
            "instanceof",
            "int",
            "interface",
            "long",
            "native",
            "new",
            "package",
            "private",
            "protected",
            "public",
            "return",
            "short",
            "static",
            "strictfp",
            "super",
            "switch",
            "synchronized",
            "this",
            "throw",
            "throws",
            "transient",
            "try",
            "void",
            "volatile",
            "while",
            "_",
            "true",
            "false",
            "null");

    /** The restricted identifiers: no type can be called so, though other names (a package) can. */
    private static final Set<String> RESTRICTED = Set.of("var", "yield", "record", "sealed", "permits");

    public TypeName {
        Assert.notBlank("value", value);
    }

    /**
     * The name of the type of a source file.
     *
     * @throws EditingException if the name of the file is not a name Java allows for a type, or does not end with
     *     {@code .java}
     */
    public static TypeName of(Path file) {
        Assert.notNull("file", file);

        String fileName = String.valueOf(file.getFileName());
        if (!fileName.endsWith(EXTENSION)) {
            throw EditingException.notATypeName(file);
        }
        String name = fileName.substring(0, fileName.length() - EXTENSION.length());
        if (!isIdentifier(name) || isKeyword(name) || RESTRICTED.contains(name)) {
            throw EditingException.notATypeName(file);
        }

        return new TypeName(name);
    }

    /** Whether the text is a word of the language no name can be: a keyword, a literal or {@code _}. */
    static boolean isKeyword(String name) {
        return KEYWORDS.contains(name);
    }

    /** Whether the text is made of the characters of a Java identifier, the first of which can start one. */
    static boolean isIdentifier(String name) {
        return !name.isEmpty()
                && Character.isJavaIdentifierStart(name.codePointAt(0))
                && name.codePoints().allMatch(Character::isJavaIdentifierPart);
    }
}
