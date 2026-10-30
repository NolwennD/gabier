package fr.craft.gabier.editing.domain;

import fr.craft.gabier.shared.error.domain.Assert;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Finds the position of an identifier in a file of a {@link FileStore}, to name a symbol without knowing its position:
 * the {@code --token} of the Python driver, with a stricter match.
 */
public final class TokenLocator {

    private final FileStore store;

    public TokenLocator(FileStore store) {
        Assert.notNull("store", store);

        this.store = store;
    }

    /**
     * Where the identifier first occurs in the file as a whole word, so {@code greet} is not found in {@code greeting}.
     * Comments and string literals are text like any other: the first occurrence may be in one of them.
     *
     * @throws EditingException if the file is missing, cannot be read or is not UTF-8, or if the identifier does not
     *     occur in it as a whole word
     */
    public Position firstOccurrence(Path file, String identifier) {
        Assert.notNull("file", file);

        return TextFile.decode(file, read(file))
                .firstWord(identifier)
                .orElseThrow(() -> EditingException.tokenNotFound(file, identifier));
    }

    private byte[] read(Path file) {
        if (!store.exists(file)) {
            throw EditingException.fileMissing(file);
        }

        try {
            return store.read(file);
        } catch (IOException failure) {
            throw EditingException.readFailed(file, failure);
        }
    }
}
