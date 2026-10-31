package fr.craft.gabier.editing.fixtures;

import fr.craft.gabier.shared.error.domain.Assert;

/**
 * Two scenarios in a row on one language server, with the files of the project changed on disk between them: what the
 * server must answer once it has learnt of the changes, not for the text it read before. The files of the {@code
 * second} scenario are those of the project after the change: the answer of the {@code first} applied to its files when
 * it is a rename, the edit of a file otherwise. The replay of an adapter starts the server on the project of the
 * {@code first}, replays it, changes the files on disk, then replays the {@code second} on the same server.
 *
 * @param name what the sequel shows, in kebab-case
 * @param first the scenario replayed first, on the project as it is written
 * @param second the scenario replayed after the files changed, on the project as it then is
 */
public record Sequel(String name, Scenario first, Scenario second) {

    public Sequel {
        Assert.notBlank("name", name);
        Assert.notNull("first", first);
        Assert.notNull("second", second);
    }
}
