package fr.craft.gabier.editing.application;

import fr.craft.gabier.editing.domain.Position;
import fr.craft.gabier.shared.error.domain.Assert;
import java.nio.file.Path;

/** The symbol to rename, named in one of the two ways a caller can name it. */
public sealed interface RenameTarget {

    /** The file that holds the symbol. */
    Path file();

    /**
     * The symbol at a position of the file.
     *
     * @param file the file
     * @param position where the symbol is (see {@link Position}: 0-based line and column)
     */
    record At(Path file, Position position) implements RenameTarget {

        public At {
            Assert.notNull("file", file);
            Assert.notNull("position", position);
        }
    }

    /**
     * The symbol that the first occurrence of an identifier in the file names: the {@code --token} of the Python
     * driver, with a stricter match (whole words only, see {@code TokenLocator}).
     *
     * @param file the file
     * @param identifier the identifier to look for
     */
    record Token(Path file, String identifier) implements RenameTarget {

        public Token {
            Assert.notNull("file", file);
            Assert.notBlank("identifier", identifier);
        }
    }
}
