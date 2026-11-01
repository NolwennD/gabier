package fr.craft.gabier.editing.domain;

import java.util.Arrays;
import java.util.Optional;

/** The kinds of type that a new file can hold, with the keyword that declares each. */
public enum TypeKind {
    CLASS("class"),
    INTERFACE("interface"),
    RECORD("record"),
    ENUM("enum");

    private final String keyword;

    TypeKind(String keyword) {
        this.keyword = keyword;
    }

    /** The keyword that declares the kind in Java, which is also how a person names it. */
    public String keyword() {
        return keyword;
    }

    /** The kind that this keyword declares, none if it is not the keyword of one of the four. */
    public static Optional<TypeKind> ofKeyword(String text) {
        return Arrays.stream(values()).filter(kind -> kind.keyword.equals(text)).findFirst();
    }
}
