package eu.gabier.testing.fixtures;

import eu.gabier.shared.error.domain.Assert;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * One question to the selection of tests on the project of {@link AffectedTestsCases}: the files that changed and the
 * tests that depend on them. What it states is the effect, never how the classes were written: the same case is played
 * on classes compiled by javac in {@code check} and on the classes a real language server wrote, in an integration
 * test, and both must select the same tests.
 *
 * @param name what the case shows, in kebab-case
 * @param edits the files rewritten on disk, as paths relative to the root of the project, before the question; none
 *     when the question is asked of the project as it was built
 * @param changed the files that changed, as paths relative to the root of the project
 * @param affected the binary names of the tests that must be selected, in the order of the names
 */
public record AffectedTestsCase(String name, Map<Path, String> edits, List<Path> changed, List<String> affected) {

    public AffectedTestsCase {
        Assert.notBlank("name", name);
        Assert.notNull("edits", edits);
        Assert.field("changed", changed).notNull().notEmpty().noNullElement();
        Assert.notNull("affected", affected);

        edits = Map.copyOf(edits);
        changed = List.copyOf(changed);
        affected = List.copyOf(affected);
    }
}
